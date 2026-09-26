package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * A Salary cycle's dates as Stats sees them. A running cycle stays open past its end date until the
 * next one starts, so it reaches today when today is later.
 */
data class CycleSpan(val id: Long, val start: LocalDate, val end: LocalDate, val isRunning: Boolean) {
    companion object {
        fun from(cycles: List<BudgetCycle>, today: LocalDate): List<CycleSpan> =
            cycles.mapNotNull { c ->
                val start = parseFlexibleDate(c.startDate) ?: return@mapNotNull null
                val end = parseFlexibleDate(c.endDate) ?: return@mapNotNull null
                val running = c.closedAt == null
                CycleSpan(c.id, start, if (running) maxOf(end, today) else end, running)
            }.sortedBy { it.start }
    }
}

/** How finely a period's timeline is split. */
enum class StatsResolution { DAY, WEEK, MONTH }

/**
 * A span of time the Stats tab reports on. It owns what "the period before this one" means, so every
 * comparison and every arrow button agrees on it.
 */
sealed interface StatsPeriod {
    val start: LocalDate
    val end: LocalDate
    val resolution: StatsResolution

    fun previous(): StatsPeriod
    fun next(): StatsPeriod

    val range: StatsDateRange get() = StatsDateRange(start, end)
    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    /** Monday to Sunday. */
    data class Week(val monday: LocalDate) : StatsPeriod {
        override val start get() = monday
        override val end: LocalDate get() = monday.plusDays(6)
        override val resolution get() = StatsResolution.DAY
        override fun previous() = Week(monday.minusWeeks(1))
        override fun next() = Week(monday.plusWeeks(1))
    }

    data class Month(val month: YearMonth) : StatsPeriod {
        override val start: LocalDate get() = month.atDay(1)
        override val end: LocalDate get() = month.atEndOfMonth()
        override val resolution get() = StatsResolution.DAY
        override fun previous() = Month(month.minusMonths(1))
        override fun next() = Month(month.plusMonths(1))
    }

    data class Year(val year: Int) : StatsPeriod {
        override val start: LocalDate get() = LocalDate.of(year, 1, 1)
        override val end: LocalDate get() = LocalDate.of(year, 12, 31)
        override val resolution get() = StatsResolution.MONTH
        override fun previous() = Year(year - 1)
        override fun next() = Year(year + 1)
    }

    /** Any range. The periods before and after it are the same length and touch it. */
    data class Custom(override val start: LocalDate, override val end: LocalDate) : StatsPeriod {
        init {
            require(!end.isBefore(start)) { "A custom period cannot end ($end) before it starts ($start)" }
        }

        override val resolution: StatsResolution
            get() = when {
                days <= 31 -> StatsResolution.DAY
                days <= 26 * 7 -> StatsResolution.WEEK
                else -> StatsResolution.MONTH
            }

        override fun previous() = Custom(start.minusDays(days.toLong()), start.minusDays(1))
        override fun next() = Custom(end.plusDays(1), end.plusDays(days.toLong()))
    }

    /**
     * One Salary cycle out of all of them, oldest first. Before the first cycle and after the last,
     * stepping falls back to a same-length [Custom] range, so comparisons still have something to use.
     */
    data class Cycle(val spans: List<CycleSpan>, val index: Int) : StatsPeriod {
        init {
            require(index in spans.indices) { "No cycle at $index" }
        }

        val span: CycleSpan get() = spans[index]
        override val start: LocalDate get() = span.start
        override val end: LocalDate get() = span.end
        override val resolution get() = StatsResolution.DAY
        val hasPrevious: Boolean get() = index > 0
        val hasNext: Boolean get() = index < spans.lastIndex

        override fun previous(): StatsPeriod = if (hasPrevious) Cycle(spans, index - 1) else Custom(start, end).previous()
        override fun next(): StatsPeriod = if (hasNext) Cycle(spans, index + 1) else Custom(start, end).next()
    }

    companion object {
        /**
         * The cycle containing [date], or the nearest one before it, or null when there are no cycles.
         * [today] decides how far the running cycle reaches (see [CycleSpan.from]).
         */
        fun cycleOn(date: LocalDate, cycles: List<BudgetCycle>, today: LocalDate): Cycle? {
            val spans = CycleSpan.from(cycles, today)
            if (spans.isEmpty()) return null
            val index = spans.indexOfLast { !it.start.isAfter(date) }.coerceAtLeast(0)
            return Cycle(spans, index)
        }

        /** The period of [scope] that contains [date]; a custom scope uses [customStart]..[customEnd]. */
        fun of(scope: StatsScope, date: LocalDate, customStart: LocalDate? = null, customEnd: LocalDate? = null): StatsPeriod =
            when (scope) {
                StatsScope.CYCLE -> error("A cycle period needs the cycles; use cycleOn")
                StatsScope.WEEKLY -> Week(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                StatsScope.MONTHLY -> Month(YearMonth.from(date))
                StatsScope.YEARLY -> Year(date.year)
                StatsScope.SELECT_PERIOD -> {
                    val a = customStart ?: date
                    val b = customEnd ?: date
                    if (a <= b) Custom(a, b) else Custom(b, a)
                }
            }
    }
}
