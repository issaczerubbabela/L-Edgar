package com.sheetsync.data.local.dao

import androidx.room.*
import com.sheetsync.data.local.entity.ExpenseRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ExpenseRecord): Long

    @Query("SELECT * FROM expense_records ORDER BY date DESC")
    fun getAllRecords(): Flow<List<ExpenseRecord>>

    @Query("SELECT * FROM expense_records WHERE type = :type ORDER BY date DESC")
    fun getByType(type: String): Flow<List<ExpenseRecord>>

    @Query("SELECT * FROM expense_records WHERE isSynced = 0")
    suspend fun getUnsyncedRecords(): List<ExpenseRecord>

    @Query("UPDATE expense_records SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Delete
    suspend fun delete(record: ExpenseRecord)

    @Query("DELETE FROM expense_records")
    suspend fun deleteAll()

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
        ORDER BY date DESC
    """)
    fun getRecordsByDateRange(startDate: String, endDate: String): Flow<List<ExpenseRecord>>

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
            ON e.fromAccountId = a.id OR e.toAccountId = a.id
        WHERE a.id = :accountId
        """
    )
    fun getAccountBalance(accountId: Long): Flow<Double>
}
