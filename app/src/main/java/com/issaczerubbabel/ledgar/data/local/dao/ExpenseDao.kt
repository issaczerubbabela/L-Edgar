package com.issaczerubbabel.ledgar.data.local.dao

import androidx.room.*
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
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

        @Query(
                """
                SELECT * FROM expense_records
                WHERE syncAction != 'DELETE'
                    AND (
                                :query IS NULL OR :query = ''
                                OR description LIKE '%' || :query || '%'
                                OR remarks LIKE '%' || :query || '%'
                            )
                    AND (:startDate IS NULL OR date >= :startDate)
                    AND (:endDate IS NULL OR date <= :endDate)
                    AND (
                                :accountId IS NULL
                                OR accountId = :accountId
                                OR fromAccountId = :accountId
                                OR toAccountId = :accountId
                            )
                    AND (:category IS NULL OR category = :category)
                    AND (:minAmount IS NULL OR amount >= :minAmount)
                    AND (:maxAmount IS NULL OR amount <= :maxAmount)
                ORDER BY date DESC, id DESC
                """
        )
        fun searchTransactions(
                query: String?,
                startDate: String?,
                endDate: String?,
                accountId: Long?,
                category: String?,
                minAmount: Double?,
                maxAmount: Double?
        ): Flow<List<ExpenseRecord>>

    @Query("SELECT * FROM expense_records WHERE isSynced = 0")
    suspend fun getUnsyncedRecords(): List<ExpenseRecord>

    @Query("UPDATE expense_records SET isSynced = 1, syncAction = 'NONE' WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Delete
    suspend fun delete(record: ExpenseRecord)

    @Query("UPDATE expense_records SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateBookmarkStatus(id: Long, isBookmarked: Boolean)

    @Query(
        """
        UPDATE expense_records
        SET isSynced = 0,
            syncAction = 'DELETE'
        WHERE id IN (:ids)
          AND syncAction != 'DELETE'
        """
    )
    suspend fun markTransactionsDeletedByIds(ids: List<Long>)

    @Query(
        """
        UPDATE expense_records
        SET isSynced = 0,
            syncAction = 'DELETE'
        WHERE id = :id
          AND syncAction != 'DELETE'
        """
    )
    suspend fun markTransactionDeletedById(id: Long)

    @Query(
        """
        UPDATE expense_records
        SET date = :newDate,
            isSynced = 0,
            syncAction = 'UPDATE'
        WHERE id IN (:ids)
        """
    )
    suspend fun updateTransactionsDateByIds(ids: List<Long>, newDate: String)

    @Query(
        """
        UPDATE expense_records
        SET category = :newCategory,
            isSynced = 0,
            syncAction = 'UPDATE'
        WHERE id IN (:ids)
        """
    )
    suspend fun updateTransactionsCategoryByIds(ids: List<Long>, newCategory: String)

    @Query(
        """
        UPDATE expense_records
        SET accountId = CASE WHEN type IN ('Expense', 'Income') THEN :accountId ELSE accountId END,
            fromAccountId = CASE WHEN type = 'Expense' THEN :accountId ELSE fromAccountId END,
            toAccountId = CASE WHEN type = 'Income' THEN :accountId ELSE toAccountId END,
            isSynced = 0,
            syncAction = 'UPDATE'
        WHERE id IN (:ids)
        """
    )
    suspend fun updateTransactionsAssetByIds(ids: List<Long>, accountId: Long)

    @Query(
        """
        UPDATE expense_records
        SET description = :newDescription,
            isSynced = 0,
            syncAction = 'UPDATE'
        WHERE id IN (:ids)
        """
    )
    suspend fun updateTransactionsDescriptionByIds(ids: List<Long>, newDescription: String)

    @Query("DELETE FROM expense_records WHERE id = :id")
    suspend fun hardDeleteById(id: Long)

    @Query(
        """
        DELETE FROM expense_records
        WHERE accountId = :accountId OR fromAccountId = :accountId OR toAccountId = :accountId
        """
    )
    suspend fun deleteLinkedTransactionsForAccount(accountId: Long)

    @Query(
        """
        UPDATE expense_records
        SET accountId = CASE WHEN accountId = :fromAccountId THEN :toAccountId ELSE accountId END,
            fromAccountId = CASE WHEN fromAccountId = :fromAccountId THEN :toAccountId ELSE fromAccountId END,
            toAccountId = CASE WHEN toAccountId = :fromAccountId THEN :toAccountId ELSE toAccountId END,
            isSynced = 0,
            syncAction = CASE
                WHEN syncAction = 'INSERT' THEN 'INSERT'
                WHEN syncAction = 'DELETE' THEN 'DELETE'
                ELSE 'UPDATE'
            END
        WHERE (accountId = :fromAccountId OR fromAccountId = :fromAccountId OR toAccountId = :fromAccountId)
          AND syncAction != 'DELETE'
        """
    )
    suspend fun reassignLinkedTransactionsForAccount(fromAccountId: Long, toAccountId: Long)

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
                SELECT COALESCE(SUM(
            CASE
                                WHEN e.type = 'Income' AND (e.toAccountId = :accountId OR e.accountId = :accountId) THEN e.amount
                                WHEN e.type = 'Expense' AND (e.fromAccountId = :accountId OR e.accountId = :accountId) THEN -e.amount
                                WHEN e.type = 'Transfer' AND e.toAccountId = :accountId THEN e.amount
                                WHEN e.type = 'Transfer' AND e.fromAccountId = :accountId THEN -e.amount
                ELSE 0
            END
                ), 0)
                FROM expense_records e
                INNER JOIN account_records a ON a.id = :accountId
                WHERE (e.accountId = :accountId OR e.fromAccountId = :accountId OR e.toAccountId = :accountId)
                    AND e.date < :beforeDate
                    AND e.date >= a.initialBalanceDate
                    AND e.syncAction != 'DELETE'
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
              AND e.date >= a.initialBalanceDate
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
           AND e.date >= a.initialBalanceDate
        WHERE a.id = :accountId
        """
    )
    fun getAccountBalance(accountId: Long): Flow<Double>
}
