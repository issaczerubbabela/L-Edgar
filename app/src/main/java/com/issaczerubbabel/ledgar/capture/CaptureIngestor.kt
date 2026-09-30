package com.issaczerubbabel.ledgar.capture

import com.google.gson.Gson
import com.issaczerubbabel.ledgar.capture.categorize.CategorizationPipeline
import com.issaczerubbabel.ledgar.capture.categorize.ConfidenceBand
import com.issaczerubbabel.ledgar.capture.parse.Direction
import com.issaczerubbabel.ledgar.capture.parse.ParserRegistry
import com.issaczerubbabel.ledgar.capture.parse.ParsingUtils
import com.issaczerubbabel.ledgar.capture.source.CaptureApps
import com.issaczerubbabel.ledgar.capture.parse.TxnTime
import com.issaczerubbabel.ledgar.data.local.dao.AccountAliasDao
import com.issaczerubbabel.ledgar.data.local.dao.CaptureDao
import com.issaczerubbabel.ledgar.data.local.dao.UnparsedAlertDao
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.UnparsedAlert
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.util.TransactionType
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.time.ZoneId
import javax.inject.Inject

object CaptureSource {
    const val NOTIFICATION = "NOTIF"
    const val SMS = "SMS"
    const val EMAIL = "EMAIL"
}

sealed interface IngestResult {
    data class Ignored(val reason: String) : IngestResult
    data object Unparsed : IngestResult
    data object Duplicate : IngestResult

    /** [shouldNotify] is true only for a High-confidence capture whose Account is already known. */
    data class Captured(val id: Long, val band: ConfidenceBand, val shouldNotify: Boolean) : IngestResult
}

/**
 * Turns one raw alert into a pending Captured transaction: parse, drop repeats, resolve the
 * Account, categorize, store. It never creates a Transaction (ADR-0005); only Confirm does.
 */
class CaptureIngestor @Inject constructor(
    private val captureDao: CaptureDao,
    private val aliasDao: AccountAliasDao,
    private val dropdownOptions: DropdownOptionRepository,
    private val pipeline: CategorizationPipeline,
    private val settings: CaptureSettingsSource,
    private val unparsedDao: UnparsedAlertDao
) {
    private val registry = ParserRegistry.default()
    private val gson = Gson()

    suspend fun ingest(
        source: String,
        sender: String,
        text: String,
        nowMillis: Long = System.currentTimeMillis()
    ): IngestResult {
        if (!settings.isEnabled()) return IngestResult.Ignored("Auto-capture is off")

        val parsed = registry.parse(sender, text) ?: run {
            recordUnparsed(source, sender, text, nowMillis)
            return IngestResult.Unparsed
        }

        val accountId = parsed.accountHint?.let { aliasDao.getByAlias(it)?.accountId }
        val expenseCategories = dropdownOptions
            .getOptionsByType(TransactionType.EXPENSE_CATEGORY_OPTION).first().map { it.name }
        val result = pipeline.categorize(parsed, settings.categoryMapping(), expenseCategories)

        val row = CapturedTransaction(
            sources = source,
            sender = sender,
            rawText = text,
            rawHash = dedupeKey(source, sender, text, parsed.refNumber, nowMillis),
            capturedAt = nowMillis,
            txnTime = TxnTime.resolve(parsed.txnDate, nowMillis, ZoneId.systemDefault()),
            amount = parsed.amount,
            direction = if (parsed.direction == Direction.CREDIT) "CREDIT" else "DEBIT",
            channel = parsed.channel.name,
            merchantRaw = parsed.merchantRaw,
            merchantNorm = result.merchantNorm.ifBlank { null },
            accountHint = parsed.accountHint,
            accountId = accountId,
            refNumber = parsed.refNumber,
            suggestedType = result.type,
            suggestedCategory = result.category,
            confidence = result.confidence,
            decidedBy = result.decidedBy,
            traceJson = gson.toJson(
                mapOf("rule" to result.decidedBy, "why" to result.why, "unmappedCanonical" to result.unmappedCanonical)
            )
        )

        val id = captureDao.insert(row)
        if (id == -1L) return IngestResult.Duplicate
        return IngestResult.Captured(id, result.band, shouldNotify = result.band == ConfidenceBand.HIGH && accountId != null)
    }

    /**
     * Keeps an alert nobody could read, but only one worth a look: it comes from a watched app or a
     * bank-style SMS sender, shows an amount, and is not an OTP. A friend's chat never qualifies.
     */
    private suspend fun recordUnparsed(source: String, sender: String, text: String, nowMillis: Long) {
        if (!CaptureApps.looksLikeBankSender(sender)) return
        if (!ParsingUtils.AMOUNT.containsMatchIn(text) || ParsingUtils.OTP.containsMatchIn(text)) return
        unparsedDao.purgeOlderThan(nowMillis - UNPARSED_RETENTION_MS)
        unparsedDao.insert(
            UnparsedAlert(
                sender = sender,
                rawText = text,
                rawHash = dedupeKey(source, sender, text, null, nowMillis),
                capturedAt = nowMillis
            )
        )
    }

    /**
     * Run again over captures still waiting without a category, using the raw text they came from.
     * Call after the category mapping changes: a keyword that was unmapped may now resolve. It only
     * ever fills in a missing category, never changes one already suggested or chosen.
     */
    suspend fun recategorizePending() {
        val expenseCategories = dropdownOptions
            .getOptionsByType(TransactionType.EXPENSE_CATEGORY_OPTION).first().map { it.name }
        val mapping = settings.categoryMapping()
        captureDao.getPendingWithoutCategory().forEach { capture ->
            val parsed = registry.parse(capture.sender, capture.rawText) ?: return@forEach
            val result = pipeline.categorize(parsed, mapping, expenseCategories)
            val category = result.category ?: return@forEach
            captureDao.update(
                capture.copy(
                    suggestedType = result.type,
                    suggestedCategory = category,
                    confidence = result.confidence,
                    decidedBy = result.decidedBy,
                    traceJson = gson.toJson(mapOf("rule" to result.decidedBy, "why" to result.why))
                )
            )
        }
    }

    /**
     * Apps re-post the same notification, but a real repeat (two ₹20 chai payments) can carry
     * identical text too. With a reference number the text is unique per payment; without one, the
     * text only counts as a repeat within the same ten-minute window.
     */
    private fun dedupeKey(source: String, sender: String, text: String, ref: String?, nowMillis: Long): String {
        val body = text.replace(Regex("\\s+"), " ").trim()
        val window = if (ref != null) "" else (nowMillis / TEN_MINUTES_MS).toString()
        val digest = MessageDigest.getInstance("SHA-256").digest("$source|$sender|$window|$body".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TEN_MINUTES_MS = 10 * 60 * 1000L
        const val UNPARSED_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
