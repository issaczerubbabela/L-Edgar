package com.issaczerubbabel.ledgar.data.repository

import androidx.room.withTransaction
import com.issaczerubbabel.ledgar.capture.CaptureReguesser
import com.issaczerubbabel.ledgar.capture.CaptureTrace
import com.issaczerubbabel.ledgar.capture.categorize.MerchantRuleLearner
import com.issaczerubbabel.ledgar.capture.categorize.RuleChange
import com.issaczerubbabel.ledgar.capture.notify.displayTitle
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
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleKey
import com.issaczerubbabel.ledgar.data.local.entity.UnparsedAlert
import com.issaczerubbabel.ledgar.util.TransactionType
import kotlinx.coroutines.CancellationException
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
        if (expenseId != null) {
            try {
                reguesser.reguessPending()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The Confirm is saved; stale guesses are only a missed improvement.
            }
        }
        return expenseId
    }

    private suspend fun confirmInTransaction(captureId: Long, edits: CaptureEdits): Long? = database.withTransaction {
        val capture = captureDao.getById(captureId) ?: return@withTransaction null
        if (capture.status != "PENDING" && capture.status != "POSSIBLE_DUPLICATE") return@withTransaction null

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
            val pairing = pairingFor(capture, amount, otherAccountId)
            if (pairing is TransferPairing.One) captureDao.markPaired(pairing.capture.id, expenseId)
        }

        // Picking an account for an alert we couldn't place teaches the alias for next time.
        capture.accountHint?.let { hint ->
            if (aliasDao.getByAlias(hint) == null) {
                aliasDao.upsert(AccountAlias(hint, edits.accountId))
                captureDao.assignAccountToPending(hint, edits.accountId)
            }
        }

        capture.merchantNorm?.let { merchant ->
            val existing = merchantRuleDao.get(merchant, capture.direction)
            val typed = edits.description.trim()
            val change = MerchantRuleLearner.onConfirmed(
                existing = existing,
                merchantNorm = merchant,
                direction = capture.direction,
                type = edits.type,
                finalCategory = category,
                alwaysUse = edits.alwaysUse,
                now = System.currentTimeMillis(),
                // Renaming is remembered; confirming with the bank's own text forgets an earlier rename.
                // A Suggested description is a guess for this payment, not a new name for the merchant,
                // so keeping one (or picking one of its chips) leaves the rule's rename as it was.
                description = when {
                    typed.isEmpty() || typed == capture.displayTitle() -> null
                    CaptureTrace.parse(capture.traceJson).descriptionGuesses.any { it.equals(typed, ignoreCase = true) } ->
                        existing?.description
                    else -> typed
                },
                toAccountId = otherAccountId
            )
            when (change) {
                is RuleChange.Upsert -> merchantRuleDao.upsert(change.rule)
                is RuleChange.Delete -> merchantRuleDao.delete(change.merchantNorm, change.direction)
                RuleChange.None -> Unit
            }
        }
        expenseId
    }

    override suspend fun findTransferPair(captureId: Long, edits: CaptureEdits): TransferPairing {
        if (edits.type != TransactionType.TRANSFER) return TransferPairing.None
        val otherAccountId = edits.otherAccountId ?: return TransferPairing.None
        val capture = captureDao.getById(captureId) ?: return TransferPairing.None
        return pairingFor(capture, edits.amount ?: capture.amount, otherAccountId)
    }

    private suspend fun pairingFor(capture: CapturedTransaction, amount: Double, otherAccountId: Long): TransferPairing {
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
