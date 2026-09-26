package com.issaczerubbabel.ledgar.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.issaczerubbabel.ledgar.data.preferences.SyncStateRepository
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
    private val syncState: SyncStateRepository,
    private val listsMerger: SheetListsMerger,
    private val preferenceRepository: ThemePreferenceRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val scriptUrl = preferenceRepository.scriptUrl.first()
            ?: return Result.failure(workDataOf(KEY_ERROR_MESSAGE to SyncUrlNotConfiguredException().message))
        return try {
            val pull = inputData.getBoolean(KEY_PULL, false)
            val allowMassDelete = inputData.getBoolean(KEY_ALLOW_MASS_DELETE, false)
            // A fresh install takes in the Sheet's Accounts first, so pulled Transactions land on them.
            if (pull) listsMerger.mergeOnce(scriptUrl)
            when (val outcome = syncer.sync(scriptUrl, pull, allowMassDelete)) {
                is TransactionSyncer.Outcome.Synced -> {
                    outcome.heldDeletes?.let { syncState.setHeldSheetDeletions(it) }
                    Log.i(TAG, "Transaction sync successful. pushed=${outcome.pushed} pulledChanges=${outcome.pulledChanges} held=${outcome.heldDeletes?.size}")
                    Result.success(workDataOf(KEY_SYNCED_COUNT to outcome.pushed))
                }
                is TransactionSyncer.Outcome.Failed -> {
                    Log.w(TAG, outcome.message)
                    Result.retry()
                }
                TransactionSyncer.Outcome.ScriptOutdated -> {
                    Log.w(TAG, "The deployed Apps Script predates Transaction IDs; sync paused until it is redeployed")
                    Result.failure(workDataOf(KEY_SCRIPT_OUTDATED to true))
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
        const val KEY_PULL = "pull"
        const val KEY_ALLOW_MASS_DELETE = "allowMassDelete"
        const val KEY_SCRIPT_OUTDATED = "scriptOutdated"
        const val KEY_ERROR_MESSAGE = "errorMessage"
    }
}
