package com.issaczerubbabel.ledgar.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.issaczerubbabel.ledgar.data.repository.RecurringRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Creates any due, automatic recurring Transaction once a day, so rent, SIPs and salary land even on
 * a day the app is never opened. `SheetSyncApp` also runs the same repository call on every app
 * start, which covers the common case of opening the app after the due date.
 */
@HiltWorker
class RecurringMaterializationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val recurringRepository: RecurringRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = try {
        val created = recurringRepository.materializeDue()
        Log.i(TAG, "Recurring materialization created $created transaction(s)")
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Recurring materialization failed", e)
        Result.retry()
    }

    companion object {
        const val TAG = "RecurringMaterialization"
        const val WORK_NAME = "RecurringMaterializationWorker"
    }
}
