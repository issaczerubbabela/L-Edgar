package com.issaczerubbabel.ledgar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A bank/UPI alert parsed by auto-capture but not yet turned into an [ExpenseRecord]. See the
 * "Captured transaction" entry in CONTEXT.md and ADR-0005: only [status] CONFIRMED comes from an
 * explicit user action, never written automatically.
 */
@Entity(
    tableName = "captured_transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountRecord::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["rawHash"], unique = true),
        Index(value = ["refNumber"]),
        Index(value = ["status"]),
        Index(value = ["txnTime"]),
        Index(value = ["accountId"])
    ]
)
data class CapturedTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * [sources], [sender], [rawText], [rawHash] and [capturedAt] are the first Alert's, the one that
     * made this capture. Every Alert, that one included, is a [CaptureAlert]; the other columns are
     * what the Alerts say together (`AlertMerge`).
     */
    val sources: String,
    val sender: String,
    val rawText: String,
    /** sha256 of source+sender+normalized text; [CaptureAlert.rawHash] is the dedupe that counts. */
    val rawHash: String,
    val capturedAt: Long,
    val txnTime: Long,
    val amount: Double,
    val direction: String, // DEBIT | CREDIT
    val channel: String, // UPI | CARD | NEFT | IMPS | ATM | UNKNOWN
    val merchantRaw: String? = null,
    val merchantNorm: String? = null,
    val accountHint: String? = null,
    val accountId: Long? = null,
    val refNumber: String? = null,
    val suggestedType: String? = null, // Expense | Income | Transfer
    /** Null for a suggested Transfer, which suggests [suggestedToAccountId] instead. */
    val suggestedCategory: String? = null,
    val confidence: Double = 0.0,
    val decidedBy: String? = null,
    val traceJson: String? = null,
    /**
     * PENDING | CONFIRMED | DISMISSED | DUPLICATE | POSSIBLE_DUPLICATE | TRIP | PAIRED | LINKED. PAIRED
     * is the other half of a confirmed Transfer (ADR-0006): it shares that Transfer's [confirmedExpenseId].
     * LINKED is the user's Same one: the alert for a Transaction already logged, whose id it holds in
     * [confirmedExpenseId] without writing a new one. POSSIBLE_DUPLICATE waits like PENDING but matched a
     * logged Transaction when it arrived, so it was never notified; the inbox re-matches every waiting
     * capture live either way.
     */
    val status: String = "PENDING",
    val confirmedExpenseId: Long? = null,
    val finalCategory: String? = null,
    /**
     * A suggested Transfer's other Account, from a Merchant rule: the To-account of a debit, the
     * From-account of a credit. Not a foreign key, so it can point at a deleted Account; check it.
     */
    val suggestedToAccountId: Long? = null
)
