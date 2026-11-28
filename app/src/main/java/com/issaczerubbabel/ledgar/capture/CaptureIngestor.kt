package com.issaczerubbabel.ledgar.capture

import com.issaczerubbabel.ledgar.capture.categorize.AccountGuesser
import com.issaczerubbabel.ledgar.capture.categorize.CaptureFacts
import com.issaczerubbabel.ledgar.capture.categorize.Categorization
import com.issaczerubbabel.ledgar.capture.categorize.CategorizationPipeline
import com.issaczerubbabel.ledgar.capture.categorize.ConfidenceBand
import com.issaczerubbabel.ledgar.capture.categorize.HistoryPredictor
import com.issaczerubbabel.ledgar.capture.pair.LedgerMatchFinder
import com.issaczerubbabel.ledgar.capture.parse.Direction
import com.issaczerubbabel.ledgar.capture.parse.ParsedTxn
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
import java.time.Instant
import java.time.LocalDateTime
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

    /**
     * [shouldNotify] is true only for a High-confidence capture whose Account is already known (and,
     * for a Transfer rule, its other Account) and that doesn't look like a Transaction already in the Ledger.
     */
    data class Captured(val id: Long, val band: ConfidenceBand, val shouldNotify: Boolean) : IngestResult
}

/** Guesses every waiting capture again; CaptureRepository calls it after each Confirm. */
fun interface CaptureReguesser {
    suspend fun reguessPending()
}

/**
 * Turns one raw alert into a pending Captured transaction: parse, drop repeats, resolve the
 * Account, guess its Category, Description and (when the digits are unknown) Account, store. It
 * never creates a Transaction (ADR-0005); only Confirm does.
 */
