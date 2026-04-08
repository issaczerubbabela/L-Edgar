package com.sheetsync.data.repository

import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.remote.ImportRecordDto
import kotlinx.coroutines.flow.Flow

data class GoogleSheetsImportResult(
    val imported: Int,
    val skipped: Int,
    val restoredDropdowns: Int,
    val restoredBudgets: Int = 0,
    val restoredAccounts: Int = 0
)

interface ExpenseRepository {
    suspend fun save(record: ExpenseRecord): Long
    suspend fun getById(id: Long): ExpenseRecord?
    suspend fun update(record: ExpenseRecord)
    suspend fun hardDeleteById(id: Long)
    fun getAllRecords(): Flow<List<ExpenseRecord>>
    fun getBookmarkedTransactions(): Flow<List<ExpenseRecord>>
    fun getByType(type: String): Flow<List<ExpenseRecord>>
    fun getRecordsForAccount(accountId: Long): Flow<List<ExpenseRecord>>
    fun searchTransactions(
        query: String?,
        startDate: String?,
        endDate: String?,
        accountId: Long?,
        category: String?,
        minAmount: Double?,
        maxAmount: Double?
    ): Flow<List<ExpenseRecord>>
    fun getRecordsForAccountInMonth(accountId: Long, startDate: String, endDate: String): Flow<List<ExpenseRecord>>
    fun getTransactionsForAccountInMonth(accountId: Long, startOfMonth: String, endOfMonth: String): Flow<List<ExpenseRecord>>
    fun getHistoricalSumForAccount(accountId: Long, beforeDate: String): Flow<Double?>
    fun getAccountBalanceUntilDate(accountId: Long, endDate: String): Flow<Double>
    fun getAccountBalance(accountId: Long): Flow<Double>
    fun getRecordsByDateRange(startDate: String, endDate: String): Flow<List<ExpenseRecord>>
    suspend fun getUnsynced(): List<ExpenseRecord>
    suspend fun markSynced(ids: List<Long>)
    suspend fun setBookmarked(id: Long, isBookmarked: Boolean)
    suspend fun delete(record: ExpenseRecord)
    suspend fun deleteAll()
    suspend fun isDuplicate(date: String, type: String, category: String, amount: Double): Boolean
    suspend fun importRemoteRecords(records: List<ImportRecordDto>): Int
    suspend fun importFromGoogleSheets(): GoogleSheetsImportResult
}
