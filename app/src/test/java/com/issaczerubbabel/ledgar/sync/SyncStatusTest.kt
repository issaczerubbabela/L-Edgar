package com.issaczerubbabel.ledgar.sync

import androidx.work.WorkInfo
import androidx.work.WorkInfo.State
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStatusTest {

    @Test
    fun `a cancelled or replaced job is not a failure`() {
        assertEquals(SyncStatus.Idle, syncStatusOf(listOf(SyncJob(State.CANCELLED, 0))))
        assertEquals(SyncStatus.Synced, syncStatusOf(listOf(SyncJob(State.CANCELLED, 0), SyncJob(State.SUCCEEDED, 0))))
    }

    @Test
    fun `a job waiting to retry after a failed attempt shows as failed`() {
        assertEquals(SyncStatus.Failed, syncStatusOf(listOf(SyncJob(State.ENQUEUED, 2))))
        assertEquals(
            "a job queued behind the retrying one doesn't hide the failure",
            SyncStatus.Failed,
            syncStatusOf(listOf(SyncJob(State.ENQUEUED, 2), SyncJob(State.BLOCKED, 0)))
        )
    }

    @Test
    fun `a job the system stopped is not a failure`() {
        val stoppedForNetwork = SyncJob(State.ENQUEUED, runAttemptCount = 1, stopReason = WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY)
        assertEquals(SyncStatus.Syncing, syncStatusOf(listOf(stoppedForNetwork)))
    }

    @Test
    fun `queued or running work shows as syncing`() {
        assertEquals(SyncStatus.Syncing, syncStatusOf(listOf(SyncJob(State.ENQUEUED, 0))))
        assertEquals(SyncStatus.Syncing, syncStatusOf(listOf(SyncJob(State.SUCCEEDED, 0), SyncJob(State.RUNNING, 0))))
        assertEquals(SyncStatus.Syncing, syncStatusOf(listOf(SyncJob(State.RUNNING, 3), SyncJob(State.BLOCKED, 0))))
    }

    @Test
    fun `finished work shows its result`() {
        assertEquals(SyncStatus.Synced, syncStatusOf(listOf(SyncJob(State.SUCCEEDED, 0))))
        assertEquals(SyncStatus.Failed, syncStatusOf(listOf(SyncJob(State.FAILED, 0))))
        assertEquals(SyncStatus.Idle, syncStatusOf(emptyList()))
    }
}
