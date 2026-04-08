package com.sheetsync.data.repository

import com.sheetsync.data.local.dao.AccountDao
import com.sheetsync.data.local.dao.ExpenseDao
import com.sheetsync.data.local.SheetSyncDatabase
import com.sheetsync.data.local.entity.AccountRecord
import androidx.room.withTransaction
import com.sheetsync.util.parseFlexibleDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AccountRepositoryImpl @Inject constructor(
    private val dao: AccountDao,
    private val expenseDao: ExpenseDao,
    private val database: SheetSyncDatabase
) : AccountRepository {

    override fun getAllAccounts(): Flow<List<AccountRecord>> = dao.getAllAccounts()

    override fun getAllVisibleAccounts(): Flow<List<AccountRecord>> = dao.getVisibleAccounts()

    override fun getAccountBalances(): Flow<List<AccountBalance>> =
        combine(dao.getAllAccounts(), expenseDao.getAllRecords()) { accounts, records ->
            val totalsByAccount = mutableMapOf<Long, Double>()
            val asOfDateByAccount = accounts.associate { it.id to it.initialBalanceDate }

            fun addDelta(accountId: Long?, delta: Double, txDate: String) {
                if (accountId == null) return
                val asOfDate = asOfDateByAccount[accountId] ?: "1970-01-01"
                if (!isOnOrAfterAsOf(txDate, asOfDate)) return
                totalsByAccount[accountId] = (totalsByAccount[accountId] ?: 0.0) + delta
            }

            records.forEach { txn ->
                when (txn.type) {
                    "Income" -> addDelta(txn.toAccountId ?: txn.accountId, txn.amount, txn.date)
                    "Expense" -> addDelta(txn.fromAccountId ?: txn.accountId, -txn.amount, txn.date)
                    "Transfer" -> {
                        addDelta(txn.fromAccountId, -txn.amount, txn.date)
                        addDelta(txn.toAccountId, txn.amount, txn.date)
                    }
                }
            }

            accounts.map { account ->
                val net = totalsByAccount[account.id] ?: 0.0
                AccountBalance(accountId = account.id, balance = account.initialBalance + net)
            }
        }

    private fun isOnOrAfterAsOf(txDate: String, asOfDate: String): Boolean {
        val tx = parseFlexibleDate(txDate)
        val asOf = parseFlexibleDate(asOfDate)
        return when {
            tx != null && asOf != null -> !tx.isBefore(asOf)
            else -> txDate >= asOfDate
        }
    }

    override fun getAccountsWithBalances(): Flow<List<AccountWithBalance>> =
        combine(dao.getAllAccounts(), getAccountBalances()) { accounts, balances ->
            val balancesById = balances.associateBy { it.accountId }
            accounts.map { account ->
                AccountWithBalance(
                    account = account,
                    balance = balancesById[account.id]?.balance ?: account.initialBalance
                )
            }
        }

    override fun getBalanceForAccount(accountId: Long): Flow<Double> =
        getAccountBalances().map { balances ->
            balances.firstOrNull { it.accountId == accountId }?.balance ?: 0.0
        }

    override suspend fun getAllAccountsSnapshot(): List<AccountRecord> = dao.getAllAccountsSnapshot()

    override suspend fun getAccountById(accountId: Long): AccountRecord? = dao.getAccountById(accountId)

    override suspend fun save(record: AccountRecord): Long = dao.insert(record)

    override suspend fun toggleHidden(accountId: Long) {
        val account = dao.getAccountById(accountId) ?: return
        dao.updateHiddenStatus(accountId = accountId, isHidden = !account.isHidden)
    }

    override suspend fun swapDisplayOrder(firstAccountId: Long, secondAccountId: Long) {
        dao.swapDisplayOrder(firstAccountId = firstAccountId, secondAccountId = secondAccountId)
    }

    override suspend fun hasTransactions(accountId: Long): Boolean =
        expenseDao.countRecordsForAccount(accountId) > 0

    override suspend fun delete(record: AccountRecord) = dao.delete(record)

    override suspend fun permanentlyDeleteAccount(
        accountId: Long,
        strategy: PermanentDeleteStrategy,
        reassignToAccountId: Long?
    ): Boolean = database.withTransaction {
        val account = dao.getAccountById(accountId) ?: return@withTransaction false
        val hasLinkedTransactions = expenseDao.countRecordsForAccount(accountId) > 0

        if (hasLinkedTransactions) {
            when (strategy) {
                PermanentDeleteStrategy.REMOVE_LINKED_TRANSACTIONS -> {
                    expenseDao.deleteLinkedTransactionsForAccount(accountId)
                }

                PermanentDeleteStrategy.REASSIGN_LINKED_TRANSACTIONS -> {
                    val targetId = reassignToAccountId ?: return@withTransaction false
                    if (targetId == accountId) return@withTransaction false
                    val target = dao.getAccountById(targetId) ?: return@withTransaction false
                    expenseDao.reassignLinkedTransactionsForAccount(accountId, target.id)
                }
            }
        }

        dao.delete(account)
        true
    }
}
