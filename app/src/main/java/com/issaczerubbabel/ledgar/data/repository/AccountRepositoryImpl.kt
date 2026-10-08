package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.account.AccountMath
import com.issaczerubbabel.ledgar.data.local.dao.AccountDao
import com.issaczerubbabel.ledgar.data.local.dao.DropdownOptionDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AccountRepositoryImpl @Inject constructor(
    private val dao: AccountDao,
    private val expenseDao: ExpenseDao,
    private val dropdownOptionDao: DropdownOptionDao,
    private val database: SheetSyncDatabase
) : AccountRepository {

    override fun getAllAccounts(): Flow<List<AccountRecord>> = dao.getAllAccounts()

    override fun getAllVisibleAccounts(): Flow<List<AccountRecord>> = dao.getVisibleAccounts()

    override fun getAccountBook(): Flow<AccountBook> =
        combine(
            dao.getAllAccounts(),
            dropdownOptionDao.getOptionsByType("ACCOUNT_GROUP"),
            expenseDao.getAllRecords(),
            AccountBook::of
        )

    override fun getAccountBalances(): Flow<List<AccountBalance>> =
        getAccountsWithBalances().map { list -> list.map { AccountBalance(it.account.id, it.balance) } }

    override fun getAccountsWithBalances(): Flow<List<AccountWithBalance>> =
        getAccountBook().map { book ->
            val balances = AccountMath.balances(book.accounts, book.transactions)
            book.records.map { AccountWithBalance(account = it, balance = balances.getValue(it.id)) }
        }

    override fun getBalanceForAccount(accountId: Long): Flow<Double> =
        getAccountBalances().map { balances ->
            balances.firstOrNull { it.accountId == accountId }?.balance ?: 0.0
        }

    override suspend fun getAllAccountsSnapshot(): List<AccountRecord> = dao.getAllAccountsSnapshot()

    override suspend fun getAccountById(accountId: Long): AccountRecord? = dao.getAccountById(accountId)

    override suspend fun save(record: AccountRecord): Long {
        if (record.id == 0L) {
            return dao.insert(record)
        }

        val existing = dao.getAccountById(record.id)
        return if (existing != null) {
            // Avoid INSERT OR REPLACE for edits because REPLACE deletes then reinserts,
            // which can null linked transaction foreign keys.
            dao.update(record)
            record.id
        } else {
            dao.insert(record)
        }
    }

    override suspend fun toggleHidden(accountId: Long) {
        val account = dao.getAccountById(accountId) ?: return
        dao.updateHiddenStatus(accountId = accountId, isHidden = !account.isHidden)
    }

    override suspend fun setDisplayOrder(accountIds: List<Long>) {
        dao.setDisplayOrder(accountIds)
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
                    expenseDao.markLinkedTransactionsDeletedForAccount(accountId)
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
