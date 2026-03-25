package com.sheetsync.data.repository

import com.sheetsync.data.local.dao.ExpenseDao
import com.sheetsync.data.local.entity.ExpenseRecord
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExpenseRepositoryImpl @Inject constructor(
    private val dao: ExpenseDao
) : ExpenseRepository {

    override suspend fun save(record: ExpenseRecord): Long = dao.insert(record)

    override fun getAllRecords(): Flow<List<ExpenseRecord>> = dao.getAllRecords()

    override fun getByType(type: String): Flow<List<ExpenseRecord>> = dao.getByType(type)

    override suspend fun getUnsynced(): List<ExpenseRecord> = dao.getUnsyncedRecords()

    override suspend fun markSynced(ids: List<Long>) = dao.markAsSynced(ids)

    override suspend fun delete(record: ExpenseRecord) = dao.delete(record)
}
