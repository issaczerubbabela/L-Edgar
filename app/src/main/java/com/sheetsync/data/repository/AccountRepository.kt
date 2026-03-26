package com.sheetsync.data.repository

import com.sheetsync.data.local.entity.AccountRecord
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun getAllAccounts(): Flow<List<AccountRecord>>
    suspend fun getAccountById(accountId: Long): AccountRecord?
    suspend fun save(record: AccountRecord): Long
    suspend fun delete(record: AccountRecord)
}
