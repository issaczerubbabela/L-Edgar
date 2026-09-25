package com.issaczerubbabel.ledgar.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.issaczerubbabel.ledgar.data.preferences.ThemePreferenceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Transaction Sync. Backups run separately in [BackupWorker]. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncer: TransactionSyncer,
    private val preferenceRepository: ThemePreferenceRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val scriptUrl = preferenceRepository.scriptUrl.first()
            ?: return Result.failure(workDataOf(KEY_ERROR_MESSAGE to SyncUrlNotConfiguredException().message))
        return try {
            when (val outcome = syncer.sync(scriptUrl)) {
                is TransactionSyncer.Outcome.Synced -> {
                    Log.i(TAG, "Transaction sync successful. processed=${outcome.count}")
                    Result.success(workDataOf(KEY_SYNCED_COUNT to outcome.count))
                }
                is TransactionSyncer.Outcome.Failed -> {
                    Log.w(TAG, outcome.message)
                    Result.retry()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Transaction sync exception", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "SyncWorker"
        const val KEY_SYNCED_COUNT = "syncedCount"
        const val KEY_ERROR_MESSAGE = "errorMessage"
    }
}
