package com.issaczerubbabel.ledgar.data.bucket

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CycleCalendarTest {

    private val start = LocalDate.of(2026, 8, 26)
    private val end = LocalDate.of(2026, 9, 25)

    @Test
    fun countsBothEndsOfTheCycle() {
        assertEquals(31, CycleCalendar.totalDays(start, end))
        assertEquals(1, CycleCalendar.totalDays(start, start))
    }

    @Test
    fun matchesTheMockupsMidCycleExample() {
        // "Day 19 of 31 · 12 days left · 61% elapsed" on 13 September.
        val today = LocalDate.of(2026, 9, 13)

        assertEquals(19, CycleCalendar.dayNumber(start, end, today))
        assertEquals(12, CycleCalendar.daysLeft(end, today))
        assertEquals(19f / 31f, CycleCalendar.elapsedFraction(start, end, today), 0.0001f)
    }

    @Test
    fun beforeTheCycleStartsNothingHasElapsed() {
        val today = start.minusDays(3)

        assertEquals(0, CycleCalendar.dayNumber(start, end, today))
        assertEquals(0f, CycleCalendar.elapsedFraction(start, end, today), 0f)
    }

    @Test
    fun onTheLastDayNothingIsLeftButItIsNotOverdue() {
        assertEquals(31, CycleCalendar.dayNumber(start, end, end))
        assertEquals(0, CycleCalendar.daysLeft(end, end))
        assertEquals(0, CycleCalendar.daysOverdue(end, end, isClosed = false))
        assertEquals(1f, CycleCalendar.elapsedFraction(start, end, end), 0f)
    }

    @Test
    fun anOverdueRunningCycleIsCappedAtFullAndCountsTheDaysLate() {
        val today = end.plusDays(3)

        assertEquals(31, CycleCalendar.dayNumber(start, end, today))
        assertEquals(0, CycleCalendar.daysLeft(end, today))
        assertEquals(3, CycleCalendar.daysOverdue(end, today, isClosed = false))
        assertEquals(1f, CycleCalendar.elapsedFraction(start, end, today), 0f)
    }

    @Test
    fun aClosedCycleIsNeverOverdue() {
        assertEquals(0, CycleCalendar.daysOverdue(end, end.plusDays(30), isClosed = true))
    }

    @Test
    fun defaultEndIsOneMonthOnLessADay() {
        assertEquals(LocalDate.of(2026, 10, 25), CycleCalendar.defaultEnd(LocalDate.of(2026, 9, 26)))
        // Month lengths clamp rather than throwing: 31 Jan + 1 month = 28 Feb, less a day.
        assertEquals(LocalDate.of(2026, 2, 27), CycleCalendar.defaultEnd(LocalDate.of(2026, 1, 31)))
    }
}
