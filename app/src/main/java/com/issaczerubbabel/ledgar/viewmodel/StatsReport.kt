package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.DropdownRole
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

/** Which Categories and Account groups play a part in Stats (ADR-0004). Names are matched trimmed and case-insensitively. */
data class StatsRoles(
    val savingCategories: Set<String> = emptySet(),
    val refundCategories: Set<String> = emptySet(),
    val savingsGroups: Set<String> = emptySet()
) {
    fun isSaving(category: String) = category.key() in savingCategories
    fun isRefund(category: String) = category.key() in refundCategories
    fun isSavingsGroup(group: String?) = group != null && group.key() in savingsGroups

    companion object {
        fun from(options: List<DropdownOption>): StatsRoles {
            fun named(type: String, role: String) =
                options.filter { it.optionType == type && it.role == role }.mapTo(mutableSetOf()) { it.name.key() }
            return StatsRoles(
                savingCategories = named("EXPENSE_CATEGORY", DropdownRole.SAVING),
                refundCategories = named("INCOME_CATEGORY", DropdownRole.REFUND),
                savingsGroups = named("ACCOUNT_GROUP", DropdownRole.SAVINGS)
            )
        }
    }
}

data class StatsInput(
    val records: List<ExpenseRecord>,
    val accounts: List<AccountRecord>,
    val roles: StatsRoles,
    val cycles: List<BudgetCycle>,
    val period: StatsPeriod,
    val today: LocalDate,
    /** Narrows the timeline to one Category; Refunds and the budget line then drop out. */
    val timelineCategory: String? = null
)

/** Money in a period, in the terms of CONTEXT.md. */
data class MoneyTotals(
    val earned: Double = 0.0,
    /** Net of Refunds. */
    val spent: Double = 0.0,
    val saved: Double = 0.0,
    val refunds: Double = 0.0
) {
    val leftOver: Double get() = earned - spent - saved
}

data class CategoryAmount(val category: String, val amount: Double)

/** One bar of the timeline. [budget] is null for points no Salary cycle covers. */
data class TimelinePoint(
    val label: String,
    val start: LocalDate,
    val end: LocalDate,
    val earned: Double,
    val spent: Double,
    val budget: Double?
)

data class PaidFrom(val card: Double = 0.0, val cashAndAccounts: Double = 0.0, val transfers: Double = 0.0)

data class StatsReportUi(
    val totals: MoneyTotals = MoneyTotals(),
    /** Spent against the previous period at the same point in it; null when that had no spending. */
    val spentChangePercent: Int? = null,
    /** Spending Categories, largest first. Saving Categories are left out; Refunds are not taken off. */
    val expenseCategories: List<CategoryAmount> = emptyList(),
    /** Earning Categories, largest first. Refund Categories are left out. */
    val incomeCategories: List<CategoryAmount> = emptyList(),
    val timeline: List<TimelinePoint> = emptyList(),
    /** Average spent per timeline point, over the points up to today. */
    val averageSpentPerPoint: Double = 0.0,
    val paidFrom: PaidFrom = PaidFrom(),
    val hasTransactions: Boolean = false
) {
    val hasBudget: Boolean get() = timeline.any { it.budget != null }
}

/**
 * Everything the Stats tab shows for one period, worked out from plain rows. Pure: no Flow, Room or
 * chart library, so a test can call [build] with a handful of records and check the answer.
 */
object StatsReport {

