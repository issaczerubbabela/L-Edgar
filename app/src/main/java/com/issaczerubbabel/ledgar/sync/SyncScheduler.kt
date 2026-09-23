package com.issaczerubbabel.ledgar.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Where the Sync status indicator stands. */
enum class SyncStatus { Idle, Syncing, Synced, Failed }

/** The part of a Sync job's WorkInfo that decides [SyncStatus]. */
data class SyncJob(val state: WorkInfo.State, val runAttemptCount: Int)

/**
 * A replaced or cancelled job isn't a failure, so CANCELLED never shows as [SyncStatus.Failed].
 * A job waiting to retry after a failed attempt does, since the change hasn't reached the Sheet.
 */
fun syncStatusOf(jobs: List<SyncJob>): SyncStatus = when {
    jobs.any { it.state == WorkInfo.State.RUNNING } -> SyncStatus.Syncing
    jobs.any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0 } -> SyncStatus.Failed
    jobs.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } -> SyncStatus.Syncing
    jobs.any { it.state == WorkInfo.State.FAILED } -> SyncStatus.Failed
    jobs.any { it.state == WorkInfo.State.SUCCEEDED } -> SyncStatus.Synced
    else -> SyncStatus.Idle
}

/** The only place Sync and Backup work is queued. */
@Singleton
class SyncScheduler @Inject constructor(private val workManager: WorkManager) {

    val transactionSyncStatus: Flow<SyncStatus> = workManager
        .getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME)
        .map { infos -> syncStatusOf(infos.map { SyncJob(it.state, it.runAttemptCount) }) }

    val backupWorkInfos: Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(BackupWorker.WORK_NAME)

    /**
     * Call after any local change. A running Sync is never cancelled: the new job queues behind it,
     * so changes made while it runs still go out, and a request already on its way can't be cut off.
     */
    fun requestSync() {
        workManager.enqueueUniqueWork(SyncWorker.WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, transactionSyncRequest())
        requestBackup()
    }

    /** Skips the retry wait of a failed Sync, unless one is running right now. */
    suspend fun retrySync() {
        val running = workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME).first()
            .any { it.state == WorkInfo.State.RUNNING }
        if (running) return
        workManager.enqueueUniqueWork(SyncWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, transactionSyncRequest())
    }

    /**
     * Backups replace whole Sheet tabs, so several requests can share one later run. A pending or
     * running Backup is kept rather than restarted.
     */
    fun requestBackup() = enqueueBackup(BACKUP_DELAY_SECONDS, ExistingWorkPolicy.KEEP)

    fun backupNow() = enqueueBackup(0, ExistingWorkPolicy.REPLACE)

    private fun enqueueBackup(delaySeconds: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(networkConstraint())
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .addTag(BackupWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(BackupWorker.WORK_NAME, policy, request)
    }

    private fun transactionSyncRequest() = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(networkConstraint())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
        .addTag(SyncWorker.TAG)
        .build()

    private fun networkConstraint() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private companion object {
        const val BACKUP_DELAY_SECONDS = 20L
        const val RETRY_BACKOFF_SECONDS = 15L
    }
}
