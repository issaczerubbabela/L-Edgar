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
        "SELECT * FROM captured_transactions WHERE status IN ('PENDING', 'POSSIBLE_DUPLICATE') ORDER BY capturedAt DESC, id DESC"
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

    /** The other half of a confirmed Transfer (ADR-0006): resolved by the same Transaction. */
    @Query("UPDATE captured_transactions SET status = 'PAIRED', confirmedExpenseId = :expenseId, finalCategory = 'Transfer' WHERE id = :id")
    suspend fun markPaired(id: Long, expenseId: Long)

    /** Captures still waiting in one direction within a time window: a Transfer's possible other halves. */
    @Query(
        "SELECT * FROM captured_transactions WHERE status IN ('PENDING', 'POSSIBLE_DUPLICATE') " +
            "AND direction = :direction AND txnTime BETWEEN :from AND :to"
    )
    suspend fun getWaiting(direction: String, from: Long, to: Long): List<CapturedTransaction>

    /** The user turned this capture into a Trip expense instead of a Transaction (ADR-0008). */
    @Query("UPDATE captured_transactions SET status = 'TRIP' WHERE id = :id")
    suspend fun markAddedToTrip(id: Long)

    @Query("UPDATE captured_transactions SET status = 'DISMISSED' WHERE id = :id")
    suspend fun markDismissed(id: Long)

    /** Once an account number is known, earlier captures waiting with that number pick it up too. */
    @Query(
        "UPDATE captured_transactions SET accountId = :accountId WHERE accountHint = :hint AND accountId IS NULL " +
            "AND status IN ('PENDING', 'POSSIBLE_DUPLICATE')"
    )
    suspend fun assignAccountToPending(hint: String, accountId: Long)

    /**
     * Writes a re-guess onto a capture that is still waiting. Only the guess columns change, and a
     * capture confirmed or dismissed meanwhile is left alone, so a re-guess can never undo a Confirm.
     */
    @Query(
        """
        UPDATE captured_transactions
        SET suggestedType = :suggestedType, suggestedCategory = :suggestedCategory,
            suggestedToAccountId = :suggestedToAccountId, confidence = :confidence,
            decidedBy = :decidedBy, traceJson = :traceJson
        WHERE id = :id AND status IN ('PENDING', 'POSSIBLE_DUPLICATE')
        """
    )
    suspend fun updateGuesses(
        id: Long,
        suggestedType: String?,
        suggestedCategory: String?,
        suggestedToAccountId: Long?,
        confidence: Double,
        decidedBy: String?,
        traceJson: String?
    )

    /** Every waiting capture, for re-guessing. */
    @Query("SELECT * FROM captured_transactions WHERE status IN ('PENDING', 'POSSIBLE_DUPLICATE')")
    suspend fun getPendingSnapshot(): List<CapturedTransaction>

    /** R3-Q1: turning auto-capture off clears queued work but never touches learned data. */
    @Query("DELETE FROM captured_transactions WHERE status = 'PENDING'")
    suspend fun deleteAllPending()
}
