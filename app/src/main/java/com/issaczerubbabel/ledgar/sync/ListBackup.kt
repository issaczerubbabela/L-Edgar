package com.issaczerubbabel.ledgar.sync

import com.issaczerubbabel.ledgar.data.preferences.SyncStateRepository
import com.issaczerubbabel.ledgar.data.remote.ApiService
import com.issaczerubbabel.ledgar.data.remote.SyncRequest
import javax.inject.Inject

/**
 * Sends one list Backup based on the revision this phone last saw (ADR-0010). When the Sheet's tab
 * changed since, the script writes nothing, and the list waits in More for the user to Import or Overwrite.
 */
class ListBackup @Inject constructor(
    private val api: ApiService,
    private val syncState: SyncStateRepository
) {
    sealed interface Outcome {
        data object Written : Outcome
        /** The tab changed in the Sheet since this phone's last Backup; nothing was written. */
        data object SheetChanged : Outcome
        /** The script answered without [expectedType]: it predates the target. */
        data object ScriptOutdated : Outcome
        data class Failed(val message: String) : Outcome
    }

    suspend fun send(scriptUrl: String, request: SyncRequest, expectedType: String? = null): Outcome {
        val target = request.target
        val response = api.syncRecords(scriptUrl, request.copy(baseRevision = syncState.listRevision(target)))
        val body = response.body()
        if (!response.isSuccessful || !body?.status.equals("ok", ignoreCase = true)) {
            return Outcome.Failed("${target.replaceFirstChar { it.uppercase() }} backup failed (HTTP ${response.code()}): ${body?.message ?: "unknown error"}")
        }
        if (body?.sheetChanged == true) {
            syncState.setListChangedInSheet(target, true)
            return Outcome.SheetChanged
        }
        if (expectedType != null && body?.type != expectedType) return Outcome.ScriptOutdated
        // A script older than version 3 sends no revision, so the next Backup goes without a base, as before.
        syncState.setListRevision(target, body?.revision)
        syncState.setListChangedInSheet(target, false)
        return Outcome.Written
    }
}
