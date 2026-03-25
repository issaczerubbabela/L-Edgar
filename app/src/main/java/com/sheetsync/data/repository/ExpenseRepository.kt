package com.sheetsync.data.repository

import com.sheetsync.data.local.entity.ExpenseRecord
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    suspend fun save(record: ExpenseRecord): Long
    fun getAllRecords(): Flow<List<ExpenseRecord>>
    fun getByType(type: String): Flow<List<ExpenseRecord>>
    suspend fun getUnsynced(): List<ExpenseRecord>
    suspend fun markSynced(ids: List<Long>)
    suspend fun delete(record: ExpenseRecord)
}
