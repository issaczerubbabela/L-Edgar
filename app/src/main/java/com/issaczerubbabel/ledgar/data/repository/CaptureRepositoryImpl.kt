package com.issaczerubbabel.ledgar.data.repository

import androidx.room.withTransaction
import com.issaczerubbabel.ledgar.capture.categorize.MerchantRuleLearner
import com.issaczerubbabel.ledgar.capture.categorize.RuleChange
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.dao.AccountAliasDao
import com.issaczerubbabel.ledgar.data.local.dao.CaptureDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.dao.MerchantRuleDao
import com.issaczerubbabel.ledgar.data.local.dao.UnparsedAlertDao
import com.issaczerubbabel.ledgar.data.local.entity.AccountAlias
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.UnparsedAlert
import com.issaczerubbabel.ledgar.util.TransactionType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class CaptureRepositoryImpl @Inject constructor(
    private val database: SheetSyncDatabase,
    private val captureDao: CaptureDao,
    private val merchantRuleDao: MerchantRuleDao,
    private val aliasDao: AccountAliasDao,
    private val expenseDao: ExpenseDao,
    private val unparsedDao: UnparsedAlertDao
) : CaptureRepository {

    override fun observePending(): Flow<List<CapturedTransaction>> = captureDao.observePending()

    override fun observePendingCount(): Flow<Int> = captureDao.observePendingCount()

    override suspend fun getById(captureId: Long): CapturedTransaction? = captureDao.getById(captureId)

    override suspend fun confirm(captureId: Long, edits: CaptureEdits): Long? = database.withTransaction {
        val capture = captureDao.getById(captureId) ?: return@withTransaction null
        if (capture.status != "PENDING" && capture.status != "POSSIBLE_DUPLICATE") return@withTransaction null

        val category = edits.category.trim()
        require(category.isNotEmpty()) { "A category is needed to confirm a capture" }

        val date = Instant.ofEpochMilli(capture.txnTime).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val isExpense = edits.type == TransactionType.EXPENSE
        val expenseId = expenseDao.insert(
            ExpenseRecord(
                date = date,
                type = edits.type,
                category = category,
                description = edits.description,
                amount = capture.amount,
                accountId = edits.accountId,
                remarks = "",
                fromAccountId = if (isExpense) edits.accountId else null,
                toAccountId = if (isExpense) null else edits.accountId,
                isSynced = false,
                syncAction = "INSERT"
            )
        )
        captureDao.markConfirmed(captureId, expenseId, category)

        // Picking an account for an alert we couldn't place teaches the alias for next time.
        capture.accountHint?.let { hint ->
            if (aliasDao.getByAlias(hint) == null) {
                aliasDao.upsert(AccountAlias(hint, edits.accountId))
                captureDao.assignAccountToPending(hint, edits.accountId)
            }
        }

        capture.merchantNorm?.let { merchant ->
            val change = MerchantRuleLearner.onConfirmed(
                existing = merchantRuleDao.getByMerchant(merchant),
                merchantNorm = merchant,
                type = edits.type,
                finalCategory = category,
                alwaysUse = edits.alwaysUse,
                now = System.currentTimeMillis()
            )
            when (change) {
                is RuleChange.Upsert -> merchantRuleDao.upsert(change.rule)
                is RuleChange.Delete -> merchantRuleDao.delete(change.merchantNorm)
                RuleChange.None -> Unit
            }
        }
        expenseId
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
