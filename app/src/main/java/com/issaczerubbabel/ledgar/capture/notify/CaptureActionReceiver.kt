package com.issaczerubbabel.ledgar.capture.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs the Confirm / Not mine buttons of a capture notification. */
class CaptureActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun handler(): CaptureActionHandler
        fun notifier(): CaptureNotifier
    }

    override fun onReceive(context: Context, intent: Intent) {
        val captureId = intent.getLongExtra(CaptureNotifier.EXTRA_CAPTURE_ID, -1L)
        if (captureId < 0) return

        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val done = when (intent.action) {
                    CaptureNotifier.ACTION_CONFIRM -> deps.handler().confirm(captureId)
                    CaptureNotifier.ACTION_DISMISS -> { deps.handler().dismiss(captureId); true }
                    else -> false
                }
                if (done) deps.notifier().cancel(captureId)
            } finally {
                pending.finish()
            }
        }
    }
}
