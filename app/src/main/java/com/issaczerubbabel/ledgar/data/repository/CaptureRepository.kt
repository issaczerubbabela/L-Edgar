package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.UnparsedAlert
import kotlinx.coroutines.flow.Flow

/** What the user settled on when confirming a Captured transaction. */
data class CaptureEdits(
    val type: String,
    val category: String,
    val accountId: Long,
    val description: String,
    /** "Always use this": saves a Merchant rule that is never changed automatically. */
    val alwaysUse: Boolean = false
)

interface CaptureRepository {
    fun observePending(): Flow<List<CapturedTransaction>>
    fun observePendingCount(): Flow<Int>

    suspend fun getById(captureId: Long): CapturedTransaction?

    /** Merchants the user renamed, by normalized merchant name. */
    fun observeRenames(): Flow<Map<String, String>>

    /** The capture's title: its merchant's rename, else the bank's text, else "Payment" or "Money received". */
    suspend fun titleFor(capture: CapturedTransaction): String

    /**
     * Turns a pending capture into a real Transaction and returns its id, or null if the capture
     * is gone or already resolved. The Transaction, the capture's new status and what the app
     * learns from it are saved together or not at all. A description other than the bank's text
     * is remembered on the merchant's rule; the bank's text clears it.
     */
    suspend fun confirm(captureId: Long, edits: CaptureEdits): Long?

    suspend fun dismiss(captureId: Long)

    /** Clears queued captures and the unreadable-alerts list. Learned rules and account aliases are kept. */
    suspend fun clearPending()

    fun observeUnparsed(): Flow<List<UnparsedAlert>>
    fun observeUnparsedCount(): Flow<Int>
    suspend fun deleteUnparsed(id: Long)
    suspend fun clearUnparsed()
}
