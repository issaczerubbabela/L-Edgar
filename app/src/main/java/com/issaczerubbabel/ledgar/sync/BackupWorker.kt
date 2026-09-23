package com.issaczerubbabel.ledgar.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.issaczerubbabel.ledgar.data.preferences.ThemePreferenceRepository
import com.issaczerubbabel.ledgar.data.remote.AccountSyncDto
import com.issaczerubbabel.ledgar.data.remote.ApiService
import com.issaczerubbabel.ledgar.data.remote.BudgetSyncDto
import com.issaczerubbabel.ledgar.data.remote.DropdownSyncDto
import com.issaczerubbabel.ledgar.data.remote.SyncRequest
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
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
    private val apiService: ApiService,
    private val preferenceRepository: ThemePreferenceRepository
) : CoroutineWorker(context, workerParams) {

    private var lastSyncError: String? = null

    override suspend fun doWork(): Result {
        val scriptUrl = preferenceRepository.scriptUrl.first()
            ?: return Result.failure(workDataOf(KEY_ERROR_MESSAGE to SyncUrlNotConfiguredException().message))
        return try {
            val accounts = accountRepository.getAllAccountsSnapshot()
            val accountCount = backupAccounts(accounts, scriptUrl)
            val dropdownCount = accountCount?.let { backupDropdownOptions(scriptUrl) }
            val budgetCount = dropdownCount?.let { backupBudgets(scriptUrl) }
            if (accountCount == null || dropdownCount == null || budgetCount == null) {
                retryOrFail(lastSyncError ?: "Backup failed")
            } else {
                Log.i(TAG, "Backup successful. accounts=$accountCount dropdowns=$dropdownCount budgets=$budgetCount")
                Result.success(
                    workDataOf(
                        KEY_ACCOUNTS_BACKUP_COUNT to accountCount,
                        KEY_DROPDOWN_BACKUP_COUNT to dropdownCount,
                        KEY_BUDGET_BACKUP_COUNT to budgetCount
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

    companion object {
        const val TAG = "BackupWorker"
        const val WORK_NAME = "BackupWorker"
        const val KEY_DROPDOWN_BACKUP_COUNT = "dropdownBackupCount"
        const val KEY_BUDGET_BACKUP_COUNT = "budgetBackupCount"
        const val KEY_ACCOUNTS_BACKUP_COUNT = "accountsBackupCount"
        const val KEY_ERROR_MESSAGE = "errorMessage"
        private const val MAX_ATTEMPTS = 3
    }
}
