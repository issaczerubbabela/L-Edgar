package com.issaczerubbabel.ledgar.sync

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.remote.ApiService
import com.issaczerubbabel.ledgar.data.remote.SyncRecordDto
import com.issaczerubbabel.ledgar.data.remote.SyncRequest
import com.issaczerubbabel.ledgar.util.generateTimestampKey
import com.issaczerubbabel.ledgar.util.normalizeTimestampKey
import java.time.LocalDateTime

/**
 * Sends pending Transaction changes so that repeating a Sync can never duplicate a row.
 *
 * Every insert and update goes out as the script's `update` action, which overwrites the row with
 * the Transaction's Remote timestamp or appends it if there is none. That makes each write safe to
 * repeat even on scripts deployed before this change. It relies on the Remote timestamp being saved
 * in Room before the first attempt, so a retry after a lost reply targets the same row.
 */
class TransactionSyncer(
    private val store: TransactionSyncStore,
    private val api: ApiService,
    private val now: () -> LocalDateTime = LocalDateTime::now
) {
    sealed interface Outcome {
        data class Synced(val count: Int) : Outcome
        data class Failed(val message: String) : Outcome
    }

    suspend fun sync(scriptUrl: String): Outcome {
        assignMissingRemoteTimestamps()

        val pending = store.pending()
        if (pending.isEmpty()) return Outcome.Synced(0)

        val (deletes, upserts) = pending.partition { it.syncAction.equals(DELETE, ignoreCase = true) }
        val accountNames = store.accountNamesById()

        upserts.chunked(BATCH_SIZE).forEach { batch ->
            upsert(batch, accountNames, scriptUrl)?.let { return Outcome.Failed(it) }
        }
        deletes.forEach { record ->
            delete(record, scriptUrl)?.let { return Outcome.Failed(it) }
        }
        return Outcome.Synced(pending.size)
    }

    private suspend fun assignMissingRemoteTimestamps() {
        val missing = store.pending().filter { it.remoteTimestamp.isNullOrBlank() }
        if (missing.isEmpty()) return

        val inUse = store.remoteTimestampsInUse().toMutableSet()
        var candidate = now().withNano(0)
        missing.forEach { record ->
            if (record.syncAction.equals(DELETE, ignoreCase = true)) {
                // Never reached the Sheet: nothing to delete there.
                store.deleteIfUnchanged(record.id, record.localVersion)
                return@forEach
            }
            while (!inUse.add(generateTimestampKey(candidate))) {
                candidate = candidate.plusSeconds(1)
            }
            store.assignRemoteTimestamp(record.id, generateTimestampKey(candidate))
        }
    }

    private suspend fun upsert(
        batch: List<ExpenseRecord>,
        accountNames: Map<Long, String>,
        scriptUrl: String
    ): String? {
        val response = api.syncRecords(
            scriptUrl,
            SyncRequest(action = "update", target = "transactions", records = batch.map { it.toSyncDto(accountNames) })
        )
        val body = response.body()
        if (!response.isSuccessful || !body?.status.equals("ok", ignoreCase = true)) {
            return "Transaction sync failed (HTTP ${response.code()}): ${body?.message ?: "unknown error"}"
        }
        batch.forEach { store.markSyncedIfUnchanged(it.id, it.localVersion) }
        return null
    }

    private suspend fun delete(record: ExpenseRecord, scriptUrl: String): String? {
        val response = api.syncRecords(
            scriptUrl,
            SyncRequest(action = "delete", target = "transactions", targetTimestamp = record.remoteKey())
        )
        val body = response.body()
        // A count of 0, or "not found", means the row is already gone: the delete has taken effect.
        val alreadyGone = body?.message.orEmpty().contains("not found", ignoreCase = true)
        if (!alreadyGone && (!response.isSuccessful || !body?.status.equals("ok", ignoreCase = true))) {
            return "Transaction delete failed (HTTP ${response.code()}): ${body?.message ?: "unknown error"}"
        }
        store.deleteIfUnchanged(record.id, record.localVersion)
        return null
    }

    private fun ExpenseRecord.remoteKey(): String =
        normalizeTimestampKey(remoteTimestamp) ?: remoteTimestamp.orEmpty().trim()

    private fun ExpenseRecord.toSyncDto(accountNames: Map<Long, String>): SyncRecordDto {
        val fromName = fromAccountId?.let { accountNames[it] } ?: fromAccountName
        val toName = toAccountId?.let { accountNames[it] } ?: toAccountName
        val combinedAccountName = when (type) {
            "Expense", "Income" ->
                accountId?.let { accountNames[it] } ?: accountName ?: fromAccountName ?: toAccountName ?: ""
            "Transfer" -> listOf(fromName.orEmpty(), toName.orEmpty()).filter { it.isNotBlank() }.joinToString(" -> ")
            else -> ""
        }
        val key = remoteKey()
        return SyncRecordDto(
            id = id,
            remoteTimestamp = key,
            timestamp = key,
            date = date,
            type = type,
            expCategory = if (type == "Expense") category else "",
            incCategory = if (type == "Income") category else "",
            description = description,
            amount = amount,
            accountName = combinedAccountName,
            fromAccountName = if (type == "Transfer") fromName else null,
            toAccountName = if (type == "Transfer") toName else null,
            remarks = remarks,
            isBookmarked = isBookmarked
        )
    }

    companion object {
        private const val DELETE = "DELETE"

        /** Each `update` rescans the sheet per row, so keep requests well inside Apps Script limits. */
        const val BATCH_SIZE = 20
    }
}
