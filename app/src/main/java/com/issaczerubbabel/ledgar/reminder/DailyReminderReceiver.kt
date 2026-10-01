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

    // Method names are prefixed to avoid colliding with another @EntryPoint interface's method of
    // the same name but a different return type: Hilt aggregates all of them onto one generated
    // component, and two same-named, differently-typed getters there fail to compile (as
    // notifier() once did here, clashing with CaptureActionReceiver.Dependencies.notifier()).
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun reminderPreferences(): ReminderPreferences
        fun reminderNotifier(): ReminderNotifier
        fun reminderScheduler(): DailyReminderScheduler
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
        val lastLogged = deps.reminderPreferences().lastLoggedEpochDay.first()
        if (ReminderRules.shouldRemind(today, lastLogged)) {
            deps.reminderNotifier().notify(ReminderNotifier.messageFor(today))
        }
        // This is a one-shot alarm: line up tomorrow's firing now that this one has run.
        deps.reminderScheduler().reschedule()
    }

    private suspend fun onDismissToday(deps: Dependencies) {
        deps.reminderPreferences().markLoggedToday(LocalDate.now().toEpochDay())
        deps.reminderNotifier().cancel()
    }
}
