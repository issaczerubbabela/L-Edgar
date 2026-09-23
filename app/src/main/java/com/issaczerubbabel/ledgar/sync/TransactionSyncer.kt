package com.issaczerubbabel.ledgar.sync

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.remote.ApiService
import com.issaczerubbabel.ledgar.data.remote.SyncRequest
import com.issaczerubbabel.ledgar.data.remote.SyncResponse
import javax.inject.Inject

/**
 * Two-way Sync keyed by Transaction ID (ADR-0003). A Pull merges the Sheet into the phone, then a
 * Push sends the phone's pending changes as upserts and deletes by ID, which are safe to repeat.
 *
 * Only a version-2 script is ever written to. An older one ignores version-2 requests (they carry no
 * `records`) and its replies carry no `scriptVersion`, which is how it is detected.
 */
class TransactionSyncer @Inject constructor(
    private val store: TransactionSyncStore,
    private val api: ApiService
) {
    sealed interface Outcome {
        /** [heldDeletes] is null when this run didn't Pull, so what was held before still stands. */
        data class Synced(val pushed: Int, val pulledChanges: Int, val heldDeletes: List<Long>?) : Outcome
        data class Failed(val message: String) : Outcome
        data object ScriptOutdated : Outcome
    }

    suspend fun sync(scriptUrl: String, pull: Boolean, allowMassDelete: Boolean = false): Outcome {
        var pulledChanges = 0
        var heldDeletes: List<Long>? = null
        if (pull || store.hasRowsWithoutSyncId()) {
            when (val pulled = pull(scriptUrl, allowMassDelete)) {
                is PullResult.Done -> {
                    pulledChanges = pulled.applied
                    heldDeletes = pulled.heldDeletes
                }
                is PullResult.Stop -> return pulled.outcome
            }
        }
        val pushed = when (val pushOutcome = push(scriptUrl)) {
            is PushResult.Done -> pushOutcome
            is PushResult.Stop -> return pushOutcome.outcome
        }
        // Rows the script refused changed in the Sheet since this phone last saw them: merge, don't overwrite.
        if (pushed.staleSeen) {
            when (val pulled = pull(scriptUrl, allowMassDelete)) {
                is PullResult.Done -> {
                    pulledChanges += pulled.applied
                    heldDeletes = pulled.heldDeletes
                }
                is PullResult.Stop -> return pulled.outcome
            }
        }
        return Outcome.Synced(pushed.pushed, pulledChanges, heldDeletes)
    }

    private sealed interface PullResult {
        data class Done(val applied: Int, val heldDeletes: List<Long>) : PullResult
        data class Stop(val outcome: Outcome) : PullResult
    }

    private suspend fun pull(scriptUrl: String, allowMassDelete: Boolean): PullResult {
        val response = api.importRecords(scriptUrl, target = "transactions")
        val body = response.body()
        if (!response.isSuccessful || !body?.status.equals("ok", ignoreCase = true)) {
            return PullResult.Stop(Outcome.Failed("Reading the Sheet failed (HTTP ${response.code()}): ${body?.message ?: "unknown error"}"))
        }
        if ((body?.scriptVersion ?: 0) < REQUIRED_SCRIPT_VERSION) return PullResult.Stop(Outcome.ScriptOutdated)

        val rows = body?.data.orEmpty().mapNotNull { dto -> dto.id?.takeIf { it.isNotBlank() }?.let { PulledRow(it, dto) } }
        val plan = SheetMerge.plan(store.allTransactions(), rows, store.accountNamesById(), allowMassDelete)
        return PullResult.Done(store.apply(plan.steps), plan.heldDeletes.map { it.localId })
    }

    private sealed interface PushResult {
        data class Done(val pushed: Int, val staleSeen: Boolean) : PushResult
        data class Stop(val outcome: Outcome) : PushResult
    }

    private suspend fun push(scriptUrl: String): PushResult {
        val pending = store.pending().filter { it.syncId != null && it.sheetConflictJson == null }
        if (pending.isEmpty()) return PushResult.Done(0, staleSeen = false)

        val (deletes, upserts) = pending.partition { it.syncAction.equals("DELETE", ignoreCase = true) }
        val accountNames = store.accountNamesById()

        var pushed = 0
        var staleSeen = false
        upserts.chunked(BATCH_SIZE).forEach { batch ->
            val request = SyncRequest(
                action = "upsert",
                target = "transactions",
                transactions = batch.map { it.toSheetDto(accountNames) }
            )
            val reply = when (val sent = send(scriptUrl, request)) {
                is Sent.Ok -> sent.reply
                is Sent.Stop -> return PushResult.Stop(sent.outcome)
            }
            val stale = reply.stale.orEmpty().toSet()
            staleSeen = staleSeen || stale.isNotEmpty()
            batch.filter { it.syncId !in stale }.forEach {
                store.markSyncedIfUnchanged(it.id, it.localVersion, reply.revisions?.get(it.syncId))
                pushed++
            }
        }

        deletes.chunked(BATCH_SIZE).forEach { batch ->
            val request = SyncRequest(action = "delete_ids", target = "transactions", ids = batch.mapNotNull(ExpenseRecord::syncId))
            (send(scriptUrl, request) as? Sent.Stop)?.let { return PushResult.Stop(it.outcome) }
            batch.forEach { store.finishDeleteIfUnchanged(it.id, it.localVersion) }
            pushed += batch.size
        }
        return PushResult.Done(pushed, staleSeen)
    }

    private sealed interface Sent {
        data class Ok(val reply: SyncResponse) : Sent
        data class Stop(val outcome: Outcome) : Sent
    }

    private suspend fun send(scriptUrl: String, request: SyncRequest): Sent {
        val response = api.syncRecords(scriptUrl, request)
        val body = response.body()
        if (!response.isSuccessful || body == null || !body.status.equals("ok", ignoreCase = true)) {
            return Sent.Stop(Outcome.Failed("Transaction ${request.action} failed (HTTP ${response.code()}): ${body?.message ?: "unknown error"}"))
        }
        return if ((body.scriptVersion ?: 0) < REQUIRED_SCRIPT_VERSION) Sent.Stop(Outcome.ScriptOutdated) else Sent.Ok(body)
    }

    companion object {
        const val REQUIRED_SCRIPT_VERSION = 2
        const val BATCH_SIZE = 50
    }
}
