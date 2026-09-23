package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.remote.ImportRecordDto
import kotlinx.coroutines.flow.Flow

data class SyncConflict(
    val localTx: ExpenseRecord,
    val sheetTx: ImportRecordDto
)

data class GoogleSheetsImportResult(
    val imported: Int,
    val skipped: Int,
    val restoredDropdowns: Int,
    val restoredBudgets: Int = 0,
    val restoredAccounts: Int = 0,
    val conflicts: List<SyncConflict> = emptyList()
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
    suspend fun setBookmarked(id: Long, isBookmarked: Boolean)
    suspend fun deleteTransactionsByIds(ids: List<Long>)
    suspend fun updateTransactionsDateByIds(ids: List<Long>, newDate: String)
    suspend fun updateTransactionsCategoryByIds(ids: List<Long>, newCategory: String)
    suspend fun updateTransactionsAssetByIds(ids: List<Long>, accountId: Long)
    suspend fun updateTransactionsDescriptionByIds(ids: List<Long>, newDescription: String)
    suspend fun delete(record: ExpenseRecord)
    suspend fun deleteAll()
    suspend fun isDuplicate(date: String, type: String, category: String, amount: Double): Boolean
    suspend fun importFromGoogleSheets(): GoogleSheetsImportResult

    /** Transactions changed differently on the phone and in the Sheet since their last Sync. */
    fun observeSyncConflicts(): Flow<List<SyncConflict>>
    suspend fun resolveConflictKeepPhone(conflict: SyncConflict)
    suspend fun resolveConflictKeepSheet(conflict: SyncConflict)
    suspend fun resolveConflictKeepBoth(conflict: SyncConflict)
    suspend fun resolveConflictDeleteEverywhere(conflict: SyncConflict)

    /** Re-uploads Transactions a Pull found missing from the Sheet, instead of deleting them. */
    suspend fun keepTransactionsMissingFromSheet(ids: Collection<Long>)

    /** Groups of Transactions that look identical: same date, type, category, amount, description and account. */
    fun observePossibleDuplicates(): Flow<List<List<ExpenseRecord>>>
}
