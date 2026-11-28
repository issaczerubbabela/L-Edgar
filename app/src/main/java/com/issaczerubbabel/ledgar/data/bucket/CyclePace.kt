package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import com.issaczerubbabel.ledgar.fund.FundMath
import com.issaczerubbabel.ledgar.fund.FundsSnapshot
import com.issaczerubbabel.ledgar.util.RepeatSchedule
import com.issaczerubbabel.ledgar.util.TransactionType
import java.time.LocalDate

/** One date on which a Repeat rule will still add an Expense to the running cycle. */
data class DueRepeat(
    val rule: RecurringRule,
    val date: LocalDate,
    /** An Ask me first occurrence that is already due: it waits for Add or Skip. */
    val isWaiting: Boolean
) {
    val amount: Double get() = rule.amount

    /** The rule's earliest occurrence not yet added: the one Add and Skip act on. */
    val isNextForRule: Boolean get() = date.toString() == rule.nextDate

    /** Stable across recompositions: a rule has at most one occurrence per date. */
    val key: String get() = "${rule.id}-$date"
}

/**
 * A cycle's Daily pace: what it can spend per remaining day once the Repeats still due before its end
 * date are paid, `(Left to spend − Still due) ÷ days left`, never below zero (#135).
 *
 * This is the only Daily pace. Every screen, notification or widget that shows one gets it from [of]
 * rather than dividing Left to spend itself, so they all agree. Left to spend stays as CONTEXT.md
 * defines it (real Transactions only); Still due sits beside it and never comes off it.
 */
data class CyclePace(
    /** Expense occurrences of running Repeat rules still to add before the cycle ends, by date. */
    val stillDue: List<DueRepeat>,
    /** Zero once the cycle is overdue, or when Still due takes all that is left. */
    val dailyPace: Double,
    val leftToSpend: Double
) {
    val stillDueTotal: Double = stillDue.sumOf { it.amount }

    /** True when the pace has left room for Repeats, so it can say "after repeats". */
    val isAfterRepeats: Boolean get() = stillDue.isNotEmpty()

    /** The Repeats still due alone need more than Left to spend. */
    val stillDueExceedsLeft: Boolean get() = isAfterRepeats && stillDueTotal > leftToSpend

    companion object {

        /**
         * The pace for [summary]'s cycle on [today]. [funds] must be the snapshot [summary] was built
         * with, so an Expense Repeat paid from a Fund's whole Account comes out of the Fund here too.
         */
        fun of(
            summary: CycleSummary,
            rules: List<RecurringRule>,
            today: LocalDate,
            funds: FundsSnapshot = FundsSnapshot.EMPTY
        ): CyclePace {
            val stillDue = if (summary.isOverdue) emptyList() else stillDue(summary.cycle, rules, today, funds)
            return CyclePace(
                stillDue = stillDue,
                dailyPace = dailyPace(summary, stillDue.sumOf { it.amount }),
                leftToSpend = summary.leftToSpend
            )
        }

        /**
         * Every Expense occurrence of an active Repeat rule that will land in [cycle] and isn't in the
         * ledger yet: from the rule's next date (so an Ask me first occurrence that is waiting counts, and
         * so does an automatic one the worker hasn't added yet) through the cycle's end date. Each counts
         * once: when it's added it becomes a Transaction, the rule moves past it, and it drops out of here
         * as it lands in Spent.
         *
         * Left out: paused and finished rules, Income and Transfers (a Transfer into a Fund is Set aside,
         * which Planned contributions already plan), occurrences dated before the cycle starts (they'd
         * be added to an earlier cycle), Expenses paid from an Account a Fund holds whole (they come out
         * of the Fund, as in [CycleSummaryBuilder]), and anything for a closed cycle or one past its end.
         */
        fun stillDue(
            cycle: BudgetCycle,
            rules: List<RecurringRule>,
            today: LocalDate,
            funds: FundsSnapshot = FundsSnapshot.EMPTY
        ): List<DueRepeat> {
            if (cycle.closedAt != null) return emptyList()
            val start = LocalDate.parse(cycle.startDate)
            val end = LocalDate.parse(cycle.endDate)
            if (today.isAfter(end)) return emptyList()
            val holdings = funds.activeHoldings

            return rules
                .filter { it.type.trim().equals(TransactionType.EXPENSE, ignoreCase = true) && !it.isPaused }
                .flatMap { rule ->
                    val account = rule.fromAccountId ?: rule.accountId
                    RepeatSchedule.datesThrough(rule, end)
                        .filter { !it.isBefore(start) }
                        .filter { date -> account == null || FundMath.heldWholeOn(account, date, holdings) == null }
                        .map { date -> DueRepeat(rule, date, isWaiting = !rule.autoAdd && !date.isAfter(today)) }
                }
                .sortedWith(compareBy({ it.date }, { it.rule.id }))
        }

        /**
         * `(Left to spend − [stillDue]) ÷ days left`, floored at zero. The last day counts as one, and an
         * overdue cycle has no days left to pace, so its pace is zero.
         */
        fun dailyPace(summary: CycleSummary, stillDue: Double): Double {
            if (summary.isOverdue) return 0.0
            val spendable = summary.leftToSpend - stillDue
            return if (spendable <= 0.0) 0.0 else spendable / summary.daysLeft.coerceAtLeast(1)
        }
    }
}
