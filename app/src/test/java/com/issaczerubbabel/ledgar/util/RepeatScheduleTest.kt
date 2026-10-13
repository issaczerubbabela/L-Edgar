package com.issaczerubbabel.ledgar.util

import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RepeatScheduleTest {

    private val today = LocalDate.of(2026, 10, 12)

    private fun rule(
        id: Long = 1,
        nextDate: String,
        frequency: String = RecurrenceFrequency.MONTHLY,
        anchorDay: Int = 1,
        autoAdd: Boolean = true,
        isPaused: Boolean = false,
        endDate: String? = null,
        remainingCount: Int? = null
    ) = RecurringRule(
        id = id, type = "Expense", category = "Rent", description = "Rent", amount = 15000.0, accountId = 1,
        remarks = "", frequency = frequency, anchorDay = anchorDay, startDate = "2026-01-01", nextDate = nextDate,
        endDate = endDate, remainingCount = remainingCount, autoAdd = autoAdd, isPaused = isPaused,
        createdAt = "2026-01-01"
    )

    @Test
    fun `an Ask me first rule with a due date waits, oldest first`() {
        val later = rule(id = 1, nextDate = "2026-10-10", autoAdd = false)
        val older = rule(id = 2, nextDate = "2026-09-01", autoAdd = false)
        val automatic = rule(id = 3, nextDate = "2026-10-01")
        val paused = rule(id = 4, nextDate = "2026-10-01", autoAdd = false, isPaused = true)
        val notYet = rule(id = 5, nextDate = "2026-10-20", autoAdd = false)

        val waiting = RepeatSchedule.waiting(listOf(later, older, automatic, paused, notYet), today)

        assertEquals(listOf(2L, 1L), waiting.map { it.id })
    }

    @Test
    fun `upcoming lists running rules due in the next 30 days`() {
        val soon = rule(id = 1, nextDate = "2026-10-20")
        val edge = rule(id = 2, nextDate = "2026-11-11")
        val tooFar = rule(id = 3, nextDate = "2026-11-12")
        val paused = rule(id = 4, nextDate = "2026-10-20", isPaused = true)
        val dueToday = rule(id = 5, nextDate = "2026-10-12")

        val upcoming = RepeatSchedule.upcoming(listOf(tooFar, edge, soon, paused, dueToday), today)

        assertEquals(listOf(1L, 2L), upcoming.map { it.id })
    }

    @Test
    fun `a rule used up by its count or end date is finished`() {
        assertTrue(RepeatSchedule.isFinished(rule(nextDate = "2026-11-01", remainingCount = 0)))
        assertTrue(RepeatSchedule.isFinished(rule(nextDate = "2026-11-01", endDate = "2026-10-31")))
        assertFalse(RepeatSchedule.isFinished(rule(nextDate = "2026-11-01", endDate = "2026-11-01", remainingCount = 1)))
    }

    @Test
    fun `resuming picks the first occurrence on or after today, keeping the anchor day`() {
        assertEquals(LocalDate.of(2026, 11, 1), RepeatSchedule.resumeDate(rule(nextDate = "2026-03-01"), today))
        assertEquals(
            LocalDate.of(2026, 10, 31),
            RepeatSchedule.resumeDate(rule(nextDate = "2026-01-31", anchorDay = 31), today)
        )
        assertEquals(LocalDate.of(2026, 10, 12), RepeatSchedule.resumeDate(rule(nextDate = "2026-10-12"), today))
        val weekly = rule(nextDate = "2026-09-07", frequency = RecurrenceFrequency.WEEKLY, anchorDay = 1)
        assertEquals(LocalDate.of(2026, 10, 12), RepeatSchedule.resumeDate(weekly, today))
    }

    @Test
    fun `the anchor is the weekday for weekly rules and the day of month otherwise`() {
        assertEquals(1, RepeatSchedule.anchorDayFor(RecurrenceFrequency.WEEKLY, today)) // a Monday
        assertEquals(12, RepeatSchedule.anchorDayFor(RecurrenceFrequency.MONTHLY, today))
    }

    @Test
    fun `the Ledger chip counts waiting repeats`() {
        assertNull(RepeatSchedule.waitingChipLabel(0))
        assertEquals("1 repeat waiting", RepeatSchedule.waitingChipLabel(1))
        assertEquals("3 repeats waiting", RepeatSchedule.waitingChipLabel(3))
    }
}
