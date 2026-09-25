package com.issaczerubbabel.ledgar.sync

import androidx.room.InvalidationTracker
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Requests work from the database itself rather than from each screen, so no write path can forget
 * to: any new or changed Transaction waiting to Sync requests a Sync (including ones left over from a
 * previous run), and any change to what a Backup copies requests a Backup.
 */
@Singleton
class SyncTriggers @Inject constructor(
    private val database: SheetSyncDatabase,
    private val scheduler: SyncScheduler
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Transactions are included because the Accounts backup carries each account's current balance,
    // and the salary-cycle backup covers cycles, buckets and category routing.
    private val backupObserver = object : InvalidationTracker.Observer(BACKED_UP_TABLES) {
        override fun onInvalidated(tables: Set<String>) = scheduler.requestBackup()
    }

    fun start() {
        scope.launch {
            database.expenseDao().observePendingVersions()
                .filter { it.isNotEmpty() }
                .distinctUntilChanged()
                .collect { scheduler.requestSync() }
        }
        database.invalidationTracker.addObserver(backupObserver)
    }

    private companion object {
        val BACKED_UP_TABLES = arrayOf(
            "account_records", "dropdown_options", "budgets", "expense_records",
            "budget_cycles", "budget_buckets", "bucket_categories"
        )
    }
}
