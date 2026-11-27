package com.issaczerubbabel.ledgar.data.repository

import androidx.room.withTransaction
import com.issaczerubbabel.ledgar.capture.CaptureReguesser
import com.issaczerubbabel.ledgar.capture.CaptureTrace
import com.issaczerubbabel.ledgar.capture.bestEffort
import com.issaczerubbabel.ledgar.capture.categorize.MerchantRuleTeacher
import com.issaczerubbabel.ledgar.capture.notify.displayTitle
import com.issaczerubbabel.ledgar.capture.pair.LedgerCandidates
import com.issaczerubbabel.ledgar.capture.pair.LedgerMatchFinder
import com.issaczerubbabel.ledgar.capture.pair.LedgerMatcher
import com.issaczerubbabel.ledgar.capture.pair.TransferPairMatcher
import com.issaczerubbabel.ledgar.capture.pair.TransferPairing
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.dao.AccountAliasDao
import com.issaczerubbabel.ledgar.data.local.dao.CaptureDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.dao.MerchantRuleDao
import com.issaczerubbabel.ledgar.data.local.dao.UnparsedAlertDao
import com.issaczerubbabel.ledgar.data.local.entity.AccountAlias
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleKey
import com.issaczerubbabel.ledgar.data.local.entity.UnparsedAlert
import com.issaczerubbabel.ledgar.util.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class CaptureRepositoryImpl @Inject constructor(
    private val database: SheetSyncDatabase,
    private val captureDao: CaptureDao,
    private val merchantRuleDao: MerchantRuleDao,
    private val aliasDao: AccountAliasDao,
    private val expenseDao: ExpenseDao,
    private val unparsedDao: UnparsedAlertDao,
    private val reguesser: CaptureReguesser
) : CaptureRepository {

    private val ledgerMatches = LedgerMatchFinder(expenseDao, captureDao)
    private val ruleTeacher = MerchantRuleTeacher(merchantRuleDao)

    override fun observePending(): Flow<List<CapturedTransaction>> = captureDao.observePending()

    override fun observePendingCount(): Flow<Int> = captureDao.observePendingCount()

    override suspend fun getById(captureId: Long): CapturedTransaction? = captureDao.getById(captureId)

    override fun observeRenames(): Flow<Map<MerchantRuleKey, String>> =
        merchantRuleDao.observeRenames().map { renames ->
            renames.associate { MerchantRuleKey(it.merchantNorm, it.direction) to it.description }
        }

    override suspend fun titleFor(capture: CapturedTransaction): String =
        capture.displayTitle(
            CaptureTrace.parse(capture.traceJson)
                .titleBeforeBankText(capture.merchantNorm?.let { merchantRuleDao.get(it, capture.direction)?.description })
        )

    override suspend fun confirm(captureId: Long, edits: CaptureEdits): Long? {
        val expenseId = confirmInTransaction(captureId, edits)
        // What this Confirm taught (a rule, an alias, one more past Transaction) can sharpen the
        // guesses on captures still waiting. Only the stored guesses change; a user's picks stay.
        if (expenseId != null) reguessQuietly()
        return expenseId
    }

    // The decision is saved; stale guesses are only a missed improvement.
    private suspend fun reguessQuietly() {
        bestEffort { reguesser.reguessPending() }
    }

    private suspend fun confirmInTransaction(captureId: Long, edits: CaptureEdits): Long? = database.withTransaction {
        val capture = captureDao.getById(captureId) ?: return@withTransaction null
        if (!capture.isWaiting()) return@withTransaction null

        val isTransfer = edits.type == TransactionType.TRANSFER
        val otherAccountId = edits.otherAccountId.takeIf { isTransfer }
        val category = if (isTransfer) TransactionType.TRANSFER else edits.category.trim()
        if (isTransfer) {
            require(otherAccountId != null && otherAccountId != edits.accountId) { "A Transfer needs a different other Account" }
        } else {
            require(category.isNotEmpty()) { "A category is needed to confirm a capture" }
        }
        val amount = edits.amount ?: capture.amount
        require(amount > 0) { "A capture's amount must be positive" }

        val date = edits.date ?: Instant.ofEpochMilli(capture.txnTime).atZone(ZoneId.systemDefault()).toLocalDate()
        val isDebit = capture.direction != "CREDIT"
        val (fromAccountId, toAccountId) = when (edits.type) {
            // The bank's direction decides the side: a debit leaves the capture's Account.
            TransactionType.TRANSFER -> if (isDebit) edits.accountId to otherAccountId else otherAccountId to edits.accountId
            TransactionType.EXPENSE -> edits.accountId to null
            else -> null to edits.accountId
        }
        val expenseId = expenseDao.insert(
            ExpenseRecord(
                date = date.toString(),
                type = edits.type,
                category = category,
                description = edits.description,
                amount = amount,
                accountId = if (isTransfer) null else edits.accountId,
                remarks = edits.remarks,
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                isSynced = false,
                syncAction = "INSERT"
            )
        )
        captureDao.markConfirmed(captureId, expenseId, category)

        // ADR-0006: the other half of the Transfer is resolved with it, only when it's unambiguous.
        if (otherAccountId != null) {
            val pairing = pairingFor(capture, amount, otherAccountId, edits.pairCaptureId)
            if (pairing is TransferPairing.One) {
                captureDao.markPaired(pairing.capture.id, expenseId)
                // A Suggested Transfer's other half may have had unknown digits: they are the other Account's now.
                teachAlias(pairing.capture.accountHint, otherAccountId)
            }
        }

        learn(
            capture,
            accountId = edits.accountId,
            type = edits.type,
            category = category,
            alwaysUse = edits.alwaysUse,
            description = { existing ->
                val typed = edits.description.trim()
                // Renaming is remembered; confirming with the bank's own text forgets an earlier rename.
                // A Suggested description is a guess for this payment, not a new name for the merchant,
                // so keeping one (or picking one of its chips) leaves the rule's rename as it was.
                when {
                    typed.isEmpty() || typed == capture.displayTitle() -> null
                    CaptureTrace.parse(capture.traceJson).descriptionGuesses.any { it.equals(typed, ignoreCase = true) } ->
                        existing?.description
                    else -> typed
                }
            },
            toAccountId = otherAccountId
        )
        expenseId
    }

    override fun observeLedgerCandidates(): Flow<LedgerCandidates> = ledgerMatches.observe()

    override suspend fun findLedgerMatch(captureId: Long): ExpenseRecord? {
        val capture = captureDao.getById(captureId)?.takeIf { it.isWaiting() } ?: return null
        return ledgerMatches.find(capture)
    }

    override suspend fun link(captureId: Long, expenseId: Long): Boolean {
        val linked = linkInTransaction(captureId, expenseId)
        // A link teaches like a Confirm, so waiting captures are guessed again the same way.
        if (linked) reguessQuietly()
        return linked
    }

    private suspend fun linkInTransaction(captureId: Long, expenseId: Long): Boolean = database.withTransaction {
        val capture = captureDao.getById(captureId)?.takeIf { it.isWaiting() } ?: return@withTransaction false
        val transaction = expenseDao.getById(expenseId) ?: return@withTransaction false
        // Re-checked here: the Transaction may have been edited, deleted or linked since the card showed it.
        val linked = captureDao.getLinkedExpenseIds().toSet()
        if (!LedgerMatcher.isMatch(capture, transaction, linked, ZoneId.systemDefault())) return@withTransaction false

        captureDao.markLinked(captureId, expenseId, transaction.category)
        // The user chose this Transaction's Category and Account themselves, so it teaches like a Confirm.
        learn(
            capture,
            accountId = transaction.accountId,
            type = transaction.type,
            category = transaction.category,
            alwaysUse = false,
            // A hand-typed description is often about that one payment, so it never renames the merchant.
            description = { existing -> existing?.description },
            toAccountId = null
        )
        true
    }

    /**
     * What a resolved capture teaches: the Account alias for digits not yet placed, and the
     * merchant's rule ([MerchantRuleLearner]). Runs inside the caller's Room transaction.
     */
    private suspend fun learn(
        capture: CapturedTransaction,
        accountId: Long?,
        type: String,
        category: String,
        alwaysUse: Boolean,
        /** The rule's description from the existing rule: the user's rename, or null to forget it. */
        description: (existing: MerchantRule?) -> String?,
        toAccountId: Long?
    ) {
        teachAlias(capture.accountHint, accountId)
        ruleTeacher.teach(capture, type, category, alwaysUse, description, toAccountId)
    }

    /** Picking an account for an alert we couldn't place teaches the alias for next time. */
    private suspend fun teachAlias(hint: String?, accountId: Long?) {
        if (hint != null && accountId != null && aliasDao.getByAlias(hint) == null) {
            aliasDao.upsert(AccountAlias(hint, accountId))
            captureDao.assignAccountToPending(hint, accountId)
        }
    }

    private fun CapturedTransaction.isWaiting() = status == "PENDING" || status == "POSSIBLE_DUPLICATE"

    override suspend fun findTransferPair(captureId: Long, edits: CaptureEdits): TransferPairing {
        if (edits.type != TransactionType.TRANSFER) return TransferPairing.None
        val otherAccountId = edits.otherAccountId ?: return TransferPairing.None
        val capture = captureDao.getById(captureId) ?: return TransferPairing.None
        return pairingFor(capture, edits.amount ?: capture.amount, otherAccountId, edits.pairCaptureId)
    }

    /**
     * The capture's other half: [pairCaptureId], a Suggested Transfer's other side, while it still
     * could be one; otherwise the one waiting capture whose digits are the other Account's alias.
     */
    private suspend fun pairingFor(
        capture: CapturedTransaction,
        amount: Double,
        otherAccountId: Long,
        pairCaptureId: Long? = null
    ): TransferPairing {
        pairCaptureId?.let { captureDao.getById(it) }
            ?.takeIf { TransferPairMatcher.couldBeOtherHalf(capture, amount, it) }
            ?.let { return TransferPairing.One(it) }
        val waiting = captureDao.getWaiting(
            direction = TransferPairMatcher.opposite(capture.direction),
            from = capture.txnTime - TransferPairMatcher.WINDOW_MS,
            to = capture.txnTime + TransferPairMatcher.WINDOW_MS
        )
        val aliases = waiting.mapNotNull { it.accountHint }.distinct()
            .mapNotNull { hint -> aliasDao.getByAlias(hint)?.let { hint to it.accountId } }
            .toMap()
        return TransferPairMatcher.match(capture, amount, otherAccountId, waiting) { aliases[it] }
    }

    override suspend fun splitTransferSuggestion(debitId: Long, creditId: Long) = database.withTransaction {
        listOf(debitId, creditId).forEach { id ->
            val capture = captureDao.getById(id)?.takeIf { it.isWaiting() } ?: return@forEach
            captureDao.updateTrace(id, CaptureTrace.parse(capture.traceJson).copy(splitFromTransfer = true).toJson())
        }
    }

    override suspend fun dismiss(captureId: Long) = captureDao.markDismissed(captureId)

    override suspend fun clearPending() {
        captureDao.deleteAllPending()
        unparsedDao.deleteAll()
    }

    override fun observeUnparsed(): Flow<List<UnparsedAlert>> = unparsedDao.observeAll()

    override fun observeUnparsedCount(): Flow<Int> = unparsedDao.observeCount()

    override suspend fun deleteUnparsed(id: Long) = unparsedDao.delete(id)

    override suspend fun clearUnparsed() = unparsedDao.deleteAll()
}
