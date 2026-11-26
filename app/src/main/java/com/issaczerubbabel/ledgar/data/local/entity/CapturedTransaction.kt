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
    /** Comma-separated capture sources that produced or merged into this row: NOTIF, SMS, EMAIL. */
    val sources: String,
    val sender: String,
    val rawText: String,
    /** sha256 of source+sender+normalized text; the unique index is same-source dedupe. */
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
     * PENDING | CONFIRMED | DISMISSED | DUPLICATE | POSSIBLE_DUPLICATE | TRIP | PAIRED. PAIRED is the
     * other half of a confirmed Transfer (ADR-0006): it shares that Transfer's [confirmedExpenseId].
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