    fun build(input: StatsInput): StatsReportUi {
        val dated = input.records.mapNotNull { r -> parseFlexibleDate(r.date)?.let { it to r } }
        val period = input.period
        val inPeriod = dated.filter { (d, _) -> d in period }
        val money = Money(input.roles, groupLookup(input.accounts))

        val totals = money.totals(inPeriod.map { it.second })
        val previousTotals = money.totals(dated.filter { (d, _) -> d in comparisonRange(period, input.today) }.map { it.second })
        val change = if (previousTotals.spent > 0.0) {
            ((totals.spent - previousTotals.spent) / previousTotals.spent * 100).roundToInt()
        } else {
            null
        }

        val allowance = DailyAllowance(input.cycles, input.today)
        val timeline = timelinePoints(period).map { (label, start, end) ->
            val slice = inPeriod.filter { (d, _) -> !d.isBefore(start) && !d.isAfter(end) }.map { it.second }
            val (earned, spent) = if (input.timelineCategory == null) {
                money.totals(slice).let { it.earned to it.spent }
            } else {
                money.categoryAmounts(slice, input.timelineCategory)
            }
            val budget = if (input.timelineCategory == null) allowance.over(start, end) else null
            TimelinePoint(label, start, end, earned, spent, budget)
        }
        val elapsed = timeline.filter { !it.start.isAfter(input.today) }
        val average = if (elapsed.isEmpty()) 0.0 else elapsed.sumOf { it.spent } / elapsed.size

        return StatsReportUi(
            totals = totals,
            spentChangePercent = change,
            expenseCategories = money.byCategory(inPeriod.map { it.second }, "Expense") { !input.roles.isSaving(it) },
            incomeCategories = money.byCategory(inPeriod.map { it.second }, "Income") { !input.roles.isRefund(it) },
            timeline = timeline,
            averageSpentPerPoint = average,
            paidFrom = money.paidFrom(inPeriod.map { it.second }),
            hasTransactions = inPeriod.isNotEmpty()
        )
    }

    /**
     * The stretch of the previous period to compare with. A period still running is compared with the
     * same number of days from the start of the previous one, so day 25 is measured against day 25.
     */
    internal fun comparisonRange(period: StatsPeriod, today: LocalDate): ClosedRange<LocalDate> {
        val previous = period.previous()
        if (!today.isBefore(period.end) || today.isBefore(period.start)) return previous.start..previous.end
        val elapsedDays = ChronoUnit.DAYS.between(period.start, today)
        val end = previous.start.plusDays(elapsedDays).coerceAtMost(previous.end)
        return previous.start..end
    }

    private fun timelinePoints(period: StatsPeriod): List<Triple<String, LocalDate, LocalDate>> {
        val spansMonths = YearMonth.from(period.start) != YearMonth.from(period.end)
        val dayLabel = DateTimeFormatter.ofPattern(if (spansMonths) "d MMM" else "d", Locale.ENGLISH)
        return when (period.resolution) {
            StatsResolution.DAY -> generateSequence(period.start) { it.plusDays(1) }
                .takeWhile { !it.isAfter(period.end) }
                .map { Triple(it.format(dayLabel), it, it) }
                .toList()

            StatsResolution.WEEK -> {
                val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
                generateSequence(period.start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) { it.plusWeeks(1) }
                    .takeWhile { !it.isAfter(period.end) }
                    .map { monday ->
                        val start = maxOf(monday, period.start)
                        Triple(start.format(fmt), start, minOf(monday.plusDays(6), period.end))
                    }
                    .toList()
            }

            StatsResolution.MONTH -> {
                val fmt = DateTimeFormatter.ofPattern(if (period.start.year == period.end.year) "MMM" else "MMM yy", Locale.ENGLISH)
                generateSequence(YearMonth.from(period.start)) { it.plusMonths(1) }
                    .takeWhile { !it.atDay(1).isAfter(period.end) }
                    .map { m -> Triple(m.format(fmt), maxOf(m.atDay(1), period.start), minOf(m.atEndOfMonth(), period.end)) }
                    .toList()
            }
        }
    }

