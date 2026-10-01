package com.issaczerubbabel.ledgar.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.issaczerubbabel.ledgar.data.preferences.ReminderPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only place the daily reminder's [AlarmManager] alarm is set or cancelled. Kept separate from
 * [com.issaczerubbabel.ledgar.sync.SyncScheduler], which stays the only place *sync* work is
 * queued: this alarm only ever posts a local notification, never touches the network.
 *
 * Uses an inexact [AlarmManager.setWindow] rather than an exact alarm, since this reminder has no
 * need for `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` (denied by default on Android 14+) and a
 * periodic WorkManager job's run time can drift by hours, which isn't right for an evening nudge.
 */
@Singleton
class DailyReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: ReminderPreferences
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Reads the current preference and (re)schedules the next alarm, or cancels it when off. */
    suspend fun reschedule() {
        if (!preferences.reminderEnabled.first()) {
            cancel()
            return
        }
        val minuteOfDay = preferences.reminderMinuteOfDay.first()
        val next = ReminderRules.nextReminderAt(Instant.now(), minuteOfDay, ZoneId.systemDefault())
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            next.toEpochMilli(),
            WINDOW_LENGTH_MILLIS,
            pendingIntent()
        )
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, DailyReminderReceiver::class.java).setAction(ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val ACTION_FIRE = "com.issaczerubbabel.ledgar.reminder.FIRE"
        private const val REQUEST_CODE = 9001
        private const val WINDOW_LENGTH_MILLIS = 15 * 60 * 1000L
    }
}
