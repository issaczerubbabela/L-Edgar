package com.issaczerubbabel.ledgar.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecurrenceCalculatorTest {

    @Test
    fun weeklyStepsByWholeWeeks() {
        val monday = LocalDate.of(2026, 9, 7)
        assertEquals(LocalDate.of(2026, 9, 14), RecurrenceCalculator.nextAfter(monday, RecurrenceFrequency.WEEKLY, 1, monday.dayOfWeek.value))
        assertEquals(LocalDate.of(2026, 9, 21), RecurrenceCalculator.nextAfter(monday, RecurrenceFrequency.WEEKLY, 2, monday.dayOfWeek.value))
    }

    @Test
    fun monthlyAnchorOnThe31stClampsThroughFebruaryAndBack() {
        val jan31 = LocalDate.of(2026, 1, 31)
        val feb28 = RecurrenceCalculator.nextAfter(jan31, RecurrenceFrequency.MONTHLY, 1, 31)
        val mar31 = RecurrenceCalculator.nextAfter(feb28, RecurrenceFrequency.MONTHLY, 1, 31)
        val apr30 = RecurrenceCalculator.nextAfter(mar31, RecurrenceFrequency.MONTHLY, 1, 31)

        assertEquals(LocalDate.of(2026, 2, 28), feb28)
        assertEquals(LocalDate.of(2026, 3, 31), mar31)
        assertEquals(LocalDate.of(2026, 4, 30), apr30)
    }

    @Test
    fun monthlyAnchorOnThe31stLandsOnFeb29InALeapYear() {
        val jan31 = LocalDate.of(2028, 1, 31)
        assertEquals(LocalDate.of(2028, 2, 29), RecurrenceCalculator.nextAfter(jan31, RecurrenceFrequency.MONTHLY, 1, 31))
    }

    @Test
    fun quarterlyStepsThreeMonthsAtATime() {
        val jan15 = LocalDate.of(2026, 1, 15)
        assertEquals(LocalDate.of(2026, 4, 15), RecurrenceCalculator.nextAfter(jan15, RecurrenceFrequency.MONTHLY, 3, 15))
    }

    @Test
    fun yearlyKeepsTheSameMonthAndDay() {
        val date = LocalDate.of(2026, 9, 26)
        assertEquals(LocalDate.of(2027, 9, 26), RecurrenceCalculator.nextAfter(date, RecurrenceFrequency.YEARLY, 1, 26))
    }

    @Test
    fun yearlyOnLeapDayClampsInNonLeapYears() {
        val leapDay = LocalDate.of(2028, 2, 29)
        assertEquals(LocalDate.of(2029, 2, 28), RecurrenceCalculator.nextAfter(leapDay, RecurrenceFrequency.YEARLY, 1, 29))
    }

    @Test
    fun catchingUpAfterThreeMissedMonthsCreatesExactlyThree() {
        val due = RecurrenceCalculator.dueOccurrences(
            nextDate = LocalDate.of(2026, 7, 1),
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            anchorDay = 1,
            today = LocalDate.of(2026, 9, 1)
        )

        assertEquals(
            listOf(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1)),
            due
        )
    }

    @Test
    fun aRuleNotYetDueProducesNoOccurrences() {
        val due = RecurrenceCalculator.dueOccurrences(
            nextDate = LocalDate.of(2026, 10, 1),
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            anchorDay = 1,
            today = LocalDate.of(2026, 9, 1)
        )

        assertEquals(emptyList<LocalDate>(), due)
    }

    @Test
    fun theCapStopsALongAbandonedRuleFromFloodingTheLedger() {
        val due = RecurrenceCalculator.dueOccurrences(
            nextDate = LocalDate.of(2000, 1, 1),
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 1,
            anchorDay = 6,
            today = LocalDate.of(2026, 9, 1),
            maxOccurrences = 24
        )

        assertEquals(24, due.size)
    }

    @Test
    fun anEndDateStopsFurtherOccurrences() {
        val due = RecurrenceCalculator.dueOccurrences(
            nextDate = LocalDate.of(2026, 1, 1),
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            anchorDay = 1,
            today = LocalDate.of(2026, 6, 1),
            endDate = LocalDate.of(2026, 3, 1)
        )

        assertEquals(
            listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)),
            due
        )
    }

    @Test
    fun aRemainingCountStopsFurtherOccurrences() {
        val due = RecurrenceCalculator.dueOccurrences(
            nextDate = LocalDate.of(2026, 1, 1),
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            anchorDay = 1,
            today = LocalDate.of(2026, 12, 1),
            remainingCount = 2
        )

        assertEquals(listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)), due)
    }

    @Test
    fun labelsReadableCadences() {
        assertEquals("Weekly", RecurrenceCalculator.label(RecurrenceFrequency.WEEKLY, 1))
        assertEquals("Every 2 weeks", RecurrenceCalculator.label(RecurrenceFrequency.WEEKLY, 2))
        assertEquals("Monthly", RecurrenceCalculator.label(RecurrenceFrequency.MONTHLY, 1))
        assertEquals("Every 3 months", RecurrenceCalculator.label(RecurrenceFrequency.MONTHLY, 3))
        assertEquals("Yearly", RecurrenceCalculator.label(RecurrenceFrequency.YEARLY, 1))
    }
}
