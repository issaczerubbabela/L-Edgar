package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.Fund
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import com.issaczerubbabel.ledgar.fund.FundsSnapshot
import com.issaczerubbabel.ledgar.util.amountToInput
import com.issaczerubbabel.ledgar.util.parseAmountInput
import java.time.LocalDate

/** One holding as the New / Edit Fund sheet edits it. An Account is in a draft at most once. */
data class HoldingDraft(
    /** The saved holding's id, or 0 for one added in this sheet. */
    val holdingId: Long = 0,
    val accountId: Long,
    val kind: HoldingKind,
    /** The fixed amount; unused for a whole-Account holding. */
    val amountInput: String = "",
    /** Money already there before tracking began. Blank on a fixed holding means the amount itself. */
    val openingInput: String = "",
    val startDate: LocalDate
) {
    val amount: Double? get() = parseAmountInput(amountInput)?.takeIf { it > 0.0 }
    val amountError: Boolean get() = kind == HoldingKind.FIXED && amount == null
    val openingError: Boolean get() = openingInput.isNotBlank() && (parseAmountInput(openingInput)?.let { it < 0.0 } ?: true)
}

/** What the New / Edit Fund sheet holds while it's open. */
data class FundDraft(
    /** The Fund being edited, or 0 for a new one. */
    val fundId: Long = 0,
    val name: String = "",
    val emoji: String = "",
    val colorIndex: Int = 0,
    val targetInput: String = "",
    val dueDate: LocalDate? = null,
    val holdings: List<HoldingDraft> = emptyList(),
    /** Where a new Fund goes in the list: after every other one. */
    val sortOrder: Int = 0
) {
    val isNew: Boolean get() = fundId == 0L
    val target: Double? get() = parseAmountInput(targetInput)?.takeIf { it > 0.0 }
    val targetError: Boolean get() = targetInput.isNotBlank() && target == null
    val canSave: Boolean
        get() = name.isNotBlank() && !targetError && holdings.none { it.amountError || it.openingError }
}

/** The New / Edit Fund sheet's rules, kept pure so they're tested without Room. */
object FundEditor {

    fun newDraft(snapshot: FundsSnapshot): FundDraft = FundDraft(
        colorIndex = snapshot.funds.size % BudgetBucket.COLOR_COUNT,
        sortOrder = (snapshot.funds.maxOfOrNull { it.sortOrder } ?: -1) + 1
    )

    fun draftOf(fund: Fund, snapshot: FundsSnapshot): FundDraft = FundDraft(
        fundId = fund.id,
        name = fund.name,
        emoji = fund.emoji,
        colorIndex = fund.colorIndex,
        targetInput = fund.targetAmount?.let(::amountToInput).orEmpty(),
        dueDate = fund.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        holdings = snapshot.holdings.filter { it.fundId == fund.id }
            .sortedWith(compareBy<FundHolding> { it.holdingKind.ordinal }.thenBy { it.id })
            .map { holding ->
                HoldingDraft(
                    holdingId = holding.id,
                    accountId = holding.accountId,
                    kind = holding.holdingKind,
                    amountInput = if (holding.holdingKind == HoldingKind.FIXED) amountToInput(holding.amount) else "",
                    openingInput = amountToInput(holding.openingAmount),
                    startDate = runCatching { LocalDate.parse(holding.startDate) }.getOrDefault(LocalDate.now())
                )
            },
        sortOrder = fund.sortOrder
    )

    /**
     * The Accounts a new [kind] holding can use: visible ones not already in [draft], and not held
     * whole by another Fund. A whole holding also can't take an Account another Fund holds part of.
     * Archived Funds count, as the repository checks every holding.
     */
    fun accountChoices(kind: HoldingKind, draft: FundDraft, snapshot: FundsSnapshot, accounts: List<AccountRecord>): List<AccountRecord> {
        val inDraft = draft.holdings.mapTo(HashSet()) { it.accountId }
        val others = snapshot.holdings.filter { it.fundId != draft.fundId || draft.isNew }
        val heldWhole = others.filter { it.holdingKind == HoldingKind.WHOLE }.mapTo(HashSet()) { it.accountId }
        val heldInPart = others.filter { it.holdingKind == HoldingKind.FIXED }.mapTo(HashSet()) { it.accountId }
        return accounts.filter { account ->
            !account.isHidden && account.id !in inDraft && account.id !in heldWhole &&
                (kind == HoldingKind.FIXED || account.id !in heldInPart)
        }
    }

    /** [draft] with a new [kind] holding in [accountId], starting [today]. A whole one opens with the Account's [balance]. */
    fun addHolding(draft: FundDraft, kind: HoldingKind, accountId: Long, balance: Double, today: LocalDate): FundDraft {
        if (draft.holdings.any { it.accountId == accountId }) return draft
        val holding = HoldingDraft(
            accountId = accountId,
            kind = kind,
            openingInput = if (kind == HoldingKind.WHOLE) amountToInput(maxOf(balance, 0.0)) else "",
            startDate = today
        )
        return draft.copy(holdings = draft.holdings + holding)
    }

    fun toFund(draft: FundDraft, existing: Fund?, today: LocalDate): Fund {
        val base = existing ?: Fund(name = "", createdDate = today.toString(), sortOrder = draft.sortOrder)
        return base.copy(
            name = draft.name.trim(),
            emoji = draft.emoji.trim(),
            colorIndex = draft.colorIndex,
            targetAmount = draft.target,
            dueDate = draft.dueDate?.toString()
        )
    }

    fun toHoldings(draft: FundDraft, fundId: Long): List<FundHolding> = draft.holdings.map { holding ->
        val amount = if (holding.kind == HoldingKind.FIXED) holding.amount ?: 0.0 else 0.0
        val opening = parseAmountInput(holding.openingInput)?.coerceAtLeast(0.0)
            ?: if (holding.kind == HoldingKind.FIXED) amount else 0.0
        FundHolding(
            id = holding.holdingId,
            fundId = fundId,
            accountId = holding.accountId,
            kind = holding.kind.name,
            amount = amount,
            openingAmount = opening,
            startDate = holding.startDate.toString()
        )
    }

    /** The saved holdings of [draft]'s Fund that the sheet removed. */
    fun removedHoldingIds(draft: FundDraft, snapshot: FundsSnapshot): List<Long> {
        if (draft.isNew) return emptyList()
        val kept = draft.holdings.mapTo(HashSet()) { it.holdingId }
        return snapshot.holdings.filter { it.fundId == draft.fundId && it.id !in kept }.map { it.id }
    }
}
