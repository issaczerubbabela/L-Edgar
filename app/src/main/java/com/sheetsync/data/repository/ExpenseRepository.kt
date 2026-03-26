package com.sheetsync.data.repository

import com.sheetsync.data.local.entity.ExpenseRecord
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    suspend fun save(record: ExpenseRecord): Long
    fun getAllRecords(): Flow<List<ExpenseRecord>>
    fun getByType(type: String): Flow<List<ExpenseRecord>>
    fun getRecordsForAccount(accountId: Long): Flow<List<ExpenseRecord>>
    fun getAccountBalance(accountId: Long): Flow<Double>
    fun getRecordsByDateRange(startDate: String, endDate: String): Flow<List<ExpenseRecord>>
    suspend fun getUnsynced(): List<ExpenseRecord>
    suspend fun markSynced(ids: List<Long>)
    suspend fun delete(record: ExpenseRecord)
    suspend fun deleteAll()
    suspend fun isDuplicate(date: String, type: String, category: String, amount: Double): Boolean
}
