package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.bucket.CycleSummary
import com.issaczerubbabel.ledgar.data.bucket.CycleSummaryBuilder
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
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
    /** The buckets and routing of the cycle on screen. Only read when [period] is a [StatsPeriod.Cycle]. */
    val buckets: List<BudgetBucket> = emptyList(),
    val assignments: List<BucketCategory> = emptyList()
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

/** One bar of the timeline. [budget] is null for points no Salary cycle covers. */
data class TimelinePoint(
    val label: String,
    val start: LocalDate,
    val end: LocalDate,
    val earned: Double,
    val spent: Double,
    val budget: Double?
)

/** Running totals across the period, for the pace chart. */
data class Pace(
    val labels: List<String> = emptyList(),
    /** Up to and including today's point only. */
    val current: List<Double> = emptyList(),
    /** The whole previous period, point by point. */
    val previous: List<Double> = emptyList(),
    /**
     * Running budget for every point, null before the first point a Salary cycle covers; empty when
     * no cycle covers the period at all.
     */
    val budget: List<Double?> = emptyList(),
    /** Where spending is heading by the last point, while the period is still running. */
    val projection: Double? = null,
    /** The previous period's running total at the same point as today. */
    val previousAtSamePoint: Double? = null
) {
    val spentSoFar: Double get() = current.lastOrNull() ?: 0.0
}

/** A spending Category with what it usually costs: the average of the three periods before. */
data class CategoryRow(val category: String, val amount: Double, val usual: Double?)

data class DayCell(val date: LocalDate, val spent: Double, val isFuture: Boolean, val step: Int)

data class TrendBar(val label: String, val earned: Double, val spent: Double, val saved: Double, val isCurrent: Boolean) {
    val leftOver: Double get() = earned - spent - saved
}

data class PaidFrom(val card: Double = 0.0, val cashAndAccounts: Double = 0.0, val transfers: Double = 0.0)

/** Left to spend in a Salary cycle, worked out exactly as the Budget tab does. */
data class CycleView(val summary: CycleSummary, val isRunning: Boolean, val saved: Double)

data class StatsReportUi(
    val totals: MoneyTotals = MoneyTotals(),
    /** Spent against the previous period at the same point in it; null when that had no spending. */
    val spentChangePercent: Int? = null,
    /** Left over in the previous period at the same point, for the headline's comparison. */
    val previousLeftOver: Double? = null,
    /** Set in Cycle view. */
    val cycle: CycleView? = null,
    val pace: Pace = Pace(),
    /** Spending Categories, largest first. Saving Categories are left out (except in Cycle view). */
    val categories: List<CategoryRow> = emptyList(),
    val timeline: List<TimelinePoint> = emptyList(),
    /** One cell per day for periods up to 62 days long; empty otherwise. */
    val days: List<DayCell> = emptyList(),
    /** The period on screen and the ones before it, oldest first. */
    val trend: List<TrendBar> = emptyList(),
    val paidFrom: PaidFrom = PaidFrom(),
    val biggest: List<ExpenseRecord> = emptyList(),
    val hasTransactions: Boolean = false
) {
    val hasBudget: Boolean get() = pace.budget.isNotEmpty()
}

/** One Category or Bucket opened from the breakdown. */
data class CategoryDetail(
    val title: String,
    val spent: Double,
    /** Average of the three periods before this one, as on the breakdown row. */
    val usual: Double?,
    val perDay: Double,
    val trend: List<TrendBar>,
    val transactions: List<ExpenseRecord>
)

/**
 * Everything the Stats tab shows for one period, worked out from plain rows. Pure: no Flow, Room or
 * chart library, so a test can call [build] with a handful of records and check the answer.
 *
 * Week, Month, Year and custom ranges follow the money rules of ADR-0004. Cycle view matches the
 * Budget tab instead: every Expense counts against the spendable amount, Saving Categories included,
 * because a Bucket can hold savings.
 */
object StatsReport {

    private const val TREND_LENGTH = 8
    private const val CALENDAR_MAX_DAYS = 62

    /** A running period isn't compared until this many days in: day 1 against day 1 is noise. */
    private const val MIN_DAYS_TO_COMPARE = 3

