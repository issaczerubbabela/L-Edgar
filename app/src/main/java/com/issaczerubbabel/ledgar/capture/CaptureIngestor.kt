package com.issaczerubbabel.ledgar.capture

import androidx.room.withTransaction
import com.issaczerubbabel.ledgar.capture.categorize.AccountGuesser
import com.issaczerubbabel.ledgar.capture.categorize.CaptureFacts
import com.issaczerubbabel.ledgar.capture.categorize.Categorization
import com.issaczerubbabel.ledgar.capture.categorize.CategorizationPipeline
import com.issaczerubbabel.ledgar.capture.categorize.ConfidenceBand
import com.issaczerubbabel.ledgar.capture.categorize.HistoryPredictor
import com.issaczerubbabel.ledgar.capture.categorize.MerchantNormalizer
import com.issaczerubbabel.ledgar.capture.pair.AlertFacts
import com.issaczerubbabel.ledgar.capture.pair.AlertSeen
import com.issaczerubbabel.ledgar.capture.pair.LedgerMatchFinder
import com.issaczerubbabel.ledgar.capture.pair.MergeCandidate
import com.issaczerubbabel.ledgar.capture.pair.SamePaymentMatcher
import com.issaczerubbabel.ledgar.capture.parse.AlertReading
import com.issaczerubbabel.ledgar.capture.parse.Direction
import com.issaczerubbabel.ledgar.capture.parse.ParsedTxn
import com.issaczerubbabel.ledgar.capture.parse.ParserRegistry
import com.issaczerubbabel.ledgar.capture.parse.ParsingUtils
import com.issaczerubbabel.ledgar.capture.parse.TxnTime
import com.issaczerubbabel.ledgar.capture.source.AlertSource
import com.issaczerubbabel.ledgar.capture.source.BankEmails
import com.issaczerubbabel.ledgar.capture.source.CaptureApps
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.dao.AccountAliasDao
import com.issaczerubbabel.ledgar.data.local.dao.CaptureDao
import com.issaczerubbabel.ledgar.data.local.dao.UnparsedAlertDao
import com.issaczerubbabel.ledgar.data.local.entity.CaptureAlert
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

    /**
     * The Alert joined capture [id], waiting for the same payment from another Alert source
     * (ADR-0012). [shouldNotify] only when the merge first made it worth a notification, so a
     * payment notifies once, not once per Alert.
     */
    data class Merged(val id: Long, val band: ConfidenceBand, val shouldNotify: Boolean) : IngestResult

    /**
     * The Alert repeats capture [id], already confirmed, linked, paired or dismissed. It is kept
     * with it quietly: no new card, no notification, and the Transaction is never edited (ADR-0005).
     */
    data class AttachedLate(val id: Long) : IngestResult
}

/** Guesses every waiting capture again; CaptureRepository calls it after each Confirm. */
fun interface CaptureReguesser {
    suspend fun reguessPending()
}

/** Not the same payment; CaptureRepository calls it from the inbox. */
fun interface SamePaymentSplitter {
    /**
     * Not the same payment: every Alert of waiting capture [captureId] becomes a capture of its own,
     * marked so they never merge again ([CaptureTrace.keptApartFrom]). The capture keeps its first
     * Alert, and so its id and what the user picked on its card. A capture with one Alert is left alone.
     */
    suspend fun splitSamePayment(captureId: Long)
}

/**
 * Turns one raw alert into a pending Captured transaction: parse, drop repeats, join the capture
 * of the same payment from another Alert source (ADR-0012) or start one, resolve the Account,
 * guess its Category, Description and (when the digits are unknown) Account, store. It never
 * creates a Transaction (ADR-0005); only Confirm does.
 */
