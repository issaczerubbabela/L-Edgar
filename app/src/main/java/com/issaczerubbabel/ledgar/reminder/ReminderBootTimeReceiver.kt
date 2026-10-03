package com.issaczerubbabel.ledgar.reminder

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

/**
 * Re-arms the daily reminder's alarm after a reboot (which clears every [android.app.AlarmManager]
 * alarm) or a clock/time-zone change (which could otherwise fire it at the wrong wall-clock time).
 */
class ReminderBootTimeReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun reminderScheduler(): DailyReminderScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                deps.reminderScheduler().reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}
