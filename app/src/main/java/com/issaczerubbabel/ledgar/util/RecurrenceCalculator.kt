package com.issaczerubbabel.ledgar.util

import java.time.LocalDate
import java.time.YearMonth

/** The cadences a `RecurringRule` can repeat on. */
object RecurrenceFrequency {
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
    const val YEARLY = "YEARLY"
}

/**
 * Pure date arithmetic for recurring transactions: no Android types, so it is unit tested directly,
 * like [com.issaczerubbabel.ledgar.data.local.migration.BucketBudgetMigration.plan].
 *
 * A monthly or yearly rule keeps its anchor day of month, clamped to whatever the target month's
 * length is: the 31st gives Feb 28 (or Feb 29 in a leap year), then goes back to the 31st in March,
 * because each step clamps against the month the *previous* occurrence actually landed on.
 */
object RecurrenceCalculator {

    /** The occurrence [interval] units of [frequency] after [date], keeping the [anchorDay] anchor. */
    fun nextAfter(date: LocalDate, frequency: String, interval: Int, anchorDay: Int): LocalDate {
        val step = interval.coerceAtLeast(1).toLong()
        return when (frequency) {
            RecurrenceFrequency.WEEKLY -> date.plusWeeks(step)
            RecurrenceFrequency.MONTHLY -> clampToAnchor(YearMonth.from(date).plusMonths(step), anchorDay)
            RecurrenceFrequency.YEARLY -> clampToAnchor(YearMonth.from(date).plusMonths(step * 12), anchorDay)
            else -> date
        }
    }

    /**
     * Every occurrence from [nextDate] up to and including [today], respecting [endDate] and
     * [remainingCount], capped at [maxOccurrences] so a rule left untouched for years cannot flood
     * the ledger in one run (catch-up after 3 missed months creates 3 Transactions, not 1 and not 6).
     */
    fun dueOccurrences(
        nextDate: LocalDate,
        frequency: String,
        interval: Int,
        anchorDay: Int,
        today: LocalDate,
        endDate: LocalDate? = null,
        remainingCount: Int? = null,
        maxOccurrences: Int = 24
    ): List<LocalDate> {
        val due = mutableListOf<LocalDate>()
        var cursor = nextDate
        var remaining = remainingCount
        while (!cursor.isAfter(today) &&
            (endDate == null || !cursor.isAfter(endDate)) &&
            (remaining == null || remaining > 0) &&
            due.size < maxOccurrences
        ) {
            due += cursor
            remaining = remaining?.minus(1)
            cursor = nextAfter(cursor, frequency, interval, anchorDay)
        }
        return due
    }

    /** A short label for a chip or list row, e.g. "Every 3 months". */
    fun label(frequency: String, interval: Int): String = when (frequency) {
        RecurrenceFrequency.WEEKLY -> if (interval <= 1) "Weekly" else "Every $interval weeks"
        RecurrenceFrequency.MONTHLY -> if (interval <= 1) "Monthly" else "Every $interval months"
        RecurrenceFrequency.YEARLY -> "Yearly"
        else -> "Repeat"
    }

    private fun clampToAnchor(month: YearMonth, anchorDay: Int): LocalDate =
        month.atDay(anchorDay.coerceIn(1, month.lengthOfMonth()))
}
