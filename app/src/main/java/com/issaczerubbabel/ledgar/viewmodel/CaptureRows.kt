package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.capture.CaptureTrace
import com.issaczerubbabel.ledgar.capture.categorize.ConfidenceBand
import com.issaczerubbabel.ledgar.capture.categorize.DescriptionGuesser
import com.issaczerubbabel.ledgar.capture.notify.displayTitle
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
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
    /**
     * What Confirm saves as the Description: the user's edit, else the merchant's rename, else the
     * Suggested description, else the bank's text.
     */
    val title: String,
    /** The title as the bank wrote it, before any rename. */
    val bankTitle: String,
    /** What the user has typed in the title field, if they've edited it. */
    val descriptionDraft: String? = null,
    val categoryPickedByUser: Boolean = false,
    val alwaysUse: Boolean = false,
    /** The title is the Suggested description, not something the user or a rename chose. */
    val titleIsGuess: Boolean = false,
    /** Up to two other guessed Descriptions, shown as chips; tapping one replaces the title. */
    val descriptionAlternatives: List<String> = emptyList(),
    /** The Account was guessed from the source app; no alias is saved until Confirm. */
    val accountIsGuess: Boolean = false,
    /**
     * The Transaction already in the Ledger this capture probably repeats (#125): the card asks
     * "Already in your Ledger?" and offers Same one and Add anyway instead of Confirm.
     */
    val match: ExpenseRecord? = null
) {
    /** The merchant's name for "Always use": its rename or the bank's text, never a guessed Description. */
    val merchantTitle: String get() = if (titleIsGuess) bankTitle else title

    val isIncome: Boolean get() = type == TransactionType.INCOME
    val isTransfer: Boolean get() = type == TransactionType.TRANSFER

    /** The bank's direction, which a Transfer never flips: a debit leaves the capture's Account. */
    val isDebit: Boolean get() = capture.direction != "CREDIT"

    val canConfirm: Boolean
        get() = accountId != null && if (isTransfer) otherAccountId != null && otherAccountId != accountId else !category.isNullOrBlank()

    /** Only a capture with a merchant can have a rule saved for it. */
    val canOfferAlwaysUse: Boolean
        get() = !capture.merchantNorm.isNullOrBlank() && if (isTransfer) otherAccountId != null else !category.isNullOrBlank()

    /**
     * Safe to confirm in bulk: a High-confidence guess (only a saved rule is one, ADR-0010) whose
     * outcome and Account are both known, the Account not merely guessed, and that isn't possibly a
     * Transaction already logged, which only the user can decide.
     */
    val isBulkCandidate: Boolean get() = band == ConfidenceBand.HIGH && isReady

    /**
     * To review's Ready filter: a Category (or a Transfer's other Account) and a real Account, not a
     * guessed one, and no Transaction in the Ledger it may repeat. Everything else Needs a pick.
     */
    val isReady: Boolean get() = canConfirm && !accountIsGuess && match == null

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
     * other Account, or a guessed Account, that has since been deleted.
     *
     * A field the user touched ([edit]) keeps their value however often the capture is guessed
     * again; every other field shows the latest guess. The merchant's rename wins over the
     * Suggested description until the merchant has had different Descriptions
     * ([CaptureTrace.guessWinsOver]).
     */
    fun build(
        capture: CapturedTransaction,
        edit: CaptureRowEdit?,
        renames: Map<MerchantRuleKey, String>,
        knownAccountIds: Set<Long>,
        why: String? = CaptureTrace.parse(capture.traceJson).whyText,
        match: ExpenseRecord? = null
    ): CaptureRowUi {
        val trace = CaptureTrace.parse(capture.traceJson)
        val suggested = suggestedType(capture)
        val type = edit?.type ?: suggested
        val onSuggestedType = type == suggested
        val isTransfer = type == TransactionType.TRANSFER
        val suggestedOther = capture.suggestedToAccountId?.takeIf { it in knownAccountIds }
        val category = if (isTransfer) null else edit?.category ?: capture.suggestedCategory.takeIf { onSuggestedType }
        val otherAccountId = if (isTransfer) edit?.otherAccountId ?: suggestedOther.takeIf { onSuggestedType } else null
        val pickedByUser = edit?.category != null || edit?.otherAccountId != null
        val hasSuggestion = onSuggestedType && (capture.suggestedCategory != null || suggestedOther != null)
        val rename = capture.merchantNorm?.let { renames[MerchantRuleKey(it, capture.direction)] }?.trim()?.takeIf { it.isNotEmpty() }
        val bankTitle = capture.displayTitle()
        // A cleared field means the bank's own text, which also forgets an earlier rename.
        val title = edit?.description?.trim()?.ifEmpty { bankTitle } ?: capture.displayTitle(trace.titleBeforeBankText(rename))
        val guessedAccount = trace.guessedAccountId
            ?.takeIf { edit?.accountId == null && capture.accountId == null && it in knownAccountIds }
        return CaptureRowUi(
            capture = capture,
            type = type,
            category = category,
            accountId = edit?.accountId ?: capture.accountId ?: guessedAccount,
            otherAccountId = otherAccountId,
            // Picking an outcome yourself is a decision, so it no longer needs a second look.
            band = when {
                pickedByUser -> ConfidenceBand.HIGH
                else -> ConfidenceBand.of(capture.confidence, hasSuggestion)
            },
            why = why.takeIf { onSuggestedType },
            title = title,
            bankTitle = bankTitle,
            descriptionDraft = edit?.description,
            categoryPickedByUser = pickedByUser,
            alwaysUse = edit?.alwaysUse ?: false,
            titleIsGuess = edit?.description == null && trace.guessWinsOver(rename),
            descriptionAlternatives = (trace.descriptionGuesses + listOfNotNull(rename))
                .distinctBy { it.lowercase() }
                .filterNot { it.equals(title, ignoreCase = true) }
                .take(DescriptionGuesser.MAX_ALTERNATIVES),
            accountIsGuess = guessedAccount != null,
            match = match
        )
    }

    /**
     * The user picked [category] while the card was on [type]: pins that type too, so a later
     * re-guess that changes the suggested type can't switch the card away and hide the pick.
     */
    fun pickCategory(edit: CaptureRowEdit, type: String?, category: String): CaptureRowEdit =
        edit.copy(type = edit.type ?: type, category = category)

    /** The user picked a Transfer's other Account while the card was on [type]; pins it like [pickCategory]. */
    fun pickOtherAccount(edit: CaptureRowEdit, type: String?, accountId: Long): CaptureRowEdit =
        edit.copy(type = edit.type ?: type, otherAccountId = accountId)

    /** Switching the type forgets the Category and other Account picked for the old one. */
    fun switchType(edit: CaptureRowEdit, type: String): CaptureRowEdit =
        edit.copy(type = type, category = null, otherAccountId = null)
}
