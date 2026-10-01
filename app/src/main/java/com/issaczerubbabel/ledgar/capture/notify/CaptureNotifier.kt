package com.issaczerubbabel.ledgar.capture.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.issaczerubbabel.ledgar.R
import com.issaczerubbabel.ledgar.data.local.dao.AccountDao
import com.issaczerubbabel.ledgar.data.local.dao.CaptureDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the "confirm this?" notification, only for a capture that is ready: High confidence with a
 * known Account (the ingestor decides that). The lock screen sees a generic line, and Confirm needs
 * the phone unlocked, so neither shows nor saves anything to someone holding a locked phone.
 */
@Singleton
class CaptureNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val captureDao: CaptureDao,
    private val accountDao: AccountDao
) {
    private val manager = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission") // areNotificationsEnabled() is checked first.
    suspend fun notify(captureId: Long) {
        if (!manager.areNotificationsEnabled()) return
        val capture = captureDao.getById(captureId)?.takeIf { it.status == "PENDING" } ?: return
        val category = capture.suggestedCategory ?: return
        val account = capture.accountId?.let { accountDao.getAccountById(it) } ?: return

        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Auto-capture confirmations")
                .setDescription("Asks you to confirm a captured bank or UPI transaction")
                .build()
        )

        val content = CaptureNotificationText.build(capture, category, account.accountName)
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_log_expense)
            .setContentTitle(content.publicTitle)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_log_expense)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(captureId))
            .addAction(
                NotificationCompat.Action.Builder(0, "Confirm", actionIntent(ACTION_CONFIRM, captureId))
                    .setAuthenticationRequired(true)
                    .build()
            )
            .addAction(
                NotificationCompat.Action.Builder(0, "Not mine", actionIntent(ACTION_DISMISS, captureId)).build()
            )
            .build()

        manager.notify(TAG, notificationId(captureId), notification)
    }

    fun cancel(captureId: Long) = manager.cancel(TAG, notificationId(captureId))

    private fun notificationId(captureId: Long): Int = (captureId % Int.MAX_VALUE).toInt()

    private fun actionIntent(action: String, captureId: Long): PendingIntent {
        val intent = Intent(context, CaptureActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_CAPTURE_ID, captureId)
        // One request code per capture and action, or the second button would replace the first.
        val requestCode = notificationId(captureId) * 2 + if (action == ACTION_CONFIRM) 0 else 1
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Opens the app normally, so the app lock still applies before anything is shown. */
    private fun openAppIntent(captureId: Long): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context, notificationId(captureId) * 2 + 1_000_000, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val CHANNEL_ID = "capture_confirmations"
        const val TAG = "capture"
        const val ACTION_CONFIRM = "com.issaczerubbabel.ledgar.capture.CONFIRM"
        const val ACTION_DISMISS = "com.issaczerubbabel.ledgar.capture.DISMISS"
        const val EXTRA_CAPTURE_ID = "capture_id"
    }
}
