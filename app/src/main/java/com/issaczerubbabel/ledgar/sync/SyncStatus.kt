package com.issaczerubbabel.ledgar.sync

import androidx.work.WorkInfo

/** Where the Sync status indicator stands. */
enum class SyncStatus { Idle, Syncing, Synced, Failed, NeedsScriptUpdate }

/** The part of a Sync job's WorkInfo that decides [SyncStatus]. */
data class SyncJob(
    val state: WorkInfo.State,
    val runAttemptCount: Int,
    val stopReason: Int = WorkInfo.STOP_REASON_NOT_STOPPED,
    val scriptOutdated: Boolean = false
)

/**
 * A replaced or cancelled job isn't a failure, and neither is one the system stopped (lost network,
 * killed process), even though WorkManager counts those as attempts too. Only a job that ran and
 * asked to retry, and is now waiting to, shows as [SyncStatus.Failed].
 */
fun syncStatusOf(jobs: List<SyncJob>): SyncStatus = when {
    jobs.any { it.state == WorkInfo.State.RUNNING } -> SyncStatus.Syncing
    jobs.any { it.isWaitingToRetryAfterFailure() } -> SyncStatus.Failed
    jobs.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } -> SyncStatus.Syncing
    jobs.any { it.state == WorkInfo.State.FAILED && it.scriptOutdated } -> SyncStatus.NeedsScriptUpdate
    jobs.any { it.state == WorkInfo.State.FAILED } -> SyncStatus.Failed
    jobs.any { it.state == WorkInfo.State.SUCCEEDED } -> SyncStatus.Synced
    else -> SyncStatus.Idle
}

private fun SyncJob.isWaitingToRetryAfterFailure() =
    state == WorkInfo.State.ENQUEUED && runAttemptCount > 0 && stopReason == WorkInfo.STOP_REASON_NOT_STOPPED
