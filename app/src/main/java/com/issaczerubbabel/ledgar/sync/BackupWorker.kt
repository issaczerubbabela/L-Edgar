package com.issaczerubbabel.ledgar.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.issaczerubbabel.ledgar.data.bucket.BucketBackupMapper
import com.issaczerubbabel.ledgar.data.preferences.ThemePreferenceRepository
import com.issaczerubbabel.ledgar.data.remote.AccountSyncDto
import com.issaczerubbabel.ledgar.data.remote.ApiService
import com.issaczerubbabel.ledgar.data.remote.BudgetSyncDto
import com.issaczerubbabel.ledgar.data.remote.DropdownSyncDto
import com.issaczerubbabel.ledgar.data.remote.SyncRequest
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.BucketBudgetRepository
import com.issaczerubbabel.ledgar.data.repository.BudgetRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Replaces the Sheet's Accounts, Dropdown options and Budgets tabs with the phone's current lists.
 * Runs apart from Transaction Sync so a failing Backup can never hold Transactions back.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val accountRepository: AccountRepository,
    private val dropdownOptionRepository: DropdownOptionRepository,
    private val budgetRepository: BudgetRepository,
    private val bucketBudgetRepository: BucketBudgetRepository,
    private val apiService: ApiService,
    private val preferenceRepository: ThemePreferenceRepository,
    private val listsMerger: SheetListsMerger
) : CoroutineWorker(context, workerParams) {

    private var lastSyncError: String? = null

    override suspend fun doWork(): Result {
        val scriptUrl = preferenceRepository.scriptUrl.first()
            ?: return Result.failure(workDataOf(KEY_ERROR_MESSAGE to SyncUrlNotConfiguredException().message))
        return try {
            listsMerger.mergeOnce(scriptUrl)
            val accounts = accountRepository.getAllAccountsSnapshot()
            val accountCount = backupAccounts(accounts, scriptUrl)
            val dropdownCount = accountCount?.let { backupDropdownOptions(scriptUrl) }
            val budgetCount = dropdownCount?.let { backupBudgets(scriptUrl) }
            val bucketCount = budgetCount?.let { backupBucketBudgets(scriptUrl) }
            if (accountCount == null || dropdownCount == null || budgetCount == null || bucketCount == null) {
                retryOrFail(lastSyncError ?: "Backup failed")
            } else {
                Log.i(TAG, "Backup successful. accounts=$accountCount dropdowns=$dropdownCount budgets=$budgetCount buckets=$bucketCount")
                Result.success(
                    workDataOf(
                        KEY_ACCOUNTS_BACKUP_COUNT to accountCount,
                        KEY_DROPDOWN_BACKUP_COUNT to dropdownCount,
                        KEY_BUDGET_BACKUP_COUNT to budgetCount,
                        KEY_BUCKET_BACKUP_COUNT to bucketCount
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Backup exception", e)
            retryOrFail(e.message ?: "Backup failed")
        }
    }

    private fun retryOrFail(message: String): Result =
        if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry()
        else Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))

    private suspend fun backupDropdownOptions(scriptUrl: String): Int? {
        val options = dropdownOptionRepository
            .getAllOptionsSnapshot()
            .filterNot { it.optionType == "PAYMENT_MODE" }
        val payload = options.map { option ->
            DropdownSyncDto(
                id = option.id,
                optionType = option.optionType,
                name = option.name,
                displayOrder = option.displayOrder
            )
        }

        if (payload.isEmpty()) {
            Log.i(TAG, "Skipping dropdown backup: local payload is empty")
            return 0
        }

        val response = apiService.syncRecords(
            scriptUrl,
            SyncRequest(action = "backup", target = "dropdowns", records = payload)
        )
        val body = response.body()
        if (response.isSuccessful && body?.status.equals("ok", ignoreCase = true)) {
            Log.i(TAG, "Dropdown backup successful. count=${payload.size}")
            return payload.size
        }

        lastSyncError = "Dropdown backup failed (HTTP ${response.code()}): ${body?.message ?: "unknown error"}"
        Log.w(TAG, "Dropdown backup failed: HTTP ${response.code()}, status=${body?.status}, message=${body?.message}")
        return null
    }

    private suspend fun backupBudgets(scriptUrl: String): Int? {
        val budgets = budgetRepository.getAllBudgetsSnapshot()
        val payload = budgets.map { budget ->
            BudgetSyncDto(
                id = budget.id,
                monthYear = budget.monthYear,
                category = budget.category,
                amount = budget.amount
            )
        }

        if (payload.isEmpty()) {
            Log.i(TAG, "Skipping budget backup: local payload is empty")
            return 0
        }

        val response = apiService.syncRecords(
            scriptUrl,
            SyncRequest(action = "backup", target = "budgets", records = payload)
        )
        val body = response.body()
        if (response.isSuccessful && body?.status.equals("ok", ignoreCase = true)) {
            Log.i(TAG, "Budget backup successful. count=${payload.size}")
            return payload.size
        }

        lastSyncError = "Budget backup failed (HTTP ${response.code()}): ${body?.message ?: "unknown error"}"
        Log.w(TAG, "Budget backup failed: HTTP ${response.code()}, status=${body?.status}, message=${body?.message}")
        return null
    }

    private suspend fun backupAccounts(
        accounts: List<com.issaczerubbabel.ledgar.data.local.entity.AccountRecord>,
        scriptUrl: String
    ): Int? {
        if (accounts.isEmpty()) {
            Log.i(TAG, "Skipping account backup: local payload is empty")
            return 0
        }

        val balancesByAccountId = accountRepository.getAccountBalances()
            .first()
            .associate { it.accountId to it.balance }

        val payload = accounts.map { account ->
            AccountSyncDto(
                id = account.id,
                groupName = account.groupName,
                accountName = account.accountName,
                initialBalance = account.initialBalance,
                initialBalanceDate = account.initialBalanceDate,
                currentBalance = balancesByAccountId[account.id] ?: account.initialBalance,
                isHidden = account.isHidden,
                displayOrder = account.displayOrder,
                description = account.description,
                includeInTotals = account.includeInTotals
            )
        }

        val response = apiService.syncRecords(
            scriptUrl,
            SyncRequest(action = "backup", target = "accounts", records = payload)
        )
        val body = response.body()
        if (response.isSuccessful && body?.status.equals("ok", ignoreCase = true)) {
            Log.i(TAG, "Account backup successful. count=${payload.size}")
            return payload.size
        }

        lastSyncError = "Account backup failed (HTTP ${response.code()}): ${body?.message ?: "unknown error"}"
        Log.w(TAG, "Account backup failed: HTTP ${response.code()}, status=${body?.status}, message=${body?.message}")
        return null
    }

    /**
     * Backs up salary cycles, buckets and their categories. Returns the number of cycles sent, 0 when
     * there is nothing to send, [SCRIPT_OUTDATED] when the deployed script does not know the target,
     * or null when the request genuinely failed.
     *
     * An outdated script must not fail the whole sync, which would stop transactions syncing for
     * anyone who updated the app but has not redeployed the script. Cycles go in `cycles` with
     * `records` empty, so an old script has nothing to file as transactions.
     */
    private suspend fun backupBucketBudgets(scriptUrl: String): Int? {
        val payload = BucketBackupMapper.toSyncDtos(bucketBudgetRepository.getBackupSnapshot())
        if (payload.isEmpty()) {
            Log.i(TAG, "Skipping bucket backup: local payload is empty")
            return 0
        }

        val response = apiService.syncRecords(
            scriptUrl,
            SyncRequest(action = "backup", target = "bucket_budgets", records = emptyList(), cycles = payload)
        )
        val body = response.body()
        if (response.isSuccessful && body?.status.equals("ok", ignoreCase = true)) {
            if (body?.type == BUCKET_BACKUP_TYPE) {
                Log.i(TAG, "Bucket backup successful. cycles=${payload.size}")
                return payload.size
            }
            Log.w(TAG, "Bucket backup skipped: the deployed Apps Script predates bucket budgets and needs redeploying")
            return SCRIPT_OUTDATED
        }

        lastSyncError = "Bucket backup failed (HTTP ${response.code()}): ${body?.message ?: "unknown error"}"
        Log.w(TAG, "Bucket backup failed: HTTP ${response.code()}, status=${body?.status}, message=${body?.message}")
        return null
    }

    companion object {
        const val TAG = "BackupWorker"
        const val WORK_NAME = "BackupWorker"
        const val KEY_DROPDOWN_BACKUP_COUNT = "dropdownBackupCount"
        const val KEY_BUDGET_BACKUP_COUNT = "budgetBackupCount"
        const val KEY_ACCOUNTS_BACKUP_COUNT = "accountsBackupCount"
        const val KEY_BUCKET_BACKUP_COUNT = "bucketBackupCount"

        /** Reported instead of a count when the deployed script does not understand bucket budgets. */
        const val SCRIPT_OUTDATED = -1
        private const val BUCKET_BACKUP_TYPE = "bucket_budgets_backed_up"
        const val KEY_ERROR_MESSAGE = "errorMessage"
        private const val MAX_ATTEMPTS = 3
    }
}