class CaptureIngestor @Inject constructor(
    private val captureDao: CaptureDao,
    private val aliasDao: AccountAliasDao,
    private val dropdownOptions: DropdownOptionRepository,
    private val pipeline: CategorizationPipeline,
    private val settings: CaptureSettingsSource,
    private val unparsedDao: UnparsedAlertDao,
    private val history: CaptureHistorySource,
    private val ledgerMatches: LedgerMatchFinder
) : CaptureReguesser {
    private val registry = ParserRegistry.default()

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
        val txnTime = TxnTime.resolve(parsed.txnDate, nowMillis, ZoneId.systemDefault())
        val (result, trace) = guess(parsed, sender, accountId, txnTime, loadInputs())

        val row = CapturedTransaction(
            sources = source,
            sender = sender,
            rawText = text,
            rawHash = dedupeKey(source, sender, text, parsed.refNumber, nowMillis),
            capturedAt = nowMillis,
            txnTime = txnTime,
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
            suggestedToAccountId = result.transferAccountId,
            confidence = result.confidence,
            decidedBy = result.decidedBy,
            traceJson = trace.toJson()
        )

        // A payment already logged by hand waits quietly as a POSSIBLE_DUPLICATE: no Confirm from the shade (#125).
        val alreadyLogged = ledgerMatches.find(row) != null
        val id = captureDao.insert(if (alreadyLogged) row.copy(status = "POSSIBLE_DUPLICATE") else row)
        if (id == -1L) return IngestResult.Duplicate
        // A Transfer rule notifies too, once it names an other Account that isn't this one (#143).
        val outcomeKnown = result.type != TransactionType.TRANSFER ||
            (result.transferAccountId != null && result.transferAccountId != accountId)
        val shouldNotify = result.band == ConfidenceBand.HIGH && accountId != null && outcomeKnown && !alreadyLogged
        return IngestResult.Captured(id, result.band, shouldNotify)
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
     * Guesses every waiting capture again from the raw text it came from, with what the app knows
     * now. Called after each Confirm (once its Undo is over) and after the category mapping
     * changes. Only the guesses stored on the capture change: what the user picked or typed on a
     * card stays in the inbox until Confirm, so it is never overwritten (see [withGuesses]).
     */
    override suspend fun reguessPending() {
        val pending = captureDao.getPendingSnapshot()
        if (pending.isEmpty()) return
        val inputs = loadInputs()
        pending.forEach { capture ->
            val parsed = registry.parse(capture.sender, capture.rawText) ?: return@forEach
            val (result, trace) = guess(parsed, capture.sender, capture.accountId, capture.txnTime, inputs)
            val updated = capture.withGuesses(result, trace, inputs.categoriesByType)
            if (updated != capture) {
                captureDao.updateGuesses(
                    updated.id, updated.suggestedType, updated.suggestedCategory, updated.suggestedToAccountId, updated.confidence,
                    updated.decidedBy, updated.traceJson
                )
            }
        }
    }

    /** What the guessers need, read once per ingest or re-guess pass. */
    private class Inputs(
        val mapping: Map<String, String>,
        /** The user's own Expense and Income Categories, the only ones any guess may name. */
        val categoriesByType: Map<String, List<String>>,
        val predictor: HistoryPredictor
    )

    private suspend fun loadInputs(): Inputs {
        val categoriesByType = mapOf(
            TransactionType.EXPENSE to categories(TransactionType.EXPENSE_CATEGORY_OPTION),
            TransactionType.INCOME to categories(TransactionType.INCOME_CATEGORY_OPTION)
        )
        return Inputs(
            mapping = settings.categoryMapping(),
            categoriesByType = categoriesByType,
            predictor = HistoryPredictor(history.pastTransactions(), categoriesByType)
        )
    }

    private suspend fun categories(optionType: String) = dropdownOptions.getOptionsByType(optionType).first().map { it.name }

    /** The Category (R1, R2, Naive Bayes, R7), the Suggested description and the Account guess. */
    private suspend fun guess(
        parsed: ParsedTxn,
        sender: String,
        accountId: Long?,
        txnTime: Long,
        inputs: Inputs
    ): Pair<Categorization, CaptureTrace> {
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(txnTime), ZoneId.systemDefault())
        val result = pipeline.categorize(parsed, inputs.mapping, inputs.categoriesByType, inputs.predictor, at)
        val description = inputs.predictor.describe(
            CaptureFacts(result.merchantNorm, result.type, parsed.amount, at),
            // The card shows the bank's text anyway, so it is no guess.
            ignore = listOfNotNull(parsed.merchantRaw)
        )
        val guessedAccount = if (accountId != null) null else AccountGuesser.guess(
            isBankApp = CaptureApps.isBankApp(sender),
            accountsAliasedForBank = history.accountsAliasedForSender(sender)
        )
        return result to CaptureTrace.of(result, description, guessedAccount)
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

/**
 * A waiting capture with a new pass of guesses. A pass with neither a Category nor a Transfer's
 * other Account keeps the suggestion already there (and what explained it), so re-guessing never
 * takes a suggestion away, unless its Category is no longer one of the user's [categoriesByType]
 * (a stale rule's High guess, say): that one goes. The Suggested description and the Account guess
 * always follow the new pass. A type, Category or Account the user picked lives in the inbox,
 * never here; a Split ([CaptureTrace.splitFromTransfer]) is kept.
 */
internal fun CapturedTransaction.withGuesses(
    result: Categorization,
    trace: CaptureTrace,
    categoriesByType: Map<String, Collection<String>>
): CapturedTransaction {
    val kept = CaptureTrace.parse(traceJson)
    // A Split is the user's decision, not a guess, so a new pass never brings the suggestion back.
    val next = trace.copy(splitFromTransfer = kept.splitFromTransfer)
    val worthKeeping = suggestedToAccountId != null || suggestedCategory?.let { old ->
        suggestedType?.let(categoriesByType::get).orEmpty().any { it.equals(old, ignoreCase = true) }
    } == true
    return if (result.category != null || result.transferAccountId != null || !worthKeeping) {
        copy(
            suggestedType = result.type,
            suggestedCategory = result.category,
            suggestedToAccountId = result.transferAccountId,
            confidence = result.confidence,
            decidedBy = result.decidedBy,
            traceJson = next.toJson()
        )
    } else {
        copy(traceJson = next.copy(rule = kept.rule, why = kept.why, unmappedCanonical = kept.unmappedCanonical).toJson())
    }
}
