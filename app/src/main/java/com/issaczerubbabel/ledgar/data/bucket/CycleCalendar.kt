package com.issaczerubbabel.ledgar.data.bucket

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Date arithmetic for a salary cycle. Pure, so it is tested without a database or a clock. */
object CycleCalendar {

    /** Length of the cycle in days, counting both the start and the end day. */
    fun totalDays(start: LocalDate, end: LocalDate): Int =
        (ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(1)

    /**
     * Which day of the cycle [today] is, 1-based: 0 before the cycle starts, and never more than
     * [totalDays] even when the cycle is overdue.
     */
    fun dayNumber(start: LocalDate, end: LocalDate, today: LocalDate): Int =
        (ChronoUnit.DAYS.between(start, today) + 1).toInt().coerceIn(0, totalDays(start, end))

    /** Whole days until the cycle begins; zero once it has started. */
    fun daysUntilStart(start: LocalDate, today: LocalDate): Int =
        ChronoUnit.DAYS.between(today, start).toInt().coerceAtLeast(0)

    /** Days remaining after today, so the last day reads as 0 left. Never negative. */
    fun daysLeft(end: LocalDate, today: LocalDate): Int =
        ChronoUnit.DAYS.between(today, end).toInt().coerceAtLeast(0)

    /**
     * How many days past its end date a cycle that is still running is. Zero for a closed cycle,
     * or one that has not reached its end yet.
     */
    fun daysOverdue(end: LocalDate, today: LocalDate, isClosed: Boolean): Int =
        if (isClosed) 0 else ChronoUnit.DAYS.between(end, today).toInt().coerceAtLeast(0)

    /** How far through the cycle today is, 0..1. This is where the pace tick sits on every bar. */
    fun elapsedFraction(start: LocalDate, end: LocalDate, today: LocalDate): Float =
        dayNumber(start, end, today).toFloat() / totalDays(start, end)

    /** A sensible default for "runs until": one calendar month on, less a day (26 Sep -> 25 Oct). */
    fun defaultEnd(start: LocalDate): LocalDate = start.plusMonths(1).minusDays(1)
}
