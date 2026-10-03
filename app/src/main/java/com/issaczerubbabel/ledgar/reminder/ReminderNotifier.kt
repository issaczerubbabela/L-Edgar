package com.issaczerubbabel.ledgar.reminder

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.issaczerubbabel.ledgar.QuickLogActivity
import com.issaczerubbabel.ledgar.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the evening "anything to log?" notification, on its own channel. Follows
 * [com.issaczerubbabel.ledgar.capture.notify.CaptureNotifier]'s pattern.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission") // areNotificationsEnabled() is checked first.
    fun notify(bodyText: String) {
        if (!manager.areNotificationsEnabled()) return

        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Daily reminder")
                .setDescription("A nudge in the evening if nothing was logged today")
                .build()
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_log_expense)
            .setContentTitle("Anything to log today?")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(logNowIntent())
            .addAction(NotificationCompat.Action.Builder(0, "Log now", logNowIntent()).build())
            .addAction(NotificationCompat.Action.Builder(0, "Nothing today", dismissTodayIntent()).build())
            .build()

        manager.notify(TAG, NOTIFICATION_ID, notification)
    }

    fun cancel() = manager.cancel(TAG, NOTIFICATION_ID)

    /** Same launch as the Quick Settings tile and the app shortcut: straight into the Quick log sheet. */
    private fun logNowIntent(): PendingIntent {
        val intent = Intent(context, QuickLogActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_HISTORY
            )
        }
        return PendingIntent.getActivity(
            context, REQUEST_CODE_LOG_NOW, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun dismissTodayIntent(): PendingIntent {
        val intent = Intent(context, DailyReminderReceiver::class.java).setAction(ACTION_DISMISS_TODAY)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE_DISMISS, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val TAG = "daily_reminder"
        const val ACTION_DISMISS_TODAY = "com.issaczerubbabel.ledgar.reminder.DISMISS_TODAY"
        private const val NOTIFICATION_ID = 1
        private const val REQUEST_CODE_LOG_NOW = 9101
        private const val REQUEST_CODE_DISMISS = 9102

        // A few short lines so the text doesn't go stale; picked deterministically by the day.
        private val MESSAGES = listOf(
            "Add today's spending before you forget.",
            "A minute now beats guessing later — log today's spends.",
            "Nothing logged yet today. Quick Add takes a few taps.",
            "Keep Left to spend accurate — add what you spent today."
        )

        fun messageFor(epochDay: Long): String =
            MESSAGES[(((epochDay % MESSAGES.size) + MESSAGES.size) % MESSAGES.size).toInt()]
    }
}
