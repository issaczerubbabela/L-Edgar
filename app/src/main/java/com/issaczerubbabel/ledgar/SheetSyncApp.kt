package com.issaczerubbabel.ledgar

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.issaczerubbabel.ledgar.data.repository.RecurringRepository
import com.issaczerubbabel.ledgar.sync.SyncScheduler
import com.issaczerubbabel.ledgar.reminder.DailyReminderScheduler
import com.issaczerubbabel.ledgar.sync.SyncTriggers
import com.issaczerubbabel.ledgar.util.SystemCurrentDate
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SheetSyncApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncTriggers: SyncTriggers

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var recurringRepository: RecurringRepository

    @Inject
    lateinit var reminderScheduler: DailyReminderScheduler

    @Inject
    lateinit var currentDate: SystemCurrentDate

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        currentDate.start()
        syncTriggers.start()
        syncScheduler.scheduleRecurringMaterialization()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            recurringRepository.materializeDue()
        }
        // Re-arms the alarm if the preference says on but nothing is scheduled yet (a fresh
        // install restoring device backup data, for instance). A no-op the rest of the time:
        // AlarmManager alarms otherwise survive the app's own process restarting.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { reminderScheduler.reschedule() }
    }
}
