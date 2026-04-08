package com.sheetsync.data.repository

import com.sheetsync.data.local.dao.AccountDao
import com.sheetsync.data.local.dao.ExpenseDao
import com.sheetsync.data.local.entity.AccountRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AccountRepositoryImpl @Inject constructor(
    private val dao: AccountDao,
    private val expenseDao: ExpenseDao
) : AccountRepository {

    override fun getAllAccounts(): Flow<List<AccountRecord>> = dao.getAllAccounts()

    override fun getAllVisibleAccounts(): Flow<List<AccountRecord>> = dao.getVisibleAccounts()

    override fun getAccountBalances(): Flow<List<AccountBalance>> =
        combine(dao.getAllAccounts(), expenseDao.getAllRecords()) { accounts, records ->
            val totalsByAccount = mutableMapOf<Long, Double>()

            fun addDelta(accountId: Long?, delta: Double) {
                if (accountId == null) return
                totalsByAccount[accountId] = (totalsByAccount[accountId] ?: 0.0) + delta
            }

            records.forEach { txn ->
                when (txn.type) {
                    "Income" -> addDelta(txn.toAccountId ?: txn.accountId, txn.amount)
                    "Expense" -> addDelta(txn.fromAccountId ?: txn.accountId, -txn.amount)
                    "Transfer" -> {
                        addDelta(txn.fromAccountId, -txn.amount)
                        addDelta(txn.toAccountId, txn.amount)
                    }
                }
            }

            accounts.map { account ->
                val net = totalsByAccount[account.id] ?: 0.0
                AccountBalance(accountId = account.id, balance = account.initialBalance + net)
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
}
