package com.issaczerubbabel.ledgar.fund

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import com.issaczerubbabel.ledgar.util.formatRupees

/** The offer to keep money a Transfer took out of a Fund's whole Account earmarked where it landed. */
data class EarmarkOffer(
    val fundId: Long,
    val fundName: String,
    val toAccountId: Long,
    val toAccountName: String,
    val amount: Double,
    /** The Transfer's date, yyyy-MM-dd: a new holding starts on it. */
    val date: String
) {
    /** The snackbar's text: "₹15,000 left Emergency for HDFC". */
    val message: String get() = "${formatRupees(amount)} left $fundName for $toAccountName"
}

/**
 * "Keep earmarked" (ADR-0011): the label never follows money, so a Transfer out of a Fund's whole
 * Account releases it to Free, and the app offers one tap to add a FIXED holding where it landed.
 */
object KeepEarmarked {

    /**
     * The offer for [transfer], or null: only a Transfer out of an Account an active Fund holds whole,
     * into one no Fund holds whole. A Transfer into another Fund's Account is a move, already labelled.
     */
    fun offerFor(transfer: ExpenseRecord, snapshot: FundsSnapshot, accountNames: Map<Long, String>): EarmarkOffer? {
        if (!transfer.type.trim().equals("transfer", ignoreCase = true)) return null
        if (!(transfer.amount > 0.0)) return null
        val from = transfer.fromAccountId ?: return null
        val to = transfer.toAccountId ?: return null
        if (from == to) return null
        val fundId = FundMath.wholeAccounts(snapshot.activeHoldings)[from] ?: return null
        // The repository won't add a fixed holding where any Fund, even an archived one, holds the Account whole.
        if (to in FundMath.wholeAccounts(snapshot.holdings)) return null
        val fund = snapshot.activeFunds.firstOrNull { it.id == fundId } ?: return null
        return EarmarkOffer(
            fundId = fundId,
            fundName = fund.name,
            toAccountId = to,
            toAccountName = accountNames[to].orEmpty().ifBlank { "the other account" },
            amount = transfer.amount,
            date = transfer.date
        )
    }

    /** The holding that keeps [offer]'s money earmarked: the Fund's fixed holding there raised, or a new one. */
    fun holdingFor(offer: EarmarkOffer, snapshot: FundsSnapshot): FundHolding {
        val existing = snapshot.holdings.firstOrNull {
            it.fundId == offer.fundId && it.accountId == offer.toAccountId && it.holdingKind == HoldingKind.FIXED
        }
        return existing?.copy(amount = existing.amount + offer.amount)
            ?: FundHolding(
                fundId = offer.fundId,
                accountId = offer.toAccountId,
                kind = HoldingKind.FIXED.name,
                amount = offer.amount,
                openingAmount = offer.amount,
                startDate = offer.date
            )
    }
}
