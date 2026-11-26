package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.capture.categorize.ConfidenceBand
import com.issaczerubbabel.ledgar.capture.notify.displayTitle
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleKey
import com.issaczerubbabel.ledgar.data.repository.CaptureEdits
import com.issaczerubbabel.ledgar.util.TransactionType

/** One pending capture with whatever the user has already changed on it. */
data class CaptureRowUi(
    val capture: CapturedTransaction,
    /** Expense, Income or Transfer: the card's switch. */
    val type: String,
    val category: String?,
    /** The capture's own Account: From for a debit Transfer, To for a credit one. */
    val accountId: Long?,
    /** A Transfer's other Account: To for a debit, From for a credit. Null for anything else. */
    val otherAccountId: Long? = null,
    val band: ConfidenceBand,
    val why: String?,
    /** What Confirm saves as the Description: the user's edit, else the merchant's rename, else the bank's text. */
    val title: String,
    /** The title as the bank wrote it, before any rename. */
    val bankTitle: String,
    /** What the user has typed in the title field, if they've edited it. */
    val descriptionDraft: String? = null,
    val categoryPickedByUser: Boolean = false,
    val alwaysUse: Boolean = false
) {
    val isIncome: Boolean get() = type == TransactionType.INCOME
    val isTransfer: Boolean get() = type == TransactionType.TRANSFER

    /** The bank's direction, which a Transfer never flips: a debit leaves the capture's Account. */
    val isDebit: Boolean get() = capture.direction != "CREDIT"

    val canConfirm: Boolean
        get() = accountId != null && if (isTransfer) otherAccountId != null && otherAccountId != accountId else !category.isNullOrBlank()

    /** Only a capture with a merchant can have a rule saved for it. */
    val canOfferAlwaysUse: Boolean
        get() = !capture.merchantNorm.isNullOrBlank() && if (isTransfer) otherAccountId != null else !category.isNullOrBlank()

    /** Safe to confirm in bulk: a High-confidence guess whose outcome and Account are both known. */
    val isBulkCandidate: Boolean get() = band == ConfidenceBand.HIGH && canConfirm

    /** What Confirm saves; null while something is still missing. */
    fun toEdits(): CaptureEdits? {
        if (!canConfirm) return null
        return CaptureEdits(
            type = type,
            category = if (isTransfer) TransactionType.TRANSFER else category.orEmpty(),
            accountId = accountId ?: return null,
            description = title,
            alwaysUse = alwaysUse,
            otherAccountId = otherAccountId.takeIf { isTransfer }
        )
    }
}

/** What the user changed on a card before deciding. */
data class CaptureRowEdit(
    val type: String? = null,
    val category: String? = null,
    val accountId: Long? = null,
    val otherAccountId: Long? = null,
    val alwaysUse: Boolean = false,
    val description: String? = null
)

object CaptureRows {

    /** The type a capture starts on: its suggestion, else what the bank's direction implies. */
    fun suggestedType(capture: CapturedTransaction): String =
        capture.suggestedType ?: if (capture.direction == "CREDIT") TransactionType.INCOME else TransactionType.EXPENSE

    /**
     * The card for [capture]. A suggestion only applies while the card is on the suggested type: a
     * switched card starts without a Category or other Account. [knownAccountIds] drops a suggested
     * other Account that has since been deleted.
     */
    fun build(
        capture: CapturedTransaction,
        edit: CaptureRowEdit?,
        renames: Map<MerchantRuleKey, String>,
        knownAccountIds: Set<Long>,
        why: String?
    ): CaptureRowUi {
        val suggested = suggestedType(capture)
        val type = edit?.type ?: suggested
        val onSuggestedType = type == suggested
        val isTransfer = type == TransactionType.TRANSFER
        val suggestedOther = capture.suggestedToAccountId?.takeIf { it in knownAccountIds }
        val category = if (isTransfer) null else edit?.category ?: capture.suggestedCategory.takeIf { onSuggestedType }
        val otherAccountId = if (isTransfer) edit?.otherAccountId ?: suggestedOther.takeIf { onSuggestedType } else null
        val pickedByUser = edit?.category != null || edit?.otherAccountId != null
        val hasSuggestion = onSuggestedType && (capture.suggestedCategory != null || suggestedOther != null)
        val rename = capture.merchantNorm?.let { renames[MerchantRuleKey(it, capture.direction)] }
        return CaptureRowUi(
            capture = capture,
            type = type,
            category = category,
            accountId = edit?.accountId ?: capture.accountId,
            otherAccountId = otherAccountId,
            // Picking an outcome yourself is a decision, so it no longer needs a second look.
            band = when {
                pickedByUser -> ConfidenceBand.HIGH
                else -> ConfidenceBand.of(capture.confidence, hasSuggestion)
            },
            why = why.takeIf { onSuggestedType },
            // A cleared field means the bank's own text, which also forgets an earlier rename.
            title = edit?.description?.trim()?.ifEmpty { capture.displayTitle() } ?: capture.displayTitle(rename),
            bankTitle = capture.displayTitle(),
            descriptionDraft = edit?.description,
            categoryPickedByUser = pickedByUser,
            alwaysUse = edit?.alwaysUse ?: false
        )
    }

    /** Switching the type forgets the Category and other Account picked for the old one. */
    fun switchType(edit: CaptureRowEdit, type: String): CaptureRowEdit =
        edit.copy(type = type, category = null, otherAccountId = null)
}
