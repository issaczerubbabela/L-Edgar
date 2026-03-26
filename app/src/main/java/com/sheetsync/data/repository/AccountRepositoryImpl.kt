package com.sheetsync.data.repository

import com.sheetsync.data.local.dao.AccountDao
import com.sheetsync.data.local.entity.AccountRecord
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AccountRepositoryImpl @Inject constructor(
    private val dao: AccountDao
) : AccountRepository {

    override fun getAllAccounts(): Flow<List<AccountRecord>> = dao.getAllAccounts()

    override suspend fun save(record: AccountRecord): Long = dao.insert(record)

    override suspend fun delete(record: AccountRecord) = dao.delete(record)
}
