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
                Log.d(TAG, "Nothing to sync.")
                return Result.success()
            }
            Log.d(TAG, "Syncing ${unsynced.size} records…")

            // Map to DTO — split category into expCategory / incCategory
            // date is already YYYY-MM-DD (LocalDate.toString()), Sheets parses this natively
            val dtos = unsynced.map { r ->
                SyncRecordDto(
                    id = r.id,
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

            val response = apiService.syncRecords(BuildConfig.APPS_SCRIPT_URL, SyncRequest(dtos))

            if (response.isSuccessful) {
                repository.markSynced(unsynced.map { it.id })
                Log.d(TAG, "Sync successful.")
                Result.success()
            } else {
                Log.w(TAG, "Sync failed: HTTP ${response.code()}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync exception: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        const val TAG = "SyncWorker"
    }
}
