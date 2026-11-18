package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.account.AccountMath
import com.issaczerubbabel.ledgar.account.Reconcile
import com.issaczerubbabel.ledgar.account.ReconcileOutcome
import com.issaczerubbabel.ledgar.data.local.dao.AccountDao
import com.issaczerubbabel.ledgar.data.local.dao.DropdownOptionDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.dao.UnmatchedAccountName
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
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

    /**
     * Saves an Account. A new or renamed Account picks up the Transactions whose Sheet name matches it
     * and have no Account yet. A rename also sends every Transaction on it to the Sheet again, so the
     * Sheet shows the new name and an Import or reinstall still finds the Account.
     */
    override suspend fun save(record: AccountRecord): Long = database.withTransaction {
        val existing = if (record.id == 0L) null else dao.getAccountById(record.id)
        val id = if (existing == null) {
            dao.insert(record)
        } else {
            // Avoid INSERT OR REPLACE for edits because REPLACE deletes then reinserts,
            // which can null linked transaction foreign keys.
            dao.update(record)
            record.id
        }
        val renamed = existing != null && existing.accountName.trim() != record.accountName.trim()
        if (renamed) {
            expenseDao.rememberAccountName(id, record.accountName)
            expenseDao.markAccountTransactionsPending(id)
        }
        if (existing == null || renamed) expenseDao.linkAccountByName(id, record.accountName)
        id
    }

    override fun observeUnmatchedAccountNames(): Flow<List<UnmatchedAccountName>> =
        expenseDao.observeUnmatchedAccountNames()

    override suspend fun mapAccountName(name: String, accountId: Long): Int = database.withTransaction {
        val account = dao.getAccountById(accountId) ?: return@withTransaction 0
        expenseDao.mapAccountName(name, account.id, account.accountName)
    }

    override suspend fun createAccountForName(name: String, groupName: String): Long = database.withTransaction {
        val order = (dao.getAllAccountsSnapshot().maxOfOrNull { it.displayOrder } ?: -1) + 1
        val id = dao.insert(
            AccountRecord(groupName = groupName, accountName = name.trim(), initialBalance = 0.0, displayOrder = order)
        )
        expenseDao.mapAccountName(name, id, name.trim())
        id
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

    override suspend fun countTransactions(accountId: Long): Int = expenseDao.countRecordsForAccount(accountId)

    override suspend fun delete(record: AccountRecord) = dao.delete(record)

    override suspend fun reconcile(accountId: Long, bankBalance: Double, today: LocalDate): Boolean =
        write(accountId) { account ->
            val book = AccountBook.of(listOf(account), emptyList(), expenseDao.getAllRecordsSnapshot().filter { it.syncAction != "DELETE" })
            val balance = AccountMath.balances(book.accounts, book.transactions).getValue(accountId)
            Reconcile.reconcile(account, appBalance = balance, bankBalance = bankBalance, today = today)
        }

    override suspend fun startFresh(accountId: Long, bankBalance: Double, today: LocalDate): Boolean =
        write(accountId) { account -> Reconcile.startFresh(account, bankBalance, today) }

    private suspend fun write(accountId: Long, outcomeFor: suspend (AccountRecord) -> ReconcileOutcome): Boolean =
        database.withTransaction {
            val account = dao.getAccountById(accountId) ?: return@withTransaction false
            val outcome = outcomeFor(account)
            outcome.adjustment?.let { expenseDao.insert(it) }
            dao.update(outcome.account)
            true
        }

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
