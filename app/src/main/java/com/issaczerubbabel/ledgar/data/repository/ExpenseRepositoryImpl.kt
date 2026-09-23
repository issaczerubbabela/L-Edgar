package com.issaczerubbabel.ledgar.data.repository

import android.util.Log
import com.issaczerubbabel.ledgar.data.local.dao.AccountDao
import com.issaczerubbabel.ledgar.data.local.dao.BudgetDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.entity.Budget
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.google.gson.Gson
import com.issaczerubbabel.ledgar.data.preferences.SyncStateRepository
import com.issaczerubbabel.ledgar.data.preferences.ThemePreferenceRepository
import com.issaczerubbabel.ledgar.data.remote.ApiService
import com.issaczerubbabel.ledgar.data.remote.ImportRecordDto
import com.issaczerubbabel.ledgar.sync.MergeStep
import com.issaczerubbabel.ledgar.sync.PulledRow
import com.issaczerubbabel.ledgar.sync.SyncUrlNotConfiguredException
import com.issaczerubbabel.ledgar.sync.TransactionSyncStore
import com.issaczerubbabel.ledgar.sync.TransactionSyncer
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject

class ExpenseRepositoryImpl @Inject constructor(
    private val dao: ExpenseDao,
    private val accountDao: AccountDao,
    private val apiService: ApiService,
    private val dropdownOptionRepository: DropdownOptionRepository,
    private val budgetDao: BudgetDao,
    private val preferenceRepository: ThemePreferenceRepository,
    private val transactionSyncer: TransactionSyncer,
    private val syncStore: TransactionSyncStore,
    private val syncState: SyncStateRepository
) : ExpenseRepository {

    private val importLogTag = "ExpenseImport"
    private val gson = Gson()

    override suspend fun save(record: ExpenseRecord): Long = dao.insert(record)

    override suspend fun getById(id: Long): ExpenseRecord? = dao.getById(id)

    override suspend fun update(record: ExpenseRecord) = dao.updateKeepingSyncState(record)

    override suspend fun hardDeleteById(id: Long) = dao.hardDeleteById(id)

    override fun getAllRecords(): Flow<List<ExpenseRecord>> = dao.getAllRecords()

    override fun getBookmarkedTransactions(): Flow<List<ExpenseRecord>> = dao.getBookmarkedTransactions()

    override fun getByType(type: String): Flow<List<ExpenseRecord>> = dao.getByType(type)

    override fun getRecordsForAccount(accountId: Long): Flow<List<ExpenseRecord>> = dao.getRecordsForAccount(accountId)

    override fun searchTransactions(
        query: String?,
        startDate: String?,
        endDate: String?,
        accountId: Long?,
        category: String?,
        minAmount: Double?,
        maxAmount: Double?
    ): Flow<List<ExpenseRecord>> = dao.searchTransactions(
        query = query,
        startDate = startDate,
        endDate = endDate,
        accountId = accountId,
        category = category,
        minAmount = minAmount,
        maxAmount = maxAmount
    )

    override fun getRecordsForAccountInMonth(accountId: Long, startDate: String, endDate: String): Flow<List<ExpenseRecord>> =
        dao.getRecordsForAccountInMonth(accountId = accountId, startDate = startDate, endDate = endDate)

    override fun getTransactionsForAccountInMonth(accountId: Long, startOfMonth: String, endOfMonth: String): Flow<List<ExpenseRecord>> =
        dao.getTransactionsForAccountInMonth(accountId = accountId, startDate = startOfMonth, endDate = endOfMonth)

    override fun getHistoricalSumForAccount(accountId: Long, beforeDate: String): Flow<Double?> =
        dao.getHistoricalSumForAccount(accountId = accountId, beforeDate = beforeDate)

    override fun getAccountBalanceUntilDate(accountId: Long, endDate: String): Flow<Double> =
        dao.getAccountBalanceUntilDate(accountId = accountId, endDate = endDate)

    override fun getAccountBalance(accountId: Long): Flow<Double> = dao.getAccountBalance(accountId)

    override fun getRecordsByDateRange(startDate: String, endDate: String): Flow<List<ExpenseRecord>> =
        dao.getRecordsByDateRange(startDate, endDate)

    override suspend fun setBookmarked(id: Long, isBookmarked: Boolean) =
        dao.updateBookmarkStatus(id = id, isBookmarked = isBookmarked)

    override suspend fun deleteTransactionsByIds(ids: List<Long>) = dao.markTransactionsDeletedByIds(ids)

    override suspend fun updateTransactionsDateByIds(ids: List<Long>, newDate: String) =
        dao.updateTransactionsDateByIds(ids = ids, newDate = newDate)

    override suspend fun updateTransactionsCategoryByIds(ids: List<Long>, newCategory: String) =
        dao.updateTransactionsCategoryByIds(ids = ids, newCategory = newCategory)

    override suspend fun updateTransactionsAssetByIds(ids: List<Long>, accountId: Long) =
        dao.updateTransactionsAssetByIds(ids = ids, accountId = accountId)

    override suspend fun updateTransactionsDescriptionByIds(ids: List<Long>, newDescription: String) =
        dao.updateTransactionsDescriptionByIds(ids = ids, newDescription = newDescription)

    override suspend fun delete(record: ExpenseRecord) = dao.markTransactionDeletedById(record.id)

    override suspend fun deleteAll() = dao.deleteAll()

    override suspend fun isDuplicate(date: String, type: String, category: String, amount: Double): Boolean =
        dao.findDuplicate(date, type, category, amount) != null

    override suspend fun importFromGoogleSheets(): GoogleSheetsImportResult {
        val scriptUrl = preferenceRepository.scriptUrl.first()
            ?: throw SyncUrlNotConfiguredException()

        val dropdownResponse = apiService.importDropdownOptions(
            url = scriptUrl,
            target = "dropdowns"
        )
        if (!dropdownResponse.isSuccessful) {
            throw IllegalStateException("Dropdown import failed: HTTP ${dropdownResponse.code()}")
        }

        val dropdownBody = dropdownResponse.body()
        if (!dropdownBody?.status.equals("ok", ignoreCase = true)) {
            throw IllegalStateException(dropdownBody?.message ?: "Dropdown import failed")
        }

        val restoredDropdowns = dropdownBody?.data.orEmpty().let { remoteDropdowns ->
            val filteredDropdowns = remoteDropdowns.filterNot { it.optionType == "PAYMENT_MODE" }
            val mapped = filteredDropdowns.map { dto ->
                DropdownOption(
                    id = 0,
                    optionType = dto.optionType,
                    name = dto.name,
                    displayOrder = dto.displayOrder
                )
            }
            dropdownOptionRepository.overwriteAllOptions(mapped)
            mapped.size
        }

        val accountsResponse = apiService.importAccounts(
            url = scriptUrl,
            target = "accounts"
        )
        if (!accountsResponse.isSuccessful) {
            throw IllegalStateException("Account import failed: HTTP ${accountsResponse.code()}")
        }

        val accountsBody = accountsResponse.body()
        if (!accountsBody?.status.equals("ok", ignoreCase = true)) {
            throw IllegalStateException(accountsBody?.message ?: "Account import failed")
        }

        val restoredAccounts = accountsBody?.data.orEmpty().let { remoteAccounts ->
            val mapped = remoteAccounts.mapIndexed { index, dto ->
                AccountRecord(
                    id = 0,
                    groupName = dto.groupName,
                    accountName = dto.accountName,
                    initialBalance = dto.initialBalance,
                    initialBalanceDate = dto.initialBalanceDate.ifBlank { "1970-01-01" },
                    isHidden = dto.isHidden,
                    displayOrder = dto.displayOrder ?: index,
                    description = dto.description,
                    includeInTotals = dto.includeInTotals
                )
            }
            accountDao.overwriteAll(mapped)
            mapped.size
        }

        val budgetResponse = apiService.importBudgets(
            url = scriptUrl,
            target = "budgets"
        )
        if (!budgetResponse.isSuccessful) {
            throw IllegalStateException("Budget import failed: HTTP ${budgetResponse.code()}")
        }

        val budgetBody = budgetResponse.body()
        if (!budgetBody?.status.equals("ok", ignoreCase = true)) {
            throw IllegalStateException(budgetBody?.message ?: "Budget import failed")
        }

        val restoredBudgets = budgetBody?.data.orEmpty().let { remoteBudgets ->
            val mapped = remoteBudgets.mapNotNull { dto ->
                val normalizedMonthYear = normalizeBudgetMonthYear(dto.monthYear)
                if (normalizedMonthYear == null || dto.category.isBlank()) {
                    Log.w(importLogTag, "Skipping malformed budget row from Sheets: monthYear='${dto.monthYear}', category='${dto.category}'")
                    null
                } else {
                    Budget(
                        id = 0,
                        monthYear = normalizedMonthYear,
                        category = dto.category,
                        amount = dto.amount
                    )
                }
            }
            if (mapped.isNotEmpty()) {
                budgetDao.clearAll()
                budgetDao.insertAll(mapped)
            } else if (remoteBudgets.isNotEmpty()) {
                // Remote contained only malformed rows; clear stale local data to avoid mixed schemas.
                budgetDao.clearAll()
            }
            mapped.size
        }

        // The phone's lists now match the Sheet's, so Backups can safely write them back.
        syncState.setMergedListsFromSheet()

        // Transactions come in through the same Pull as every Sync, keyed by Transaction ID.
        val imported = when (val outcome = transactionSyncer.sync(scriptUrl, pull = true)) {
            is TransactionSyncer.Outcome.Synced -> {
                outcome.heldDeletes?.let { syncState.setHeldSheetDeletions(it) }
                outcome.pulledChanges
            }
            is TransactionSyncer.Outcome.Failed -> throw IllegalStateException(outcome.message)
            TransactionSyncer.Outcome.ScriptOutdated -> throw IllegalStateException(SCRIPT_OUTDATED_MESSAGE)
        }

        return GoogleSheetsImportResult(
            imported = imported,
            skipped = 0,
            restoredDropdowns = restoredDropdowns,
            restoredBudgets = restoredBudgets,
            restoredAccounts = restoredAccounts,
            conflicts = dao.observeConflicts().first().map(::toConflict)
        )
    }

    override fun observeSyncConflicts(): Flow<List<SyncConflict>> =
        dao.observeConflicts().map { rows -> rows.map(::toConflict) }

    private fun toConflict(record: ExpenseRecord) = SyncConflict(
        localTx = record,
        sheetTx = gson.fromJson(record.sheetConflictJson, ImportRecordDto::class.java)
    )

    /** The phone's version wins: it goes up next Sync, over the Sheet's. */
    override suspend fun resolveConflictKeepPhone(conflict: SyncConflict) {
        dao.resolveConflictKeepingPhone(conflict.localTx.id, conflict.sheetTx.revision)
    }

    override suspend fun resolveConflictKeepSheet(conflict: SyncConflict) {
        val current = dao.getById(conflict.localTx.id) ?: return
        val syncId = current.syncId ?: return
        syncStore.apply(listOf(MergeStep.ApplyFromSheet(current.id, current.localVersion, PulledRow(syncId, conflict.sheetTx))))
    }

    /** The Sheet's version stays on this Transaction; the phone's becomes a new one. */
    override suspend fun resolveConflictKeepBoth(conflict: SyncConflict) {
        val phoneVersion = dao.getById(conflict.localTx.id) ?: return
        resolveConflictKeepSheet(conflict)
        dao.insert(
            phoneVersion.copy(
                id = 0,
                syncId = null,
                remoteTimestamp = null,
                isSynced = false,
                syncAction = "INSERT",
                localVersion = 0,
                syncedRevision = null,
                sheetConflictJson = null
            )
        )
    }

    override suspend fun resolveConflictDeleteEverywhere(conflict: SyncConflict) {
        dao.clearConflictAndMarkDeleted(conflict.localTx.id)
    }

    override suspend fun keepTransactionsMissingFromSheet(ids: Collection<Long>) {
        if (ids.isNotEmpty()) dao.markForReupload(ids.toList())
        syncState.setHeldSheetDeletions(emptyList())
    }

    override fun observePossibleDuplicates(): Flow<List<List<ExpenseRecord>>> =
        dao.getAllRecords().map { records ->
            records
                .groupBy { record ->
                    listOf(
                        record.date,
                        record.type.trim().lowercase(),
                        record.category.trim().lowercase(),
                        "%.2f".format(java.util.Locale.ROOT, record.amount),
                        record.description.trim().lowercase(),
                        record.accountId, record.fromAccountId, record.toAccountId
                    )
                }
                .values
                .filter { it.size > 1 }
                .sortedByDescending { it.first().date }
        }

    private fun normalizeBudgetMonthYear(rawValue: String): String? {
        val raw = rawValue.trim()
        if (raw.isBlank()) return null

        runCatching { YearMonth.parse(raw, MONTH_YEAR_FORMATTER) }
            .getOrNull()
            ?.let { return it.format(MONTH_YEAR_FORMATTER) }

        for (pattern in LEGACY_MONTH_YEAR_PATTERNS) {
            val parsed = try {
                YearMonth.parse(raw, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
            } catch (_: DateTimeParseException) {
                null
            }
            if (parsed != null) {
                return parsed.format(MONTH_YEAR_FORMATTER)
            }
        }

        parseFlexibleDate(raw)?.let { parsedDate ->
            return YearMonth.from(parsedDate).format(MONTH_YEAR_FORMATTER)
        }

        return null
    }

    companion object {
        const val SCRIPT_OUTDATED_MESSAGE =
            "Your Apps Script is out of date. Open Database Setup, copy the new script into Apps Script and deploy a new version."
        private val MONTH_YEAR_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
        private val LEGACY_MONTH_YEAR_PATTERNS = listOf("MMM yyyy", "MMMM yyyy", "MM/yyyy", "M/yyyy", "yyyy/MM", "yyyy/M")
    }
}
