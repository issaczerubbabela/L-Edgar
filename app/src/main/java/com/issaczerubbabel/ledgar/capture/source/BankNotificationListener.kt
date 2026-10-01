package com.issaczerubbabel.ledgar.capture.source

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.issaczerubbabel.ledgar.capture.CaptureIngestor
import com.issaczerubbabel.ledgar.capture.CaptureSource
import com.issaczerubbabel.ledgar.capture.IngestResult
import com.issaczerubbabel.ledgar.capture.notify.CaptureNotifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hands watched apps' notifications to [CaptureIngestor]. It only reads the text of apps in
 * [CaptureApps], and never logs a notification's contents.
 */
@AndroidEntryPoint
class BankNotificationListener : NotificationListenerService() {

    @Inject lateinit var ingestor: CaptureIngestor
    @Inject lateinit var notifier: CaptureNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)

    /**
     * Alerts that arrived while access was off, or while Android had stopped this service, are
     * still in the shade. Repeats are dropped by the ingestor's dedupe, so reading them is safe.
     */
    override fun onListenerConnected() {
        runCatching { activeNotifications }.getOrNull()?.forEach(::handle)
    }

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || !CaptureApps.isWatched(sbn.packageName)) return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (body.isBlank()) return

        // A bank or UPI app names itself; an SMS's sender is the notification title.
        val sender = CaptureApps.labelFor(sbn.packageName) ?: title
        val text = if (title.isBlank() || CaptureApps.labelFor(sbn.packageName) == null) body else "$title\n$body"

        scope.launch {
            // The notification's own post time, not "now": reading the shade again after a reconnect
            // must give the same alert the same time, or it would be captured a second time.
            val result = runCatching { ingestor.ingest(CaptureSource.NOTIFICATION, sender, text, sbn.postTime) }
            result.getOrNull()?.let { if (it is IngestResult.Captured && it.shouldNotify) runCatching { notifier.notify(it.id) } }
            Log.d(TAG, "${sbn.packageName}: " + result.fold({ describe(it) }, { "failed: ${it.javaClass.simpleName}" }))
        }
    }

    private fun describe(result: IngestResult): String = when (result) {
        is IngestResult.Captured -> "captured (${result.band})"
        is IngestResult.Duplicate -> "duplicate"
        is IngestResult.Unparsed -> "not a transaction"
        is IngestResult.Ignored -> "ignored (${result.reason})"
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "BankNotifListener"
    }
}
