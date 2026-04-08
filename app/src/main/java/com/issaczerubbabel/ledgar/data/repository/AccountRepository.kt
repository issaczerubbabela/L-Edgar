package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import kotlinx.coroutines.flow.Flow

data class AccountBalance(
    val accountId: Long,
    val balance: Double
)

data class AccountWithBalance(
    val account: AccountRecord,
    val balance: Double
)

enum class PermanentDeleteStrategy {
    REMOVE_LINKED_TRANSACTIONS,
    REASSIGN_LINKED_TRANSACTIONS
}

interface AccountRepository {
    fun getAllAccounts(): Flow<List<AccountRecord>>
    fun getAllVisibleAccounts(): Flow<List<AccountRecord>>
    fun getAccountBalances(): Flow<List<AccountBalance>>
    fun getAccountsWithBalances(): Flow<List<AccountWithBalance>>
    fun getBalanceForAccount(accountId: Long): Flow<Double>
    suspend fun getAllAccountsSnapshot(): List<AccountRecord>
    suspend fun getAccountById(accountId: Long): AccountRecord?
    suspend fun save(record: AccountRecord): Long
    suspend fun toggleHidden(accountId: Long)
    suspend fun swapDisplayOrder(firstAccountId: Long, secondAccountId: Long)
    suspend fun hasTransactions(accountId: Long): Boolean
    suspend fun delete(record: AccountRecord)
    suspend fun permanentlyDeleteAccount(
        accountId: Long,
        strategy: PermanentDeleteStrategy,
        reassignToAccountId: Long? = null
    ): Boolean
}
