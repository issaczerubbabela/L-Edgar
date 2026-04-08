package com.sheetsync.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sheetsync.data.preferences.ThemePreferenceRepository
import com.sheetsync.data.remote.ApiService
import com.sheetsync.data.remote.AccountSyncDto
import com.sheetsync.data.remote.BudgetSyncDto
import com.sheetsync.data.remote.DropdownSyncDto
import com.sheetsync.data.remote.SyncRequest
import com.sheetsync.data.repository.AccountRepository
import com.sheetsync.data.repository.BudgetRepository
import com.sheetsync.data.repository.DropdownOptionRepository
import com.sheetsync.data.repository.ExpenseRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.sheetsync.data.remote.SyncRecordDto
import kotlinx.coroutines.flow.first

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ExpenseRepository,
    private val accountRepository: AccountRepository,
    private val dropdownOptionRepository: DropdownOptionRepository,
    private val budgetRepository: BudgetRepository,
    private val apiService: ApiService,
    private val preferenceRepository: ThemePreferenceRepository
) : CoroutineWorker(context, workerParams) {

    private var lastSyncError: String? = null

    override suspend fun doWork(): Result {
        return try {
            val scriptUrl = preferenceRepository.scriptUrl.first()
                ?: throw SyncUrlNotConfiguredException()
            val backupOnly = inputData.getBoolean(KEY_BACKUP_ONLY, false)

            val accounts = accountRepository.getAllAccountsSnapshot()
            val accountNameById = accounts.associate { it.id to it.accountName }

            val unsynced = repository.getUnsynced()

            val accountsBackupCount = backupAccounts(accounts, scriptUrl) ?: run {
                return Result.failure(workDataOf(KEY_ERROR_MESSAGE to (lastSyncError ?: "Account backup failed")))
            }

            val dropdownBackupCount = backupDropdownOptions(scriptUrl) ?: run {
                return Result.failure(workDataOf(KEY_ERROR_MESSAGE to (lastSyncError ?: "Dropdown backup failed")))
            }

            val budgetBackupCount = backupBudgets(scriptUrl) ?: run {
                return Result.failure(workDataOf(KEY_ERROR_MESSAGE to (lastSyncError ?: "Budget backup failed")))
            }

            if (backupOnly) {
                Log.i(TAG, "Backup-only sync completed. skippedTransactionSync=true")
            } else if (unsynced.isEmpty()) {
                Log.i(TAG, "No transaction changes to sync.")
            } else {
                Log.i(TAG, "Syncing ${unsynced.size} records to configured Apps Script URL")

                val byAction = unsynced.groupBy { it.syncAction.uppercase() }

                val inserts = byAction["INSERT"].orEmpty()
                if (inserts.isNotEmpty() && !syncRecords("insert", inserts, accountNameById, scriptUrl)) {
                    return Result.retry()
                }

                val updates = byAction["UPDATE"].orEmpty()
                if (updates.isNotEmpty() && !syncRecords("update", updates, accountNameById, scriptUrl)) {
                    return Result.retry()
                }

                val deletes = byAction["DELETE"].orEmpty()
                if (deletes.isNotEmpty() && !syncDeletes(deletes, scriptUrl)) {
                    return Result.retry()
                }
            }

            Log.i(TAG, "Sync successful. processed=${unsynced.size}")
            Result.success(
                workDataOf(
                    KEY_DROPDOWN_BACKUP_COUNT to dropdownBackupCount,
                    KEY_BUDGET_BACKUP_COUNT to budgetBackupCount,
                    KEY_ACCOUNTS_BACKUP_COUNT to accountsBackupCount
                )
            )
        } catch (e: SyncUrlNotConfiguredException) {
            Log.w(TAG, e.message ?: "Sync URL not configured")
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Sync URL not configured")))
        } catch (e: Exception) {
            Log.e(TAG, "Sync exception", e)
            Result.retry()
        }
    }

    private suspend fun syncRecords(
        action: String,
        records: List<com.sheetsync.data.local.entity.ExpenseRecord>,
        accountNameById: Map<Long, String>,
        scriptUrl: String
    ): Boolean {
        val dtos = records.map { r ->
            val resolvedAccountName = when (r.type) {
                "Expense", "Income" -> r.accountId?.let { accountNameById[it] }.orEmpty()
                "Transfer" -> {
                    val from = r.fromAccountId?.let { accountNameById[it] }.orEmpty()
                    val to = r.toAccountId?.let { accountNameById[it] }.orEmpty()
                    listOf(from, to).filter { it.isNotBlank() }.joinToString(" -> ")
                }
                else -> ""
            }

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
                accountName = resolvedAccountName,
                remarks = r.remarks,
                isBookmarked = r.isBookmarked
            )
        }

        val response = apiService.syncRecords(
            scriptUrl,
            SyncRequest(action = action, target = "transactions", records = dtos)
        )
        val body = response.body()

        if (response.isSuccessful && body?.status.equals("ok", ignoreCase = true)) {
            repository.markSynced(records.map { it.id })
            return true
        }

        lastSyncError = "${action.uppercase()} transaction sync failed (HTTP ${response.code()}): ${body?.message ?: "unknown error"}"
        Log.w(TAG, "${action.uppercase()} sync failed: HTTP ${response.code()}, status=${body?.status}, message=${body?.message}")
        return false
    }

    private suspend fun syncDeletes(
        records: List<com.sheetsync.data.local.entity.ExpenseRecord>,
        scriptUrl: String
    ): Boolean {
        records.forEach { record ->
            val ts = record.remoteTimestamp
            if (ts.isNullOrBlank()) {
                // Local-only transaction that was never created remotely.
                repository.hardDeleteById(record.id)
                return@forEach
            }

            val response = apiService.syncRecords(
                scriptUrl,
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
                lastSyncError = "DELETE transaction sync failed (HTTP ${response.code()}): ${body?.message ?: "unknown error"}"
                Log.w(TAG, "DELETE sync failed: id=${record.id}, ts=$ts, code=${response.code()}, status=${body?.status}, message=${body?.message}")
                return false
            }
        }
        return true
    }

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
        accounts: List<com.sheetsync.data.local.entity.AccountRecord>,
        scriptUrl: String
    ): Int? {
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
        const val TAG = "SyncWorker"
        const val KEY_BACKUP_ONLY = "backupOnly"
        const val KEY_BACKUP_ACCOUNTS = "backupAccounts"
        const val KEY_DROPDOWN_BACKUP_COUNT = "dropdownBackupCount"
        const val KEY_BUDGET_BACKUP_COUNT = "budgetBackupCount"
        const val KEY_ACCOUNTS_BACKUP_COUNT = "accountsBackupCount"
        const val KEY_ERROR_MESSAGE = "errorMessage"
    }
}