    fun build(input: StatsInput): StatsReportUi {
        val period = input.period
        val today = input.today
        val budgetBasis = period is StatsPeriod.Cycle
        val money = Money(input.roles, groupLookup(input.accounts), budgetBasis)
        val dated = input.records.mapNotNull { r -> parseFlexibleDate(r.date)?.let { it to r } }
        fun within(start: LocalDate, end: LocalDate) = dated.filter { (d, _) -> !d.isBefore(start) && !d.isAfter(end) }.map { it.second }

        val inPeriod = within(period.start, period.end)
        val totals = money.totals(inPeriod)
        val comparable = hasRealPrevious(period) && daysIn(period, today) >= MIN_DAYS_TO_COMPARE
        val comparison = comparisonRange(period, today)
        val previousTotals = money.totals(within(comparison.start, comparison.endInclusive))
        val change = if (comparable && previousTotals.spent > 0.0) {
            ((totals.spent - previousTotals.spent) / previousTotals.spent * 100).roundToInt()
        } else {
            null
        }

        val allowance = DailyAllowance(input.cycles, today)
        fun timelineOf(p: StatsPeriod): List<TimelinePoint> = timelinePoints(p).map { (label, start, end) ->
            val slice = within(start, end)
            val t = money.totals(slice)
            TimelinePoint(label, start, end, t.earned, t.spent, allowance.over(start, end))
        }
        val timeline = timelineOf(period)
        // A previous period with no spending at all (before the user's first records) draws no line.
        val previousTimeline = if (hasRealPrevious(period)) timelineOf(period.previous()).takeIf { t -> t.any { it.spent != 0.0 } }.orEmpty() else emptyList()

        return StatsReportUi(
            totals = totals,
            spentChangePercent = change,
            previousLeftOver = if (!comparable || previousTotals == MoneyTotals()) null else previousTotals.leftOver,
            cycle = (period as? StatsPeriod.Cycle)?.let { cycleView(it, input, money, inPeriod) },
            pace = pace(timeline, previousTimeline, today, period).let { if (comparable) it else it.copy(previousAtSamePoint = null) },
            categories = categoryRows(period, money, ::within),
            timeline = timeline,
            days = if (period.days <= CALENDAR_MAX_DAYS) dayCells(timeline, today) else emptyList(),
            trend = trend(period, today) { p -> money.totals(within(p.start, p.end)) },
            paidFrom = money.paidFrom(inPeriod),
            biggest = inPeriod.filter { money.isSpending(it) }.sortedByDescending { it.amount }.take(5),
            hasTransactions = inPeriod.isNotEmpty()
        )
    }

