package com.issaczerubbabel.ledgar.data.preferences

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ReminderPreferencesTest {

    private val preferences = ReminderPreferences(ApplicationProvider.getApplicationContext())

    @Test
    fun `defaults are off, 9pm, and nothing logged`() = runBlocking {
        assertFalse(preferences.reminderEnabled.first())
        assertEquals(DEFAULT_REMINDER_MINUTE_OF_DAY, preferences.reminderMinuteOfDay.first())
        assertNull(preferences.lastLoggedEpochDay.first())
    }

    @Test
    fun `setReminderEnabled and setReminderMinuteOfDay persist`() = runBlocking {
        preferences.setReminderEnabled(true)
        preferences.setReminderMinuteOfDay(8 * 60)

        assertTrue(preferences.reminderEnabled.first())
        assertEquals(8 * 60, preferences.reminderMinuteOfDay.first())
    }

    @Test
    fun `setReminderMinuteOfDay clamps to a valid minute of day`() = runBlocking {
        preferences.setReminderMinuteOfDay(-5)
        assertEquals(0, preferences.reminderMinuteOfDay.first())

        preferences.setReminderMinuteOfDay(10_000)
        assertEquals(23 * 60 + 59, preferences.reminderMinuteOfDay.first())
    }

    @Test
    fun `markLoggedToday records the day and hasLoggedToday reflects it`() = runBlocking {
        assertFalse(preferences.hasLoggedToday(100L))

        preferences.markLoggedToday(100L)

        assertTrue(preferences.hasLoggedToday(100L))
        assertFalse(preferences.hasLoggedToday(101L))
    }
}
