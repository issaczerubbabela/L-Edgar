package com.issaczerubbabel.ledgar.ledger

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.formatListMoney
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** One Sunday-to-Saturday week of a month on Monthly, clipped to the month, so a month's weeks add up to it. */
data class LedgerWeek(
    val start: LocalDate,
    val end: LocalDate,
    /** "4 – 10 Oct". */
    val label: String,
    val income: String,
    val expense: String,
    val net: String,
    /** Where Daily scrolls when the week is tapped: its newest day with Transactions, null when it has none. */
    val scrollTo: LocalDate?
)

/** One month on Monthly. */
data class LedgerMonthRow(
    val month: YearMonth,
    /** "Oct". */
    val name: String,
    /** "1 – 31 Oct". */
    val range: String,
    val income: String,
    val expense: String,
    val net: String,
    /** In calendar order. */
    val weeks: List<LedgerWeek>
)

/** Monthly for one year: the year's totals and its months, newest first. */
data class LedgerYear(
    val year: Int,
    val summary: LedgerSummary,
    /** Up to this month in the current year, all 12 in a past one, none in a future one. */
    val months: List<LedgerMonthRow>
)

private val monthNameFormat = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

/** Monthly for [year], counted like the Ledger: the same filter, and deletes waiting on Undo left out. */
fun Ledger.year(
    transactions: List<ExpenseRecord>,
    year: Int,
    today: LocalDate,
    pendingDeleteIds: Set<Long> = emptySet(),
    filter: LedgerFilter = LedgerFilter()
): LedgerYear {
    val byDate = transactions.asSequence()
        .filter { it.id !in pendingDeleteIds && filter.matches(it) }
        .mapNotNull { txn -> dateOf(txn)?.takeIf { it.year == year }?.let { it to txn } }
        .groupBy({ it.first }, { it.second })
    val lastMonth = when {
        year < today.year -> 12
        year == today.year -> today.monthValue
        else -> 0
    }

    fun between(start: LocalDate, end: LocalDate): List<ExpenseRecord> =
        byDate.filterKeys { !it.isBefore(start) && !it.isAfter(end) }.values.flatten()

    val months = (lastMonth downTo 1).map { m ->
        val month = YearMonth.of(year, m)
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        val weeks = generateSequence(first) { it.with(TemporalAdjusters.next(DayOfWeek.SUNDAY)) }
            .takeWhile { !it.isAfter(last) }
            .map { start ->
                val end = minOf(start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)), last)
                val totals = Totals.of(between(start, end))
                LedgerWeek(
                    start = start,
                    end = end,
                    label = "${start.dayOfMonth} – ${end.format(dayMonthFormat)}",
                    income = formatListMoney(totals.income),
                    expense = formatListMoney(totals.expense),
                    net = formatListMoney(totals.net),
                    scrollTo = byDate.keys.filter { !it.isBefore(start) && !it.isAfter(end) }.maxOrNull()
                )
            }.toList()
        val totals = Totals.of(between(first, last))
        LedgerMonthRow(
            month = month,
            name = first.format(monthNameFormat),
            range = "1 – ${last.format(dayMonthFormat)}",
            income = formatListMoney(totals.income),
            expense = formatListMoney(totals.expense),
            net = formatListMoney(totals.net),
            weeks = weeks
        )
    }
    val totals = Totals.of(byDate.values.flatten())
    return LedgerYear(
        year = year,
        summary = LedgerSummary(
            income = formatListMoney(totals.income),
            expenses = formatListMoney(totals.expense),
            net = formatListMoney(totals.net)
        ),
        months = months
    )
}
