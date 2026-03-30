package com.sheetsync.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sheetsync.BuildConfig
import com.sheetsync.data.remote.ApiService
import com.sheetsync.data.remote.SyncRequest
import com.sheetsync.data.repository.ExpenseRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import android.util.Log

import com.sheetsync.data.remote.SyncRecordDto

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ExpenseRepository,
    private val apiService: ApiService
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val unsynced = repository.getUnsynced()
            if (unsynced.isEmpty()) {
                Log.i(TAG, "Nothing to sync.")
                return Result.success()
            }
            Log.i(TAG, "Syncing ${unsynced.size} records to ${BuildConfig.APPS_SCRIPT_URL}")

            val byAction = unsynced.groupBy { it.syncAction.uppercase() }

            val inserts = byAction["INSERT"].orEmpty()
            if (inserts.isNotEmpty() && !syncRecords("insert", inserts)) {
                return Result.retry()
            }

            val updates = byAction["UPDATE"].orEmpty()
            if (updates.isNotEmpty() && !syncRecords("update", updates)) {
                return Result.retry()
            }

            val deletes = byAction["DELETE"].orEmpty()
            if (deletes.isNotEmpty() && !syncDeletes(deletes)) {
                return Result.retry()
            }

            Log.i(TAG, "Sync successful. processed=${unsynced.size}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync exception", e)
            Result.retry()
        }
    }

    private suspend fun syncRecords(action: String, records: List<com.sheetsync.data.local.entity.ExpenseRecord>): Boolean {
        val dtos = records.map { r ->
            SyncRecordDto(
                id = r.id,
                remoteTimestamp = r.remoteTimestamp,
                timestamp = r.remoteTimestamp.orEmpty(),
                date = r.date,
                type = r.type,
                expCategory = if (r.type == "Expense") r.category else "",
                incCategory = if (r.type == "Income") r.category else "",
                description = r.description,
                amount = r.amount,
                paymentMode = r.paymentMode,
                remarks = r.remarks
            )
        }

        val response = apiService.syncRecords(
            BuildConfig.APPS_SCRIPT_URL,
            SyncRequest(action = action, records = dtos)
        )
        val body = response.body()

        if (response.isSuccessful && body?.status.equals("ok", ignoreCase = true)) {
            repository.markSynced(records.map { it.id })
            return true
        }

        Log.w(TAG, "${action.uppercase()} sync failed: HTTP ${response.code()}, status=${body?.status}, message=${body?.message}")
        return false
    }

    private suspend fun syncDeletes(records: List<com.sheetsync.data.local.entity.ExpenseRecord>): Boolean {
        records.forEach { record ->
            val ts = record.remoteTimestamp
            if (ts.isNullOrBlank()) {
                // Local-only transaction that was never created remotely.
                repository.hardDeleteById(record.id)
                return@forEach
            }

            val response = apiService.syncRecords(
                BuildConfig.APPS_SCRIPT_URL,
                SyncRequest(action = "delete", targetTimestamp = ts)
            )
            val body = response.body()

            if (response.isSuccessful && body?.status.equals("ok", ignoreCase = true)) {
                repository.hardDeleteById(record.id)
            } else {
                val message = body?.message.orEmpty()
                if (message.contains("not found", ignoreCase = true)) {
                    // Treat idempotent delete as success if server record is already gone.
                    repository.hardDeleteById(record.id)
                    return@forEach
                }
                Log.w(TAG, "DELETE sync failed: id=${record.id}, ts=$ts, code=${response.code()}, status=${body?.status}, message=${body?.message}")
                return false
            }
        }
        return true
    }

    companion object {
        const val TAG = "SyncWorker"
    }
}
