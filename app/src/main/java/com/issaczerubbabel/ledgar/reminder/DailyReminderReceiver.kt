package com.issaczerubbabel.ledgar.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.issaczerubbabel.ledgar.data.preferences.ReminderPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Runs the daily reminder alarm (posts the notification, if nothing was logged today) and its
 * "Nothing today" action. `exported=false`: only this app's own alarm and notification actions
 * reach it.
 */
class DailyReminderReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun preferences(): ReminderPreferences
        fun notifier(): ReminderNotifier
        fun scheduler(): DailyReminderScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    DailyReminderScheduler.ACTION_FIRE -> onFire(deps)
                    ReminderNotifier.ACTION_DISMISS_TODAY -> onDismissToday(deps)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun onFire(deps: Dependencies) {
        val today = LocalDate.now().toEpochDay()
        val lastLogged = deps.preferences().lastLoggedEpochDay.first()
        if (ReminderRules.shouldRemind(today, lastLogged)) {
            deps.notifier().notify(ReminderNotifier.messageFor(today))
        }
        // This is a one-shot alarm: line up tomorrow's firing now that this one has run.
        deps.scheduler().reschedule()
    }

    private suspend fun onDismissToday(deps: Dependencies) {
        deps.preferences().markLoggedToday(LocalDate.now().toEpochDay())
        deps.notifier().cancel()
    }
}
