package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.account.AccountMath
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import com.issaczerubbabel.ledgar.fund.FundsSnapshot
import java.time.LocalDate

/**
 * The one-time offer to raise a running cycle's spendable after Funds arrive mid-cycle (#156).
 *
 * Before Funds, a Transfer into an RD or SIP never came off Left to spend, so a spendable entered
 * net of those instalments was right. Once a Fund holds that Account whole, the same Transfers are
 * Set aside and come off Left to spend too (ADR-0011), counting them twice. A cycle started after
 * Funds is fine: Start new cycle already asks for the whole salary. Pure: no Room, no Android.
 */
object SpendableTopUp {

    /**
     * How much to raise [summary]'s spendable by, or null for no card: its Set aside so far plus the
     * Planned contributions still to transfer. Only for a running cycle that started before
     * [fundsSince] (the day this phone first had a Fund holding a whole Account) and that the user
     * hasn't answered, by Raise or Keep, in [answeredCycleIds].
     */
    fun suggest(summary: CycleSummary, fundsSince: LocalDate?, answeredCycleIds: Set<Long>): Double? {
        val cycle = summary.cycle
        if (fundsSince == null || cycle.closedAt != null || cycle.id in answeredCycleIds) return null
        val start = runCatching { LocalDate.parse(cycle.startDate) }.getOrNull() ?: return null
        if (!start.isBefore(fundsSince)) return null
        val paise = AccountMath.paise(summary.setAside) +
            summary.fundContributions.sumOf { AccountMath.paise(it.stillToTransfer) }
        return if (paise > 0L) AccountMath.rupees(paise) else null
    }

    /**
     * When this phone's data first had a Fund holding a whole Account, worked out from the Funds
     * when the app first sees one: for each active whole holding, the later of its Fund's creation
     * and the holding's start (a holding can be backdated, but never before its Fund existed), and
     * the earliest of those. Never after [today]; null with no whole holding. The app keeps the
     * first answer, so deleting or editing Funds later doesn't move it.
     */
    fun fundsSince(funds: FundsSnapshot, today: LocalDate): LocalDate? {
        val created = funds.activeFunds.associate { it.id to parse(it.createdDate) }
        return funds.activeHoldings
            .filter { it.holdingKind == HoldingKind.WHOLE }
            .map { holding -> listOfNotNull(created[holding.fundId], parse(holding.startDate)).maxOrNull() ?: today }
            .minOrNull()
            ?.let { if (it.isAfter(today)) today else it }
    }

    private fun parse(date: String): LocalDate? = runCatching { LocalDate.parse(date) }.getOrNull()
}
