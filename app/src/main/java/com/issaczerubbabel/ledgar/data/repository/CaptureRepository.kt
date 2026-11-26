package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.capture.pair.TransferPairing
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleKey
import com.issaczerubbabel.ledgar.data.local.entity.UnparsedAlert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** What the user settled on when confirming a Captured transaction. */
data class CaptureEdits(
    /** Expense, Income or Transfer. The bank's direction never changes with it. */
    val type: String,
    /** Ignored for a Transfer, which is saved under the Category "Transfer". */
    val category: String,
    /** The capture's own Account: the From-account of a debit Transfer, the To-account of a credit one. */
    val accountId: Long,
    val description: String,
    /** "Always use this": saves a Merchant rule that is never changed automatically. */
    val alwaysUse: Boolean = false,
    /** A Transfer's other Account: the To-account of a debit, the From-account of a credit. */
    val otherAccountId: Long? = null,
    /** Set from Edit: replaces the amount in the alert. */
    val amount: Double? = null,
    /** Set from Edit: replaces the date in the alert. */
    val date: LocalDate? = null,
    val remarks: String = ""
)

interface CaptureRepository {
    fun observePending(): Flow<List<CapturedTransaction>>
    fun observePendingCount(): Flow<Int>

    suspend fun getById(captureId: Long): CapturedTransaction?

    /** Merchants the user renamed, by normalized merchant name and direction. */
    fun observeRenames(): Flow<Map<MerchantRuleKey, String>>

    /** The capture's title: its merchant's rename, else the bank's text, else "Payment" or "Money received". */
    suspend fun titleFor(capture: CapturedTransaction): String

    /**
     * Turns a pending capture into a real Transaction and returns its id, or null if the capture
     * is gone or already resolved. The Transaction, the capture's new status and what the app
     * learns from it are saved together or not at all. A description other than the bank's text
     * is remembered on the merchant's rule; the bank's text clears it.
     *
     * A Transfer also resolves its other half in the same Room transaction when exactly one waiting
     * capture matches (see [findTransferPair]): that capture becomes PAIRED with the same Transaction id.
     */
    suspend fun confirm(captureId: Long, edits: CaptureEdits): Long?

    /**
     * What [confirm] would pair with this capture if confirmed with [edits] now, so the Undo
     * snackbar can say so and hide both cards. [TransferPairing.None] for anything but a Transfer.
     */
    suspend fun findTransferPair(captureId: Long, edits: CaptureEdits): TransferPairing

    suspend fun dismiss(captureId: Long)

    /** Clears queued captures and the unreadable-alerts list. Learned rules and account aliases are kept. */
    suspend fun clearPending()

    fun observeUnparsed(): Flow<List<UnparsedAlert>>
    fun observeUnparsedCount(): Flow<Int>
    suspend fun deleteUnparsed(id: Long)
    suspend fun clearUnparsed()
}
