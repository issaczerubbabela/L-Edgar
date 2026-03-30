package com.sheetsync.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sheetsync.BuildConfig
import com.sheetsync.data.remote.ApiService
import com.sheetsync.data.remote.DropdownSyncDto
import com.sheetsync.data.remote.SyncRequest
import com.sheetsync.data.repository.DropdownOptionRepository
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
    private val dropdownOptionRepository: DropdownOptionRepository,
    private val apiService: ApiService
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val unsynced = repository.getUnsynced()
            if (unsynced.isEmpty()) {
                Log.i(TAG, "No transaction changes to sync.")
            } else {
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
            }

            val dropdownBackupCount = backupDropdownOptions() ?: run {
                return Result.retry()
            }

            Log.i(TAG, "Sync successful. processed=${unsynced.size}")
            Result.success(
                workDataOf(
                    KEY_DROPDOWN_BACKUP_COUNT to dropdownBackupCount
                )
            )
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
            SyncRequest(action = action, target = "transactions", records = dtos)
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
                SyncRequest(action = "delete", target = "transactions", targetTimestamp = ts)
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

    private suspend fun backupDropdownOptions(): Int? {
        val options = dropdownOptionRepository.getAllOptionsSnapshot()
        val payload = options.map { option ->
            DropdownSyncDto(
                id = option.id,
                optionType = option.optionType,
                name = option.name,
                displayOrder = option.displayOrder
            )
        }

        val response = apiService.syncRecords(
            BuildConfig.APPS_SCRIPT_URL,
            SyncRequest(action = "backup", target = "dropdowns", records = payload)
        )
        val body = response.body()
        if (response.isSuccessful && body?.status.equals("ok", ignoreCase = true)) {
            Log.i(TAG, "Dropdown backup successful. count=${payload.size}")
            return payload.size
        }

        Log.w(TAG, "Dropdown backup failed: HTTP ${response.code()}, status=${body?.status}, message=${body?.message}")
        return null
    }

    companion object {
        const val TAG = "SyncWorker"
        const val KEY_DROPDOWN_BACKUP_COUNT = "dropdownBackupCount"
    }
}