class CaptureIngestor @Inject constructor(
    private val database: SheetSyncDatabase,
    private val captureDao: CaptureDao,
    private val aliasDao: AccountAliasDao,
    private val dropdownOptions: DropdownOptionRepository,
    private val pipeline: CategorizationPipeline,
    private val settings: CaptureSettingsSource,
    private val unparsedDao: UnparsedAlertDao,
    private val history: CaptureHistorySource,
    private val ledgerMatches: LedgerMatchFinder
) : CaptureReguesser, SamePaymentSplitter {
    private val registry = ParserRegistry.default()

    suspend fun ingest(
        source: String,
        sender: String,
        text: String,
        nowMillis: Long = System.currentTimeMillis()
    ): IngestResult {
        if (!settings.isEnabled()) return IngestResult.Ignored("Auto-capture is off")
        // The listener already drops these; checked again so no other sender's email is ever parsed or kept.
        if (source == CaptureSource.EMAIL) {
            if (!BankEmails.isKnownSender(sender)) return IngestResult.Ignored("Not a known email sender")
            if (!settings.readsBankEmails()) return IngestResult.Ignored("Read bank emails is off")
        } else if (BankEmails.isKnownSender(sender)) {
            // A chat or app titled like the bank's email sender is not its email.
            return IngestResult.Ignored("An email sender outside email")
        }

        val parsed = when (val reading = registry.read(sender, text)) {
            is AlertReading.Payment -> reading.txn
            AlertReading.NotAPayment -> return IngestResult.Ignored("Not a payment")
            AlertReading.Unreadable -> {
                recordUnparsed(source, sender, text, nowMillis)
                return IngestResult.Unparsed
            }
        }
        val alert = CaptureAlert(
            captureId = 0,
            source = source,
            sender = sender,
            rawText = text,
            rawHash = dedupeKey(source, sender, text, parsed.refNumber, nowMillis),
            capturedAt = nowMillis
        )
        // Read before the transaction: these include Flow reads, which a Room transaction would block.
        val inputs = loadInputs()

        // One transaction, so two Alerts of one payment read at the same moment can't both start a capture.
        return database.withTransaction {
            if (captureDao.hasAlert(alert.rawHash)) return@withTransaction IngestResult.Duplicate
            val reading = read(alert, parsed)
            val same = samePayment(reading)
            when {
                same == null -> store(alert, reading, inputs)
                same.isWaiting() -> merge(same, alert, inputs)
                else -> {
                    captureDao.insertAlert(alert.copy(captureId = same.id))
                    IngestResult.AttachedLate(same.id)
                }
            }
        }
    }

    /** A new payment: a capture of its own. */
    private suspend fun store(alert: CaptureAlert, reading: ReadAlert, inputs: Inputs): IngestResult {
        val row = newCapture(alert, reading, inputs)
        // A payment already logged by hand waits quietly as a POSSIBLE_DUPLICATE: no Confirm from the shade (#125).
        val alreadyLogged = ledgerMatches.find(row) != null
        val stored = if (alreadyLogged) row.copy(status = "POSSIBLE_DUPLICATE") else row
        val id = captureDao.insert(stored)
        if (id == -1L) return IngestResult.Duplicate
        captureDao.insertAlert(alert.copy(captureId = id))
        return IngestResult.Captured(id, stored.band(), stored.isNotifiable())
    }

    /**
     * Another source's Alert for [capture]'s payment: it joins the capture, whose facts are put
     * together again ([AlertMerge]) and guessed again. The capture keeps its id, so what the user
     * already picked on its card still wins.
     */
    private suspend fun merge(capture: CapturedTransaction, alert: CaptureAlert, inputs: Inputs): IngestResult {
        if (captureDao.insertAlert(alert.copy(captureId = capture.id)) == -1L) return IngestResult.Duplicate
        val stored = captureDao.getAlerts(capture.id)
        val facts = AlertMerge.facts(stored.mapNotNull(::read).ifEmpty { listOfNotNull(read(capture.firstAlert())) })
        val rebuilt = rebuild(capture, facts, accountFor(facts), inputs)
        val alreadyLogged = ledgerMatches.find(rebuilt) != null
        val updated = if (rebuilt.status == "PENDING" && alreadyLogged) rebuilt.copy(status = "POSSIBLE_DUPLICATE") else rebuilt
        captureDao.update(updated)
        return IngestResult.Merged(capture.id, updated.band(), updated.isNotifiable() && !capture.isNotifiable())
    }

    /** The capture [alert] repeats from another Alert source, in any status, or null for a new payment. */
    private suspend fun samePayment(alert: ReadAlert): CapturedTransaction? {
        val direction = directionOf(alert.parsed)
        val rows = captureDao.getSamePaymentCandidates(
            refNumber = alert.parsed.refNumber,
            direction = direction,
            amount = alert.parsed.amount,
            from = alert.arrivedAt - SamePaymentMatcher.WINDOW_MS,
            to = alert.arrivedAt + SamePaymentMatcher.WINDOW_MS
        )
        if (rows.isEmpty()) return null
        val alerts = alertsFor(rows.map { it.id })
        val candidates = rows.map { row ->
            MergeCandidate(
                captureId = row.id,
                status = row.status,
                amount = row.amount,
                direction = row.direction,
                refNumber = row.refNumber,
                alerts = (alerts[row.id] ?: listOf(row.firstAlert())).map { AlertSeen(sourceOf(it), it.capturedAt) },
                keptApartFrom = CaptureTrace.parse(row.traceJson).keptApartFrom
            )
        }
        val facts = AlertFacts(alert.source, alert.parsed.amount, direction, alert.parsed.refNumber, alert.arrivedAt)
        val picked = SamePaymentMatcher.pick(facts, candidates) ?: return null
        return rows.first { it.id == picked.captureId }
    }

    override suspend fun splitSamePayment(captureId: Long) {
        val inputs = loadInputs()
        database.withTransaction {
            val capture = captureDao.getById(captureId)?.takeIf { it.isWaiting() } ?: return@withTransaction
            val alerts = captureDao.getAlerts(captureId)
            if (alerts.size < 2) return@withTransaction
            val keep = alerts.firstOrNull { it.rawHash == capture.rawHash } ?: alerts.first()

            val keptReading = read(keep) ?: return@withTransaction
            // An Alert no parser reads any more can't stand alone, so it stays with the first.
            val moving = (alerts - keep).mapNotNull { alert -> read(alert)?.let { alert to it } }
            if (moving.isEmpty()) return@withTransaction
            val apart = (moving.map { sourceOf(it.first) } + sourceOf(keep)).toSet()

            val keptFacts = AlertMerge.facts(listOf(keptReading) + (alerts - keep - moving.map { it.first }.toSet()).mapNotNull(::read))
            captureDao.update(rebuild(capture, keptFacts, accountFor(keptFacts), inputs).keptApartFrom(apart - sourceOf(keep)))
            moving.forEach { (alert, reading) ->
                val row = newCapture(alert, reading, inputs).keptApartFrom(apart - sourceOf(alert))
                val alreadyLogged = ledgerMatches.find(row) != null
                // The Alert's hash was never a capture's, so the insert always lands.
                val id = captureDao.insert(if (alreadyLogged) row.copy(status = "POSSIBLE_DUPLICATE") else row)
                captureDao.moveAlert(alert.id, id)
            }
        }
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
     * Guesses every waiting capture again from the Alerts it came from, with what the app knows
     * now. Called after each Confirm (once its Undo is over) and after the category mapping
     * changes. Only the guesses stored on the capture change: what the user picked or typed on a
     * card stays in the inbox until Confirm, so it is never overwritten (see [withGuesses]).
     */
    override suspend fun reguessPending() {
        val pending = captureDao.getPendingSnapshot()
        if (pending.isEmpty()) return
        val inputs = loadInputs()
        val alerts = alertsFor(pending.map { it.id })
        pending.forEach { capture ->
            val reads = (alerts[capture.id] ?: listOf(capture.firstAlert())).mapNotNull(::read)
            if (reads.isEmpty()) return@forEach
            val (result, trace) = guess(AlertMerge.facts(reads), capture.accountId, inputs)
            val updated = capture.withGuesses(result, trace, inputs.categoriesByType)
            if (updated != capture) {
                captureDao.updateGuesses(
                    updated.id, updated.suggestedType, updated.suggestedCategory, updated.suggestedToAccountId, updated.confidence,
                    updated.decidedBy, updated.traceJson
                )
            }
        }
    }

    /** A capture made from [alert] alone ([reading] is it read), guessed, not yet stored. */
    private suspend fun newCapture(alert: CaptureAlert, reading: ReadAlert, inputs: Inputs): CapturedTransaction {
        val facts = AlertMerge.facts(listOf(reading))
        val accountId = accountFor(facts)
        val (result, trace) = guess(facts, accountId, inputs)
        return CapturedTransaction(
            sources = alert.source,
            sender = alert.sender,
            rawText = alert.rawText,
            rawHash = alert.rawHash,
            capturedAt = alert.capturedAt,
            txnTime = facts.txnTime,
            amount = facts.parsed.amount,
            direction = directionOf(facts.parsed),
            channel = facts.parsed.channel.name
        ).withFacts(facts, result, accountId).copy(
            suggestedType = result.type,
            suggestedCategory = result.category,
            suggestedToAccountId = result.transferAccountId,
            confidence = result.confidence,
            decidedBy = result.decidedBy,
            traceJson = trace.toJson()
        )
    }

    /** [capture] with what its Alerts now say together, guessed again (see [withGuesses]). */
    private suspend fun rebuild(capture: CapturedTransaction, facts: MergedFacts, accountId: Long?, inputs: Inputs): CapturedTransaction {
        val (result, trace) = guess(facts, accountId, inputs)
        return capture.withFacts(facts, result, accountId).withGuesses(result, trace, inputs.categoriesByType)
    }

    private suspend fun accountFor(facts: MergedFacts): Long? = facts.parsed.accountHint?.let { aliasDao.getByAlias(it)?.accountId }

    /** One stored Alert read again, or null if no parser reads it any more. */
    private fun read(alert: CaptureAlert): ReadAlert? =
        registry.parse(alert.sender, alert.rawText)?.let { read(alert, it) }

    private fun read(alert: CaptureAlert, parsed: ParsedTxn) = ReadAlert(
        source = sourceOf(alert),
        sender = alert.sender,
        parsed = parsed,
        arrivedAt = alert.capturedAt,
        txnTime = TxnTime.resolve(parsed.txnDate, alert.capturedAt, ZoneId.systemDefault())
    )

    /** Each capture's Alerts, read in chunks to stay under SQLite's limit on query parameters. */
    private suspend fun alertsFor(captureIds: List<Long>): Map<Long, List<CaptureAlert>> =
        captureIds.chunked(500).flatMap { captureDao.getAlertsFor(it) }.groupBy { it.captureId }

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

    /**
     * The Category (R1, R2, Naive Bayes, R7), the Suggested description and the Account guess. A
     * Merchant rule is looked up under the best payee, then the bank's own text (ADR-0012).
     */
    private suspend fun guess(facts: MergedFacts, accountId: Long?, inputs: Inputs): Pair<Categorization, CaptureTrace> {
        val parsed = facts.parsed
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(facts.txnTime), ZoneId.systemDefault())
        val result = pipeline.categorize(parsed, inputs.mapping, inputs.categoriesByType, inputs.predictor, at, alsoKnownAs = facts.bankMerchantRaw)
        val description = inputs.predictor.describe(
            CaptureFacts(result.merchantNorm, result.type, parsed.amount, at),
            // The card shows the bank's text anyway, so it is no guess.
            ignore = listOfNotNull(parsed.merchantRaw, facts.bankMerchantRaw)
        )
        // Only a bank's own app suggests an Account, from the aliases seen on its alerts.
        val guessedAccount = if (accountId != null) null else facts.bankSender?.let { sender ->
            AccountGuesser.guess(isBankApp = CaptureApps.isBankApp(sender), accountsAliasedForBank = history.accountsAliasedForSender(sender))
        }
        val bankName = MerchantNormalizer.normalize(facts.bankMerchantRaw).norm.takeIf { it.isNotBlank() && it != result.merchantNorm }
        return result to CaptureTrace.of(result, description, guessedAccount).copy(alsoKnownAs = bankName)
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

private fun directionOf(parsed: ParsedTxn) = if (parsed.direction == Direction.CREDIT) "CREDIT" else "DEBIT"

private fun sourceOf(alert: CaptureAlert) = AlertSource.of(alert.source, alert.sender)

private fun CapturedTransaction.isWaiting() = status == "PENDING" || status == "POSSIBLE_DUPLICATE"

/** The Alert that made this capture, from its own columns, for a capture stored before it had any. */
private fun CapturedTransaction.firstAlert() = CaptureAlert(
    captureId = id, source = sources.substringBefore(','), sender = sender, rawText = rawText, rawHash = rawHash, capturedAt = capturedAt
)

private fun CapturedTransaction.band() =
    ConfidenceBand.of(confidence, suggestedCategory != null || suggestedToAccountId != null)

/**
 * Worth a notification: waiting as PENDING (not a possible duplicate), High confidence, the
 * Account known and, for a Transfer rule, an other Account that isn't this one (#143).
 */
private fun CapturedTransaction.isNotifiable(): Boolean {
    val outcomeKnown = suggestedType != TransactionType.TRANSFER || (suggestedToAccountId != null && suggestedToAccountId != accountId)
    return status == "PENDING" && band() == ConfidenceBand.HIGH && accountId != null && outcomeKnown
}

/** [facts] written onto the capture: the amount, date, payee, Account and reference its Alerts give together. */
private fun CapturedTransaction.withFacts(facts: MergedFacts, result: Categorization, accountId: Long?) = copy(
    txnTime = facts.txnTime,
    amount = facts.parsed.amount,
    channel = facts.parsed.channel.name,
    merchantRaw = facts.parsed.merchantRaw,
    merchantNorm = result.merchantNorm.ifBlank { null },
    accountHint = facts.parsed.accountHint,
    accountId = accountId,
    refNumber = facts.parsed.refNumber
)

private fun CapturedTransaction.keptApartFrom(sources: Set<AlertSource>): CapturedTransaction {
    val trace = CaptureTrace.parse(traceJson)
    return copy(traceJson = trace.copy(keptApartFrom = trace.keptApartFrom + sources).toJson())
}

/**
 * A waiting capture with a new pass of guesses. A pass with neither a Category nor a Transfer's
 * other Account keeps the suggestion already there (and what explained it), so re-guessing never
 * takes a suggestion away, unless its Category is no longer one of the user's [categoriesByType]
 * (a stale rule's High guess, say): that one goes. The Suggested description and the Account guess
 * always follow the new pass. A type, Category or Account the user picked lives in the inbox,
 * never here; a Split ([CaptureTrace.splitFromTransfer]) and Not the same payment
 * ([CaptureTrace.keptApartFrom]) are kept.
 */
internal fun CapturedTransaction.withGuesses(
    result: Categorization,
    trace: CaptureTrace,
    categoriesByType: Map<String, Collection<String>>
): CapturedTransaction {
    val kept = CaptureTrace.parse(traceJson)
    // A Split is the user's decision, not a guess, so a new pass never brings the suggestion back.
    val next = trace.copy(splitFromTransfer = kept.splitFromTransfer, keptApartFrom = kept.keptApartFrom)
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
