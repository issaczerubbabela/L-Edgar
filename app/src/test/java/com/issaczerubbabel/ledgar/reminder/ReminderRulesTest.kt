package com.issaczerubbabel.ledgar.reminder

import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
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

    @Test
    fun `message shows Left to spend while a cycle has money left`() {
        val text = ReminderRules.message(todayEpochDay = 100L, leftToSpend = 12_500.0)
        assertTrue(text, text.startsWith("₹12,500 left to spend this cycle"))
    }

    @Test
    fun `message falls back to a rotating line with no cycle or nothing left`() {
        val plain = ReminderRules.message(todayEpochDay = 100L, leftToSpend = null)
        assertEquals(plain, ReminderRules.message(todayEpochDay = 100L, leftToSpend = -40.0))
        assertEquals(plain, ReminderRules.message(todayEpochDay = 100L, leftToSpend = 0.0))
        assertFalse(plain.contains("left to spend this cycle"))
        // A different day picks a different line, and negative epoch days don't crash.
        assertTrue(plain != ReminderRules.message(todayEpochDay = 101L, leftToSpend = null))
        ReminderRules.message(todayEpochDay = -3L, leftToSpend = null)
    }

    private fun capture(amount: Double, direction: String = "DEBIT") = CapturedTransaction(
        sources = "NOTIF", sender = "HDFC Bank", rawText = "x", rawHash = "h", capturedAt = 0, txnTime = 0,
        amount = amount, direction = direction, channel = "UPI"
    )

    @Test
    fun `message with captures waiting gives the count and the total of their debits`() {
        val waiting = WaitingCaptures(count = 6, debitTotal = 1_240.0)

        assertEquals("6 waiting to review · ₹1,240.", ReminderRules.message(todayEpochDay = 100L, leftToSpend = null, waiting = waiting))
    }

    @Test
    fun `message with one capture waiting reads the same way`() {
        val waiting = WaitingCaptures(count = 1, debitTotal = 212.4)

        assertEquals("1 waiting to review · ₹212.", ReminderRules.message(todayEpochDay = 100L, leftToSpend = null, waiting = waiting))
    }

    @Test
    fun `message with captures waiting adds Left to spend while a cycle has money left`() {
        val waiting = WaitingCaptures(count = 6, debitTotal = 1_240.0)

        assertEquals(
            "6 waiting to review · ₹1,240. ₹12,500 left to spend this cycle.",
            ReminderRules.message(todayEpochDay = 100L, leftToSpend = 12_500.0, waiting = waiting)
        )
        assertEquals(
            "6 waiting to review · ₹1,240.",
            ReminderRules.message(todayEpochDay = 100L, leftToSpend = -40.0, waiting = waiting)
        )
    }

    @Test
    fun `message with only money received waiting leaves out the total`() {
        val waiting = WaitingCaptures(count = 2, debitTotal = 0.0)

        assertEquals("2 waiting to review.", ReminderRules.message(todayEpochDay = 100L, leftToSpend = null, waiting = waiting))
    }

    @Test
    fun `message with nothing waiting is today's text, as before`() {
        assertEquals(
            ReminderRules.message(todayEpochDay = 100L, leftToSpend = null),
            ReminderRules.message(todayEpochDay = 100L, leftToSpend = null, waiting = WaitingCaptures.NONE)
        )
        assertTrue(
            ReminderRules.message(todayEpochDay = 100L, leftToSpend = 12_500.0, waiting = WaitingCaptures(0, 0.0))
                .startsWith("₹12,500 left to spend this cycle")
        )
    }

    @Test
    fun `waiting captures count every capture but total only the debits`() {
        val waiting = WaitingCaptures.of(listOf(capture(450.0), capture(790.0), capture(5_000.0, direction = "CREDIT")))

        assertEquals(3, waiting.count)
        assertEquals(1_240.0, waiting.debitTotal, 0.0)
        assertTrue(waiting.any)
        assertFalse(WaitingCaptures.of(emptyList()).any)
    }
}