    /** The trend, total and transactions of a set of Categories (one Category, or a Bucket's). */
    fun categoryDetail(input: StatsInput, title: String, categories: Set<String>): CategoryDetail {
        val keys = categories.mapTo(mutableSetOf()) { it.key() }
        val mine = input.records.filter { r ->
            r.category.key() in keys && r.type.trim().equals("Expense", ignoreCase = true)
        }
        val dated = mine.mapNotNull { r -> parseFlexibleDate(r.date)?.let { it to r } }
        fun spentIn(p: StatsPeriod) = dated.filter { (d, _) -> !d.isBefore(p.start) && !d.isAfter(p.end) }.sumOf { it.second.amount }

        val period = input.period
        val trend = trend(period, input.today) { p -> MoneyTotals(spent = spentIn(p)) }
        val spent = spentIn(period)
        // "Usual" means the same here as on the breakdown row: the average of the three periods before.
        val before = trend.dropLast(1).takeLast(3).takeIf { h -> h.any { it.spent > 0.0 } }.orEmpty()
        val elapsedDays = (ChronoUnit.DAYS.between(period.start, minOf(input.today, period.end)) + 1).coerceAtLeast(1)
        return CategoryDetail(
            title = title,
            spent = spent,
            usual = before.takeIf { it.isNotEmpty() }?.map { it.spent }?.average(),
            perDay = spent / elapsedDays,
            trend = trend,
            transactions = dated
                .filter { (d, _) -> !d.isBefore(period.start) && !d.isAfter(period.end) }
                .sortedWith(compareByDescending<Pair<LocalDate, ExpenseRecord>> { it.first }.thenByDescending { it.second.amount })
                .map { it.second }
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

    /** Every period has one before it, except that a first Salary cycle has no earlier cycle to compare with. */
    private fun hasRealPrevious(period: StatsPeriod) = period !is StatsPeriod.Cycle || period.hasPrevious

    /** Days of the period up to today; the whole period once it is over. */
    private fun daysIn(period: StatsPeriod, today: LocalDate): Long =
        if (!today.isBefore(period.end)) period.days.toLong() else ChronoUnit.DAYS.between(period.start, today) + 1

    private fun cycleView(period: StatsPeriod.Cycle, input: StatsInput, money: Money, inPeriod: List<ExpenseRecord>): CycleView? {
        val cycle = input.cycles.firstOrNull { it.id == period.span.id } ?: return null
        val summary = CycleSummaryBuilder.build(cycle, input.buckets, input.assignments, input.records, input.today)
        val saved = inPeriod.filter { it.type.trim().equals("Expense", ignoreCase = true) && money.roles.isSaving(it.category) }.sumOf { it.amount }
        return CycleView(summary, period.span.isRunning, saved)
    }

    private fun pace(timeline: List<TimelinePoint>, previous: List<TimelinePoint>, today: LocalDate, period: StatsPeriod): Pace {
        val elapsed = timeline.count { !it.start.isAfter(today) }
        val current = timeline.take(elapsed).map { it.spent }.runningSum()
        val prev = previous.map { it.spent }.runningSum()
        val firstCovered = timeline.indexOfFirst { it.budget != null }
        val budget: List<Double?> = if (firstCovered < 0) {
            emptyList()
        } else {
            val running = timeline.drop(firstCovered).map { it.budget ?: 0.0 }.runningSum()
            List(firstCovered) { null } + running
        }
        val running = !today.isBefore(period.start) && today.isBefore(period.end)
        val projection = if (running && period.resolution == StatsResolution.DAY && elapsed >= 3) {
            // Leave out the biggest day (usually rent) so one bill doesn't set the pace for the rest.
            val days = timeline.take(elapsed).map { it.spent }
            val typical = (days.sum() - days.max()) / (days.size - 1)
            current.last() + typical * (timeline.size - elapsed)
        } else {
            null
        }
        return Pace(
            labels = timeline.map { it.label },
            current = current,
            previous = prev,
            budget = budget,
            projection = projection,
            previousAtSamePoint = if (elapsed in 1..prev.size) prev[elapsed - 1] else prev.lastOrNull()
        )
    }

    private fun categoryRows(
        period: StatsPeriod,
        money: Money,
        within: (LocalDate, LocalDate) -> List<ExpenseRecord>
    ): List<CategoryRow> {
        fun byCategory(p: StatsPeriod) = within(p.start, p.end)
            .filter { money.isSpending(it) }
            .groupBy { it.categoryName() }
            .mapValues { (_, items) -> items.sumOf { it.amount } }
        val now = byCategory(period)
        val earlier = generateSequence(period.previous()) { it.previous() }.take(3).map(::byCategory).toList()
        return now.map { (name, amount) ->
            val history = earlier.map { it[name] ?: 0.0 }
            CategoryRow(name, amount, history.takeIf { h -> h.any { it > 0.0 } }?.average())
        }.sortedByDescending { it.amount }
    }

    /** Five shades by quintile of the period's spending days, so one big bill doesn't wash the rest out. */
    private fun dayCells(timeline: List<TimelinePoint>, today: LocalDate): List<DayCell> {
        val positives = timeline.filter { !it.start.isAfter(today) && it.spent > 0.0 }.map { it.spent }.sorted()
        fun step(v: Double): Int {
            if (v <= 0.0 || positives.isEmpty()) return 0
            val rank = positives.count { it <= v }.toDouble() / positives.size
            return (rank * 5).toInt().coerceIn(1, 5)
        }
        return timeline.map { DayCell(it.start, it.spent, it.start.isAfter(today), if (it.start.isAfter(today)) 0 else step(it.spent)) }
    }

    /** The period on screen and the ones before it; a year shows its own months. */
    private fun trend(period: StatsPeriod, today: LocalDate, totalsOf: (StatsPeriod) -> MoneyTotals): List<TrendBar> {
        val periods: List<Pair<String, StatsPeriod>> = when (period) {
            is StatsPeriod.Year -> (1..12).map { m ->
                val month = YearMonth.of(period.year, m)
                month.format(MONTH_SHORT) to StatsPeriod.Custom(month.atDay(1), month.atEndOfMonth())
            }
            is StatsPeriod.Custom -> return emptyList()
            // Only real cycles: past the first one, stepping back gives made-up ranges.
            else -> generateSequence<StatsPeriod>(period) { p -> if (hasRealPrevious(p)) p.previous() else null }
                .take(TREND_LENGTH)
                .toList()
                .reversed()
                .map { trendLabel(it) to it }
        }
        return periods.map { (label, p) ->
            val t = totalsOf(p)
            val current = !today.isBefore(p.start) && !today.isAfter(p.end)
            TrendBar(label, t.earned, t.spent, t.saved, current || p == period)
        }
    }

    private fun trendLabel(p: StatsPeriod): String = when (p) {
        is StatsPeriod.Month -> p.month.format(MONTH_SHORT)
        is StatsPeriod.Week -> p.start.format(DAY_MONTH)
        is StatsPeriod.Year -> p.year.toString()
        else -> p.start.format(DAY_MONTH)
    }

    private fun timelinePoints(period: StatsPeriod): List<Triple<String, LocalDate, LocalDate>> {
        val spansMonths = YearMonth.from(period.start) != YearMonth.from(period.end)
        val dayLabel = DateTimeFormatter.ofPattern(if (spansMonths) "d MMM" else "d", Locale.ENGLISH)
        return when (period.resolution) {
            StatsResolution.DAY -> generateSequence(period.start) { it.plusDays(1) }
                .takeWhile { !it.isAfter(period.end) }
                .map { Triple(it.format(dayLabel), it, it) }
                .toList()

            StatsResolution.WEEK -> generateSequence(period.start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) { it.plusWeeks(1) }
                .takeWhile { !it.isAfter(period.end) }
                .map { monday ->
                    val start = maxOf(monday, period.start)
                    Triple(start.format(DAY_MONTH), start, minOf(monday.plusDays(6), period.end))
                }
                .toList()

            StatsResolution.MONTH -> {
                val fmt = if (period.start.year == period.end.year) MONTH_SHORT else MONTH_YEAR
                generateSequence(YearMonth.from(period.start)) { it.plusMonths(1) }
                    .takeWhile { !it.atDay(1).isAfter(period.end) }
                    .map { m -> Triple(m.format(fmt), maxOf(m.atDay(1), period.start), minOf(m.atEndOfMonth(), period.end)) }
                    .toList()
            }
        }
    }

    private fun groupLookup(accounts: List<AccountRecord>): (Long?, String?) -> String? {
        val byId = accounts.associate { it.id to it.groupName }
        val byName = accounts.associate { it.accountName.key() to it.groupName }
        return { id, name -> id?.let(byId::get) ?: name?.let { byName[it.key()] } }
    }

    /**
     * The money rules of ADR-0004. With [budgetBasis] (Cycle view) every Expense is spending and
     * Refunds are left alone, matching the Budget tab.
     */
    private class Money(val roles: StatsRoles, val groupOf: (Long?, String?) -> String?, val budgetBasis: Boolean) {

        fun isSpending(r: ExpenseRecord) = r.kind() == Kind.EXPENSE && (budgetBasis || !roles.isSaving(r.category))

        fun totals(records: List<ExpenseRecord>): MoneyTotals {
            var earned = 0.0
            var grossSpent = 0.0
            var saved = 0.0
            var refunds = 0.0
            records.forEach { r ->
                when (r.kind()) {
                    Kind.EXPENSE -> when {
                        !roles.isSaving(r.category) -> grossSpent += r.amount
                        budgetBasis -> { grossSpent += r.amount; saved += r.amount }
                        else -> saved += r.amount
                    }
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
            val spent = if (budgetBasis) grossSpent else grossSpent - refunds
            return MoneyTotals(earned = earned, spent = spent, saved = saved, refunds = refunds)
        }

        fun paidFrom(records: List<ExpenseRecord>): PaidFrom {
            val spending = records.filter(::isSpending)
            val (card, other) = spending.partition { groupOf(it.accountId, it.accountName)?.contains("card", ignoreCase = true) == true }
            return PaidFrom(
                card = card.sumOf { it.amount },
                cashAndAccounts = other.sumOf { it.amount },
                transfers = records.filter { it.kind() == Kind.TRANSFER }.sumOf { it.amount }
            )
        }
    }

    /** Each day's share of the Salary cycle covering it: spendable amount ÷ the cycle's planned days. */
    private class DailyAllowance(cycles: List<BudgetCycle>, today: LocalDate) {
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

    private fun List<Double>.runningSum(): List<Double> = runningReduce { acc, v -> acc + v }

    private val MONTH_SHORT = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH)
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
}

private fun String.key(): String = trim().lowercase()