    private operator fun StatsPeriod.contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)

    private fun groupLookup(accounts: List<AccountRecord>): (Long?, String?) -> String? {
        val byId = accounts.associate { it.id to it.groupName }
        val byName = accounts.associate { it.accountName.key() to it.groupName }
        return { id, name -> id?.let(byId::get) ?: name?.let { byName[it.key()] } }
    }

    /** The money rules of ADR-0004, applied to any list of Transactions. */
    private class Money(val roles: StatsRoles, val groupOf: (Long?, String?) -> String?) {

        fun totals(records: List<ExpenseRecord>): MoneyTotals {
            var earned = 0.0
            var grossSpent = 0.0
            var saved = 0.0
            var refunds = 0.0
            records.forEach { r ->
                when (r.kind()) {
                    Kind.EXPENSE -> if (roles.isSaving(r.category)) saved += r.amount else grossSpent += r.amount
                    Kind.INCOME -> if (roles.isRefund(r.category)) refunds += r.amount else earned += r.amount
                    Kind.TRANSFER -> {
                        val intoSavings = roles.isSavingsGroup(groupOf(r.toAccountId, r.toAccountName))
                        val outOfSavings = roles.isSavingsGroup(groupOf(r.fromAccountId, r.fromAccountName))
                        if (intoSavings && !outOfSavings) saved += r.amount
                        if (outOfSavings && !intoSavings) saved -= r.amount
                    }
                    Kind.OTHER -> Unit
                }
            }
            return MoneyTotals(earned = earned, spent = grossSpent - refunds, saved = saved, refunds = refunds)
        }

        /** Earned and spent for one Category only. */
        fun categoryAmounts(records: List<ExpenseRecord>, category: String): Pair<Double, Double> {
            val mine = records.filter { it.categoryName().key() == category.key() }
            return mine.filter { it.kind() == Kind.INCOME }.sumOf { it.amount } to
                mine.filter { it.kind() == Kind.EXPENSE }.sumOf { it.amount }
        }

        fun byCategory(records: List<ExpenseRecord>, type: String, include: (String) -> Boolean): List<CategoryAmount> {
            val kind = if (type == "Expense") Kind.EXPENSE else Kind.INCOME
            return records.asSequence()
                .filter { it.kind() == kind && include(it.category) }
                .groupBy { it.categoryName() }
                .map { (name, items) -> CategoryAmount(name, items.sumOf { it.amount }) }
                .sortedByDescending { it.amount }
        }

        fun paidFrom(records: List<ExpenseRecord>): PaidFrom {
            val spending = records.filter { it.kind() == Kind.EXPENSE && !roles.isSaving(it.category) }
            val (card, other) = spending.partition { groupOf(it.accountId, it.accountName)?.contains("card", ignoreCase = true) == true }
            return PaidFrom(
                card = card.sumOf { it.amount },
                cashAndAccounts = other.sumOf { it.amount },
                transfers = records.filter { it.kind() == Kind.TRANSFER }.sumOf { it.amount }
            )
        }
    }

    /** Each day's share of the Salary cycle covering it: spendable amount ÷ the cycle's planned days. */
    private class DailyAllowance(cycles: List<BudgetCycle>, private val today: LocalDate) {
        private val spans = cycles.mapNotNull { c ->
            val start = parseFlexibleDate(c.startDate) ?: return@mapNotNull null
            val plannedEnd = parseFlexibleDate(c.endDate) ?: return@mapNotNull null
            val planned = ChronoUnit.DAYS.between(start, plannedEnd) + 1
            if (planned <= 0) return@mapNotNull null
            // A running cycle stays open past its end date until the next one starts.
            val end = if (c.closedAt == null) maxOf(plannedEnd, today) else plannedEnd
            Triple(start, end, c.spendableAmount / planned)
        }.sortedByDescending { it.first }

        private fun on(day: LocalDate): Double? =
            spans.firstOrNull { (start, end, _) -> !day.isBefore(start) && !day.isAfter(end) }?.third

        fun over(start: LocalDate, end: LocalDate): Double? {
            var total = 0.0
            var covered = false
            var day = start
            while (!day.isAfter(end)) {
                on(day)?.let { total += it; covered = true }
                day = day.plusDays(1)
            }
            return if (covered) total else null
        }
    }

    private enum class Kind { EXPENSE, INCOME, TRANSFER, OTHER }

    private fun ExpenseRecord.kind(): Kind = when (type.trim().lowercase()) {
        "expense" -> Kind.EXPENSE
        "income" -> Kind.INCOME
        "transfer" -> Kind.TRANSFER
        else -> Kind.OTHER
    }

    private fun ExpenseRecord.categoryName(): String = category.trim().ifBlank { "Uncategorized" }
}

private fun String.key(): String = trim().lowercase()
