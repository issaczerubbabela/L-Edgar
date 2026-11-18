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
 * Replaces the Sheet's Accounts, Dropdown options and Budgets tabs with the phone's current lists,
 * unless a tab changed in the Sheet since the last Backup (ADR-0010).
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
    private val preferenceRepository: ThemePreferenceRepository,
    private val listsMerger: SheetListsMerger,
    private val listBackup: ListBackup
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
                displayOrder = option.displayOrder,
                role = option.role
            )
        }

        if (payload.isEmpty()) {
            Log.i(TAG, "Skipping dropdown backup: local payload is empty")
            return 0
        }

        return countOf(listBackup.send(scriptUrl, SyncRequest(action = "backup", target = "dropdowns", records = payload)), payload.size)
    }

    /** The number written, [SHEET_CHANGED] or [SCRIPT_OUTDATED], or null when the request genuinely failed. */
    private fun countOf(outcome: ListBackup.Outcome, sent: Int): Int? = when (outcome) {
        ListBackup.Outcome.Written -> sent
        ListBackup.Outcome.SheetChanged -> {
            Log.w(TAG, "Backup held: the Sheet's tab changed since the last Backup, waiting for the user")
            SHEET_CHANGED
        }
        ListBackup.Outcome.ScriptOutdated -> {
            Log.w(TAG, "Backup skipped: the deployed Apps Script predates this target and needs redeploying")
            SCRIPT_OUTDATED
        }
        is ListBackup.Outcome.Failed -> {
            lastSyncError = outcome.message
            Log.w(TAG, outcome.message)
            null
        }
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

        return countOf(listBackup.send(scriptUrl, SyncRequest(action = "backup", target = "budgets", records = payload)), payload.size)
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
                includeInTotals = account.includeInTotals,
                reconciledAt = account.reconciledAt
            )
        }

        return countOf(listBackup.send(scriptUrl, SyncRequest(action = "backup", target = "accounts", records = payload)), payload.size)
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

        val request = SyncRequest(action = "backup", target = "bucket_budgets", records = emptyList(), cycles = payload)
        return countOf(listBackup.send(scriptUrl, request, expectedType = BUCKET_BACKUP_TYPE), payload.size)
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

        /** Reported instead of a count when the Sheet's tab changed since the last Backup (ADR-0010). */
        const val SHEET_CHANGED = -2
        private const val BUCKET_BACKUP_TYPE = "bucket_budgets_backed_up"
        const val KEY_ERROR_MESSAGE = "errorMessage"
        private const val MAX_ATTEMPTS = 3
    }
}
