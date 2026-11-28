package com.issaczerubbabel.ledgar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.issaczerubbabel.ledgar.data.local.entity.CaptureAlert
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

    @Query("SELECT COUNT(*) FROM captured_transactions WHERE status IN ('PENDING', 'POSSIBLE_DUPLICATE')")
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

    /**
     * A Link (#125): the capture is the bank's alert for a Transaction the user had already logged,
     * so it resolves to that Transaction's id without writing a new one.
     */
    @Query("UPDATE captured_transactions SET status = 'LINKED', confirmedExpenseId = :expenseId, finalCategory = :finalCategory WHERE id = :id")
    suspend fun markLinked(id: Long, expenseId: Long, finalCategory: String?)

    /** Transactions a capture already resolved (Confirm, a Transfer's other half or a Link): each absorbs one alert. */
    @Query("SELECT DISTINCT confirmedExpenseId FROM captured_transactions WHERE confirmedExpenseId IS NOT NULL")
    fun observeLinkedExpenseIds(): Flow<List<Long>>

    @Query("SELECT DISTINCT confirmedExpenseId FROM captured_transactions WHERE confirmedExpenseId IS NOT NULL")
    suspend fun getLinkedExpenseIds(): List<Long>

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

    /** Rewrites a waiting capture's trace, say to keep a Split; a resolved capture is left alone. */
    @Query("UPDATE captured_transactions SET traceJson = :traceJson WHERE id = :id AND status IN ('PENDING', 'POSSIBLE_DUPLICATE')")
    suspend fun updateTrace(id: Long, traceJson: String)

    /**
     * The captures a Transaction came from by a Confirm or a Link, so editing it in the Ledger can
     * teach their Merchant rules. A Transfer's PAIRED other half is left out: its Confirm never taught it.
     */
    @Query("SELECT * FROM captured_transactions WHERE confirmedExpenseId IN (:expenseIds) AND status IN ('CONFIRMED', 'LINKED')")
    suspend fun getTeachingFor(expenseIds: List<Long>): List<CapturedTransaction>

    /** Every waiting capture, for re-guessing. */
    @Query("SELECT * FROM captured_transactions WHERE status IN ('PENDING', 'POSSIBLE_DUPLICATE')")
    suspend fun getPendingSnapshot(): List<CapturedTransaction>

    /** A repeat of an Alert already stored, on any capture, is rejected by the unique index on rawHash: -1. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlert(alert: CaptureAlert): Long

    @Query("SELECT EXISTS(SELECT 1 FROM capture_alerts WHERE rawHash = :rawHash)")
    suspend fun hasAlert(rawHash: String): Boolean

    /** A capture's Alerts, in the order they arrived. */
    @Query("SELECT * FROM capture_alerts WHERE captureId = :captureId ORDER BY capturedAt, id")
    suspend fun getAlerts(captureId: Long): List<CaptureAlert>

    @Query("SELECT * FROM capture_alerts WHERE captureId IN (:captureIds) ORDER BY capturedAt, id")
    suspend fun getAlertsFor(captureIds: List<Long>): List<CaptureAlert>

    /** The Alerts of every waiting capture, for the inbox's Original text and Not the same payment. */
    @Query(
        "SELECT a.* FROM capture_alerts a INNER JOIN captured_transactions c ON c.id = a.captureId " +
            "WHERE c.status IN ('PENDING', 'POSSIBLE_DUPLICATE') ORDER BY a.capturedAt, a.id"
    )
    fun observePendingAlerts(): Flow<List<CaptureAlert>>

    /** Not the same payment: an Alert leaves its capture for one of its own. */
    @Query("UPDATE capture_alerts SET captureId = :captureId WHERE id = :alertId")
    suspend fun moveAlert(alertId: Long, captureId: Long)

    /**
     * Captures, in any status, that a new Alert could repeat (ADR-0012): the same reference number,
     * or the same direction and amount with an Alert that arrived between [from] and [to].
     * `SamePaymentMatcher` decides among them.
     */
    @Query(
        """
        SELECT * FROM captured_transactions
        WHERE (:refNumber IS NOT NULL AND refNumber = :refNumber)
           OR (direction = :direction AND amount BETWEEN :amount - 0.005 AND :amount + 0.005
               AND id IN (SELECT captureId FROM capture_alerts WHERE capturedAt BETWEEN :from AND :to))
        """
    )
    suspend fun getSamePaymentCandidates(refNumber: String?, direction: String, amount: Double, from: Long, to: Long): List<CapturedTransaction>

    /** R3-Q1: turning auto-capture off clears queued work but never touches learned data. */
    @Query("DELETE FROM captured_transactions WHERE status IN ('PENDING', 'POSSIBLE_DUPLICATE')")
    suspend fun deleteAllPending()
}
