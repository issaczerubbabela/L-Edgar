package com.issaczerubbabel.ledgar.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.reminderDataStore: DataStore<Preferences> by preferencesDataStore(name = "reminder_prefs")

/** 9:00 PM, as minutes since midnight. */
const val DEFAULT_REMINDER_MINUTE_OF_DAY = 21 * 60

/**
 * Device-local state for the daily "anything to log today?" reminder: whether it's on, what time
 * it fires, and the last day the user actually logged something. Never synced to the Sheet.
 */
@Singleton
class ReminderPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
    private val REMINDER_MINUTE_OF_DAY = intPreferencesKey("daily_reminder_minute_of_day")
    private val LAST_LOGGED_EPOCH_DAY = longPreferencesKey("last_logged_epoch_day")

    val reminderEnabled: Flow<Boolean> = context.reminderDataStore.data
        .map { prefs -> prefs[REMINDER_ENABLED] ?: false }

    val reminderMinuteOfDay: Flow<Int> = context.reminderDataStore.data
        .map { prefs -> prefs[REMINDER_MINUTE_OF_DAY] ?: DEFAULT_REMINDER_MINUTE_OF_DAY }

    val lastLoggedEpochDay: Flow<Long?> = context.reminderDataStore.data
        .map { prefs -> prefs[LAST_LOGGED_EPOCH_DAY] }

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.reminderDataStore.edit { prefs -> prefs[REMINDER_ENABLED] = enabled }
    }

    suspend fun setReminderMinuteOfDay(minuteOfDay: Int) {
        context.reminderDataStore.edit { prefs ->
            prefs[REMINDER_MINUTE_OF_DAY] = minuteOfDay.coerceIn(0, 23 * 60 + 59)
        }
    }

    /** Called when Log, Quick log or "Nothing today" happens — never by a Pull or an Import. */
    suspend fun markLoggedToday(epochDay: Long) {
        context.reminderDataStore.edit { prefs -> prefs[LAST_LOGGED_EPOCH_DAY] = epochDay }
    }

    suspend fun hasLoggedToday(epochDay: Long): Boolean = lastLoggedEpochDay.first() == epochDay
}
