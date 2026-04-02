package com.sheetsync.data.repository

import com.sheetsync.data.local.entity.AccountRecord
import kotlinx.coroutines.flow.Flow

data class AccountBalance(
    val accountId: Long,
    val balance: Double
)

data class AccountWithBalance(
    val account: AccountRecord,
    val balance: Double
)

interface AccountRepository {
    fun getAllAccounts(): Flow<List<AccountRecord>>
    fun getAllVisibleAccounts(): Flow<List<AccountRecord>>
    fun getAccountBalances(): Flow<List<AccountBalance>>
    fun getAccountsWithBalances(): Flow<List<AccountWithBalance>>
    fun getBalanceForAccount(accountId: Long): Flow<Double>
    suspend fun getAllAccountsSnapshot(): List<AccountRecord>
    suspend fun getAccountById(accountId: Long): AccountRecord?
    suspend fun save(record: AccountRecord): Long
    suspend fun delete(record: AccountRecord)
}
