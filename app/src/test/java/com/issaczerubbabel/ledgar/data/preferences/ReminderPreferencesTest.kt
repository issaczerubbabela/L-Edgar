package com.issaczerubbabel.ledgar.data.preferences

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [androidx.datastore.preferences.preferencesDataStore] caches one [androidx.datastore.core.DataStore]
 * per process for the file name it's given — by design, since a real app has a single Application.
 * In this JVM test suite that means every test method shares the same store across the whole run
 * (JUnit gives no ordering guarantee), so each test resets what it depends on rather than assuming
 * a pristine store, and nothing here asserts an absolute "never logged" state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ReminderPreferencesTest {

    private val preferences = ReminderPreferences(ApplicationProvider.getApplicationContext())

    @Before
    fun resetToDefaults() = runBlocking {
        preferences.setReminderEnabled(false)
        preferences.setReminderMinuteOfDay(DEFAULT_REMINDER_MINUTE_OF_DAY)
    }

    @Test
    fun `defaults are off and 9pm`() = runBlocking {
        assertFalse(preferences.reminderEnabled.first())
        assertEquals(DEFAULT_REMINDER_MINUTE_OF_DAY, preferences.reminderMinuteOfDay.first())
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
        // Distinct, improbable-to-collide days rather than a fixed literal, so this stays correct
        // no matter what another test method in this shared store marked before or after it.
        val markedDay = 1_000_100L
        val otherDay = 1_000_101L

        preferences.markLoggedToday(markedDay)

        assertTrue(preferences.hasLoggedToday(markedDay))
        assertFalse(preferences.hasLoggedToday(otherDay))
    }
}
