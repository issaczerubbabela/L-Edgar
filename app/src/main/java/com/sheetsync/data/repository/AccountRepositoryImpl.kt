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
            val totalsByAccount = records
                .asSequence()
                .filter { it.accountId != null }
                .groupBy { it.accountId!! }
                .mapValues { (_, txns) ->
                    txns.sumOf { txn ->
                        when (txn.type) {
                            "Income" -> txn.amount
                            "Expense" -> -txn.amount
                            else -> 0.0
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

    override suspend fun delete(record: AccountRecord) = dao.delete(record)
}
