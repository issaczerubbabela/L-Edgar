package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Builds the Accounts card on the Insights screen.
 *
 * Card versus cash is decided by the group of the account each expense was paid from
 * (Card, Debit Card, Cash, ...). It used to read a payment-mode field that the app never saves,
 * so every expense fell into "cash" and the card total was always zero.
 */
object AccountsBreakdownCalculator {

    fun build(
        records: List<ExpenseRecord>,
        range: StatsDateRange,
        groupNameByAccountId: Map<Long, String>
    ): AccountsBreakdownUi {
        val periodDays = (ChronoUnit.DAYS.between(range.start, range.end) + 1).coerceAtLeast(1)
        val previousEnd = range.start.minusDays(1)
        val previousStart = previousEnd.minusDays(periodDays - 1)

        val current = records.withinRange(range.start, range.end)
        val previous = records.withinRange(previousStart, previousEnd)

        val currentExpenses = current.filter { it.type == "Expense" }
        val currentExpense = currentExpenses.sumOf { it.amount }
        val previousExpense = previous.filter { it.type == "Expense" }.sumOf { it.amount }

        val (card, cash) = currentExpenses.partition { isCard(it, groupNameByAccountId) }

        return AccountsBreakdownUi(
            cashAndAccountsExpense = cash.sumOf { it.amount },
            cardExpense = card.sumOf { it.amount },
            transferTotal = current.filter { it.type == "Transfer" }.sumOf { it.amount },
            changePercent = if (previousExpense <= 0.0) {
                null
            } else {
                (((currentExpense - previousExpense) / previousExpense) * 100).roundToInt()
            }
        )
    }

    private fun isCard(record: ExpenseRecord, groupNameByAccountId: Map<Long, String>): Boolean {
        val group = record.accountId?.let { groupNameByAccountId[it] } ?: return false
        return group.contains("card", ignoreCase = true)
    }

    private fun List<ExpenseRecord>.withinRange(start: LocalDate, end: LocalDate): List<ExpenseRecord> =
        filter { record ->
            val date = parseFlexibleDate(record.date) ?: return@filter false
            !date.isBefore(start) && !date.isAfter(end)
        }
}
