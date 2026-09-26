package com.issaczerubbabel.ledgar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {

    /** Same-source repeats are already rejected by the unique index on rawHash. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(capture: CapturedTransaction): Long

    @Update
    suspend fun update(capture: CapturedTransaction)

    @Query("SELECT * FROM captured_transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CapturedTransaction?

    @Query(
        "SELECT * FROM captured_transactions WHERE status IN ('PENDING', 'POSSIBLE_DUPLICATE') ORDER BY txnTime DESC"
    )
    fun observePending(): Flow<List<CapturedTransaction>>

    @Query("SELECT COUNT(*) FROM captured_transactions WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM captured_transactions WHERE refNumber = :ref LIMIT 1")
    suspend fun findByRef(ref: String): CapturedTransaction?

    @Query(
        """
        SELECT * FROM captured_transactions
        WHERE amount = :amount AND direction = :direction AND accountHint IS :accountHint
        AND txnTime BETWEEN :from AND :to
        LIMIT 1
        """
    )
    suspend fun findNear(amount: Double, direction: String, accountHint: String?, from: Long, to: Long): CapturedTransaction?

    @Query(
        "UPDATE captured_transactions SET status = 'CONFIRMED', confirmedExpenseId = :expenseId, finalCategory = :finalCategory WHERE id = :id"
    )
    suspend fun markConfirmed(id: Long, expenseId: Long, finalCategory: String?)

    @Query("UPDATE captured_transactions SET status = 'DISMISSED' WHERE id = :id")
    suspend fun markDismissed(id: Long)

    /** R3-Q1: turning auto-capture off clears queued work but never touches learned data. */
    @Query("DELETE FROM captured_transactions WHERE status = 'PENDING'")
    suspend fun deleteAllPending()
}
