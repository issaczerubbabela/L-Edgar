package com.issaczerubbabel.ledgar.sync

import androidx.room.withTransaction
import com.issaczerubbabel.ledgar.data.bucket.BucketBackupMapper
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.Budget
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.preferences.SyncStateRepository
import com.issaczerubbabel.ledgar.data.remote.ApiService
import com.issaczerubbabel.ledgar.data.repository.BucketBudgetRepository
import retrofit2.Response
import javax.inject.Inject

/**
 * A Backup replaces the Sheet's Accounts, Dropdown options, Budgets and salary-cycle tabs with the phone's lists.
 * On a fresh install those are empty or defaults, so the first Backup would wipe the Sheet's real
 * lists. Before this phone's first Backup, this adds whatever the Sheet has that the phone doesn't
 * (matched by name), so every Backup after it writes back at least what the Sheet had.
 */
class SheetListsMerger @Inject constructor(
    private val api: ApiService,
    private val database: SheetSyncDatabase,
    private val bucketBudgets: BucketBudgetRepository,
    private val syncState: SyncStateRepository
) {
    suspend fun mergeOnce(scriptUrl: String) {
        if (syncState.hasMergedListsFromSheet()) return

        val sheetAccounts = api.importAccounts(scriptUrl).dataOrThrow("Accounts") { it.status to it.data }
        val sheetDropdowns = api.importDropdownOptions(scriptUrl).dataOrThrow("Dropdown options") { it.status to it.data }
        val sheetBudgets = api.importBudgets(scriptUrl).dataOrThrow("Budgets") { it.status to it.data }
        val sheetCycles = api.importBucketBudgets(scriptUrl).let { response ->
            val body = response.body()
            check(response.isSuccessful && body?.status.equals("ok", ignoreCase = true)) {
                "Reading salary cycles from the Sheet failed (HTTP ${response.code()})"
            }
            // A script from before bucket budgets answers without the marker: nothing to take in.
            if (body?.isUnderstoodByScript == true) BucketBackupMapper.fromImportDtos(body.data.orEmpty()) else emptyList()
        }

        database.withTransaction {
            val accountDao = database.accountDao()
            val localAccounts = accountDao.getAllAccountsSnapshot()
            val accountNames = localAccounts.map { it.accountName.key() }.toMutableSet()
            var nextOrder = (localAccounts.maxOfOrNull { it.displayOrder } ?: -1) + 1
            sheetAccounts.filter { it.accountName.isNotBlank() && accountNames.add(it.accountName.key()) }.forEach { dto ->
                accountDao.insert(
                    AccountRecord(
                        groupName = dto.groupName,
                        accountName = dto.accountName,
                        initialBalance = dto.initialBalance,
                        initialBalanceDate = dto.initialBalanceDate.ifBlank { "1970-01-01" },
                        isHidden = dto.isHidden,
                        displayOrder = nextOrder++,
                        description = dto.description,
                        includeInTotals = dto.includeInTotals
                    )
                )
            }

            val dropdownDao = database.dropdownOptionDao()
            val localOptions = dropdownDao.getAllOptionsSnapshot().map { it.optionType to it.name.key() }.toMutableSet()
            sheetDropdowns
                .filter { it.optionType != "PAYMENT_MODE" && it.name.isNotBlank() && localOptions.add(it.optionType to it.name.key()) }
                .forEach { dto -> dropdownDao.insert(DropdownOption(optionType = dto.optionType, name = dto.name, displayOrder = dto.displayOrder)) }

            val budgetDao = database.budgetDao()
            val localBudgets = budgetDao.getAllBudgetsSnapshot().map { it.monthYear to it.category.key() }.toMutableSet()
            sheetBudgets
                .filter { it.monthYear.isNotBlank() && it.category.isNotBlank() && localBudgets.add(it.monthYear to it.category.key()) }
                .forEach { dto -> budgetDao.upsert(Budget(monthYear = dto.monthYear, category = dto.category, amount = dto.amount)) }
        }
        // Salary cycles can't be matched up by name, so they only come in when the phone has none.
        if (sheetCycles.isNotEmpty() && bucketBudgets.getBackupSnapshot().cycles.isEmpty()) {
            bucketBudgets.replaceAllFromBackup(sheetCycles)
        }
        syncState.setMergedListsFromSheet()
    }

    private fun String.key() = trim().lowercase()

    private inline fun <B, T> Response<B>.dataOrThrow(what: String, unpack: (B) -> Pair<String?, List<T>?>): List<T> {
        val body = body()
        val (status, data) = body?.let(unpack) ?: (null to null)
        check(isSuccessful && status.equals("ok", ignoreCase = true)) { "Reading $what from the Sheet failed (HTTP ${code()})" }
        return data.orEmpty()
    }
}
