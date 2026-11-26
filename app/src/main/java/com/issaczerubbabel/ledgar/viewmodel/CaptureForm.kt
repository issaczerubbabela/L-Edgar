package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.repository.CaptureEdits
import com.issaczerubbabel.ledgar.util.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The Add Transaction form's Account fields. */
data class FormAccounts(val accountId: Long?, val fromAccountId: Long?, val toAccountId: Long?)

/** What the Add Transaction form starts with when opened with Edit on a capture. */
data class CaptureFormFill(
    val type: String,
    val category: String,
    val accountId: Long?,
    val fromAccountId: Long?,
    val toAccountId: Long?,
    val description: String,
    val amount: Double,
    val date: LocalDate,
    val alwaysUse: Boolean
)

/**
 * The Add Transaction form opened from a capture. The bank's direction fixes which side of a
 * Transfer the capture's own Account is on: From for a debit, To for a credit.
 */
object CaptureForm {

    val WAITING = setOf("PENDING", "POSSIBLE_DUPLICATE")

    /** [draft] is the card's unsaved choices, if it had any; [title] is the card's title. */
    fun prefill(capture: CapturedTransaction, draft: CaptureEdits?, title: String, zone: ZoneId = ZoneId.systemDefault()): CaptureFormFill {
        val suggested = CaptureRows.suggestedType(capture)
        val type = draft?.type ?: suggested
        val onSuggested = type == suggested
        val isTransfer = type == TransactionType.TRANSFER
        val captureSide = draft?.accountId?.takeIf { it > 0L } ?: capture.accountId
        val other = if (isTransfer) draft?.otherAccountId ?: capture.suggestedToAccountId.takeIf { onSuggested } else null
        val accounts = place(capture, type, captureSide, other)
        return CaptureFormFill(
            type = type,
            category = when {
                isTransfer -> TransactionType.TRANSFER
                draft != null -> draft.category
                onSuggested -> capture.suggestedCategory.orEmpty()
                else -> ""
            },
            accountId = accounts.accountId,
            fromAccountId = accounts.fromAccountId,
            toAccountId = accounts.toAccountId,
            description = draft?.description ?: title,
            amount = draft?.amount ?: capture.amount,
            date = draft?.date ?: Instant.ofEpochMilli(capture.txnTime).atZone(zone).toLocalDate(),
            alwaysUse = draft?.alwaysUse ?: false
        )
    }

    /** Puts the capture's own Account, and a Transfer's other Account, in the form's fields for [type]. */
    fun place(capture: CapturedTransaction, type: String, captureSideId: Long?, otherAccountId: Long?): FormAccounts {
        if (type != TransactionType.TRANSFER) return FormAccounts(captureSideId, null, null)
        return if (capture.direction != "CREDIT") {
            FormAccounts(null, captureSideId, otherAccountId)
        } else {
            FormAccounts(null, otherAccountId, captureSideId)
        }
    }

    /** The capture's own Account as the form shows it for [type]. */
    fun captureSideAccountId(capture: CapturedTransaction, type: String, accountId: Long?, fromAccountId: Long?, toAccountId: Long?): Long? =
        when {
            type != TransactionType.TRANSFER -> accountId
            capture.direction != "CREDIT" -> fromAccountId
            else -> toAccountId
        }

    /** What Save confirms the capture with, or null while its own Account is missing. */
    fun toEdits(
        capture: CapturedTransaction,
        type: String,
        category: String,
        accountId: Long?,
        fromAccountId: Long?,
        toAccountId: Long?,
        description: String,
        remarks: String,
        amount: Double,
        date: LocalDate,
        alwaysUse: Boolean
    ): CaptureEdits? {
        val isTransfer = type == TransactionType.TRANSFER
        val captureSide = captureSideAccountId(capture, type, accountId, fromAccountId, toAccountId) ?: return null
        val other = when {
            !isTransfer -> null
            capture.direction != "CREDIT" -> toAccountId ?: return null
            else -> fromAccountId ?: return null
        }
        return CaptureEdits(
            type = type,
            category = if (isTransfer) TransactionType.TRANSFER else category,
            accountId = captureSide,
            description = description,
            alwaysUse = alwaysUse,
            otherAccountId = other,
            amount = amount,
            date = date,
            remarks = remarks
        )
    }
}
