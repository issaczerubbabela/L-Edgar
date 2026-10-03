package com.issaczerubbabel.ledgar.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderRulesTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val ninePm = 21 * 60

    @Test
    fun `before today's time schedules today`() {
        val now = ZonedDateTime.of(2026, 3, 10, 18, 0, 0, 0, kolkata).toInstant()

        val next = ReminderRules.nextReminderAt(now, ninePm, kolkata)

        val expected = ZonedDateTime.of(2026, 3, 10, 21, 0, 0, 0, kolkata).toInstant()
        assertEquals(expected, next)
    }

    @Test
    fun `after today's time schedules tomorrow`() {
        val now = ZonedDateTime.of(2026, 3, 10, 22, 0, 0, 0, kolkata).toInstant()

        val next = ReminderRules.nextReminderAt(now, ninePm, kolkata)

        val expected = ZonedDateTime.of(2026, 3, 11, 21, 0, 0, 0, kolkata).toInstant()
        assertEquals(expected, next)
    }

    @Test
    fun `exactly at today's time schedules tomorrow, not an immediate repeat`() {
        val now = ZonedDateTime.of(2026, 3, 10, 21, 0, 0, 0, kolkata).toInstant()

        val next = ReminderRules.nextReminderAt(now, ninePm, kolkata)

        val expected = ZonedDateTime.of(2026, 3, 11, 21, 0, 0, 0, kolkata).toInstant()
        assertEquals(expected, next)
    }

    @Test
    fun `DST spring-forward day lands on the chosen wall-clock time, not a shifted instant`() {
        // America/New_York springs forward on 2026-03-08: 02:00 local becomes 03:00 local.
        val newYork = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 3, 8, 0, 30, 0, 0, newYork).toInstant()

        val next = ReminderRules.nextReminderAt(now, ninePm, newYork)

        val wallClock = ZonedDateTime.ofInstant(next, newYork)
        assertEquals(8, wallClock.dayOfMonth)
        assertEquals(21, wallClock.hour)
        assertEquals(0, wallClock.minute)
    }

    @Test
    fun `DST fall-back day still lands on the chosen wall-clock time`() {
        // America/New_York falls back on 2026-11-01: 02:00 local becomes 01:00 local.
        val newYork = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 11, 1, 0, 30, 0, 0, newYork).toInstant()

        val next = ReminderRules.nextReminderAt(now, ninePm, newYork)

        val wallClock = ZonedDateTime.ofInstant(next, newYork)
        assertEquals(1, wallClock.dayOfMonth)
        assertEquals(21, wallClock.hour)
        assertEquals(0, wallClock.minute)
    }

    @Test
    fun `shouldRemind is false once today's epoch day was logged`() {
        assertFalse(ReminderRules.shouldRemind(todayEpochDay = 100L, lastLoggedEpochDay = 100L))
    }

    @Test
    fun `shouldRemind is true when nothing was logged today`() {
        assertTrue(ReminderRules.shouldRemind(todayEpochDay = 100L, lastLoggedEpochDay = 99L))
        assertTrue(ReminderRules.shouldRemind(todayEpochDay = 100L, lastLoggedEpochDay = null))
    }
}
