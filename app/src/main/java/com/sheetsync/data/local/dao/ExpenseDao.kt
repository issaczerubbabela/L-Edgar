package com.sheetsync.data.local.dao

import androidx.room.*
import com.sheetsync.data.local.entity.ExpenseRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ExpenseRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ExpenseRecord>)

    @Update
    suspend fun update(record: ExpenseRecord)

    @Query("SELECT * FROM expense_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ExpenseRecord?

    @Query("SELECT * FROM expense_records WHERE syncAction != 'DELETE' ORDER BY date DESC")
    fun getAllRecords(): Flow<List<ExpenseRecord>>

    @Query("SELECT * FROM expense_records WHERE isBookmarked = 1 AND syncAction != 'DELETE' ORDER BY date DESC, id DESC")
    fun getBookmarkedTransactions(): Flow<List<ExpenseRecord>>

    @Query("SELECT * FROM expense_records")
    suspend fun getAllRecordsSnapshot(): List<ExpenseRecord>

    @Query("SELECT remoteTimestamp FROM expense_records WHERE remoteTimestamp IS NOT NULL AND remoteTimestamp != ''")
    suspend fun getAllRemoteTimestamps(): List<String>

    @Query("SELECT * FROM expense_records WHERE type = :type AND syncAction != 'DELETE' ORDER BY date DESC")
    fun getByType(type: String): Flow<List<ExpenseRecord>>

    @Query("SELECT * FROM expense_records WHERE isSynced = 0")
    suspend fun getUnsyncedRecords(): List<ExpenseRecord>

    @Query("UPDATE expense_records SET isSynced = 1, syncAction = 'NONE' WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Delete
    suspend fun delete(record: ExpenseRecord)

    @Query("UPDATE expense_records SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateBookmarkStatus(id: Long, isBookmarked: Boolean)

    @Query("DELETE FROM expense_records WHERE id = :id")
    suspend fun hardDeleteById(id: Long)

    @Query("DELETE FROM expense_records")
    suspend fun deleteAll()

        @Query(
                """
                SELECT COUNT(*) FROM expense_records
                WHERE syncAction != 'DELETE'
                    AND (accountId = :accountId OR fromAccountId = :accountId OR toAccountId = :accountId)
                """
        )
        suspend fun countRecordsForAccount(accountId: Long): Int

    /**
     * Duplicate detection: matches on date + type + category + amount.
     * Returns the first matching record, or null if it's a new entry.
     */
    @Query("""
        SELECT * FROM expense_records
        WHERE date = :date
          AND type = :type
          AND category = :category
          AND amount = :amount
        LIMIT 1
    """)
    suspend fun findDuplicate(date: String, type: String, category: String, amount: Double): ExpenseRecord?

    /**
     * Fetch all records between two dates (inclusive).
     * Used for month aggregation and weekly breakdowns.
     */
    @Query("""
        SELECT * FROM expense_records
        WHERE date >= :startDate AND date <= :endDate
          AND syncAction != 'DELETE'
        ORDER BY date DESC
    """)
    fun getRecordsByDateRange(startDate: String, endDate: String): Flow<List<ExpenseRecord>>

    @Query(
        """
        SELECT * FROM expense_records
                WHERE (fromAccountId = :accountId OR toAccountId = :accountId)
                    AND syncAction != 'DELETE'
        ORDER BY date DESC, id DESC
        """
    )
    fun getRecordsForAccount(accountId: Long): Flow<List<ExpenseRecord>>

    @Query(
        """
        SELECT * FROM expense_records
        WHERE accountId = :accountId
          AND date >= :startDate
          AND date <= :endDate
          AND syncAction != 'DELETE'
        ORDER BY date DESC, id DESC
        """
    )
    fun getRecordsForAccountInMonth(accountId: Long, startDate: String, endDate: String): Flow<List<ExpenseRecord>>

    @Query(
        """
        SELECT * FROM expense_records
        WHERE accountId = :accountId
          AND date >= :startDate
          AND date <= :endDate
          AND syncAction != 'DELETE'
        ORDER BY date ASC, id ASC
        """
    )
    fun getTransactionsForAccountInMonth(accountId: Long, startDate: String, endDate: String): Flow<List<ExpenseRecord>>

    @Query(
        """
        SELECT SUM(
            CASE
                WHEN type = 'Income' THEN amount
                WHEN type = 'Expense' THEN -amount
                ELSE 0
            END
        )
        FROM expense_records
        WHERE accountId = :accountId
          AND date < :beforeDate
          AND syncAction != 'DELETE'
        """
    )
    fun getHistoricalSumForAccount(accountId: Long, beforeDate: String): Flow<Double?>

    @Query(
        """
        SELECT a.initialBalance + COALESCE(SUM(
            CASE
                WHEN e.type = 'Income' THEN e.amount
                WHEN e.type = 'Expense' THEN -e.amount
                ELSE 0
            END
        ), 0)
        FROM account_records a
        LEFT JOIN expense_records e
            ON e.accountId = a.id
           AND e.syncAction != 'DELETE'
           AND e.date <= :endDate
        WHERE a.id = :accountId
        """
    )
    fun getAccountBalanceUntilDate(accountId: Long, endDate: String): Flow<Double>

    @Query(
        """
        SELECT a.initialBalance + COALESCE(SUM(
            CASE
                WHEN e.type = 'Income' AND e.toAccountId = :accountId THEN e.amount
                WHEN e.type = 'Expense' AND e.fromAccountId = :accountId THEN -e.amount
                WHEN e.type = 'Transfer' AND e.toAccountId = :accountId THEN e.amount
                WHEN e.type = 'Transfer' AND e.fromAccountId = :accountId THEN -e.amount
                ELSE 0
            END
        ), 0)
        FROM account_records a
        LEFT JOIN expense_records e
            ON (e.fromAccountId = a.id OR e.toAccountId = a.id)
           AND e.syncAction != 'DELETE'
        WHERE a.id = :accountId
        """
    )
    fun getAccountBalance(accountId: Long): Flow<Double>
}
