package com.issaczerubbabel.ledgar.data.repository

import android.util.Log
import com.issaczerubbabel.ledgar.data.local.dao.AccountDao
import com.issaczerubbabel.ledgar.data.local.dao.BudgetDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.entity.Budget
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.preferences.ThemePreferenceRepository
import com.issaczerubbabel.ledgar.data.remote.ApiService
import com.issaczerubbabel.ledgar.data.remote.ImportRecordDto
import com.issaczerubbabel.ledgar.data.remote.SyncRequest
import com.issaczerubbabel.ledgar.sync.SyncUrlNotConfiguredException
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import com.issaczerubbabel.ledgar.util.normalizeTimestampKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    private val preferenceRepository: ThemePreferenceRepository
) : ExpenseRepository {

    private val importLogTag = "ExpenseImport"

    override suspend fun save(record: ExpenseRecord): Long = dao.insert(record)

    override suspend fun getById(id: Long): ExpenseRecord? = dao.getById(id)

    override suspend fun update(record: ExpenseRecord) = dao.update(record)

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

    override suspend fun getUnsynced(): List<ExpenseRecord> = dao.getUnsyncedRecords()

    override suspend fun markSynced(ids: List<Long>) = dao.markAsSynced(ids)

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

    override suspend fun importRemoteRecords(records: List<ImportRecordDto>): Int =
        importRemoteRecordsInternal(records).imported

    private suspend fun importRemoteRecordsInternal(records: List<ImportRecordDto>): ImportRemoteRecordsOutcome {
        repairLegacyRecords()
        if (records.isEmpty()) return ImportRemoteRecordsOutcome(imported = 0, duplicates = emptyList())

        val initialAccounts = accountDao.getAllAccountsSnapshot().toMutableList()
        if (initialAccounts.isEmpty()) {
            val fallbackId = accountDao.insert(
                com.issaczerubbabel.ledgar.data.local.entity.AccountRecord(
                    groupName = "Cash",
                    accountName = "Cash",
                    initialBalance = 0.0,
                    initialBalanceDate = "1970-01-01",
                    isHidden = false
                )
            )
            initialAccounts += com.issaczerubbabel.ledgar.data.local.entity.AccountRecord(
                id = fallbackId,
                groupName = "Cash",
                accountName = "Cash",
                initialBalance = 0.0,
                initialBalanceDate = "1970-01-01",
                isHidden = false
            )
        }

        val accountsByName = initialAccounts.associateBy { normalizeAccountKey(it.accountName) }
        val accountsByGroup = initialAccounts.associateBy { normalizeAccountKey(it.groupName) }
        val fallbackAccountId = initialAccounts
            .firstOrNull { it.accountName.equals("Cash", ignoreCase = true) || it.groupName.equals("Cash", ignoreCase = true) }
            ?.id
            ?: initialAccounts.firstOrNull()?.id

        val localSnapshot = dao.getAllRecordsSnapshot()

        val localComparable = localSnapshot.map { local ->
            ComparableTx(
                date = normalizeDate(local.date, local.remoteTimestamp),
                amount = local.amount,
                type = canonicalType(local.type),
                description = local.description,
                accountId = local.accountId
            )
        }.toMutableList()
        val localRemoteTimestamps = dao.getAllRemoteTimestamps()
            .mapNotNull(::normalizeTimestampKey)
            .toMutableSet()
        val localByRemoteTimestamp = localSnapshot
            .filter { !it.remoteTimestamp.isNullOrBlank() && it.syncAction != "DELETE" }
            .mapNotNull { record ->
                normalizeTimestampKey(record.remoteTimestamp)?.let { normalizedTs -> normalizedTs to record }
            }
            .toMap()
            .toMutableMap()

        val toInsert = mutableListOf<ExpenseRecord>()
        val duplicateCandidates = mutableListOf<SkippedDuplicateCandidate>()

        var unexpectedDateLogCount = 0
        records.forEachIndexed { index, dto ->
            val resolvedType = canonicalType(dto.type)
            val resolvedDate = normalizeDate(dto.date, dto.timestamp)
            if (parseFlexibleDate(resolvedDate) == null && unexpectedDateLogCount < 5) {
                Log.w(
                    importLogTag,
                    "Unparseable imported date. rawDate='${dto.date}', rawTimestamp='${dto.timestamp}', normalized='$resolvedDate', type='${dto.type}'"
                )
                unexpectedDateLogCount++
            }
            val mappedCategoryRaw = when {
                resolvedType.equals("Expense", ignoreCase = true) -> dto.expCategory
                resolvedType.equals("Income", ignoreCase = true) -> dto.incCategory
                else -> dto.expCategory ?: dto.incCategory
            }
            val mappedCategory = mappedCategoryRaw
                ?.trim()
                ?.takeUnless { it.isBlank() }
                ?: if (resolvedType.equals("Transfer", ignoreCase = true)) "Transfer" else ""

            val remoteAccountName = dto.accountName?.trim().takeUnless { it.isNullOrBlank() }
                ?: dto.paymentMode?.trim().takeUnless { it.isNullOrBlank() }
            val legacyTransferParts = remoteAccountName
                ?.split("->")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()

            val explicitFromName = dto.fromAccountName?.trim().takeUnless { it.isNullOrBlank() }
                ?: legacyTransferParts.getOrNull(0)
            val explicitToName = dto.toAccountName?.trim().takeUnless { it.isNullOrBlank() }
                ?: legacyTransferParts.getOrNull(1)

            val fromAccountId = explicitFromName
                ?.let { token ->
                    val key = normalizeAccountKey(token)
                    accountsByName[key]?.id ?: accountsByGroup[key]?.id
                }
            val toAccountId = explicitToName
                ?.let { token ->
                    val key = normalizeAccountKey(token)
                    accountsByName[key]?.id ?: accountsByGroup[key]?.id
                }

            val mappedAccountId = when {
                resolvedType.equals("Transfer", ignoreCase = true) -> null
                remoteAccountName == null -> fallbackAccountId
                else -> {
                    val key = normalizeAccountKey(remoteAccountName)
                    accountsByName[key]?.id ?: accountsByGroup[key]?.id ?: fallbackAccountId
                }
            }

            val fallbackAccountName = remoteAccountName?.trim().takeUnless { it.isNullOrBlank() }
            val fallbackFromName = explicitFromName?.trim().takeUnless { it.isNullOrBlank() }
            val fallbackToName = explicitToName?.trim().takeUnless { it.isNullOrBlank() }
            val timestamp = normalizeTimestampKey(dto.timestamp)

            if (timestamp != null && localRemoteTimestamps.contains(timestamp)) {
                val existing = localByRemoteTimestamp[timestamp]
                if (existing != null) {
                    val updated = existing.copy(
                        accountId = when {
                            resolvedType.equals("Transfer", ignoreCase = true) -> existing.accountId
                            else -> mappedAccountId ?: existing.accountId
                        },
                        fromAccountId = when {
                            resolvedType.equals("Expense", ignoreCase = true) -> mappedAccountId ?: existing.fromAccountId
                            resolvedType.equals("Transfer", ignoreCase = true) -> fromAccountId ?: existing.fromAccountId
                            else -> null
                        },
                        toAccountId = when {
                            resolvedType.equals("Income", ignoreCase = true) -> mappedAccountId ?: existing.toAccountId
                            resolvedType.equals("Transfer", ignoreCase = true) -> toAccountId ?: existing.toAccountId
                            else -> null
                        },
                        accountName = when {
                            resolvedType.equals("Transfer", ignoreCase = true) -> existing.accountName
                            else -> fallbackAccountName ?: existing.accountName
                        },
                        fromAccountName = when {
                            resolvedType.equals("Expense", ignoreCase = true) -> fallbackAccountName ?: existing.fromAccountName
                            resolvedType.equals("Transfer", ignoreCase = true) -> fallbackFromName ?: existing.fromAccountName
                            else -> null
                        },
                        toAccountName = when {
                            resolvedType.equals("Income", ignoreCase = true) -> fallbackAccountName ?: existing.toAccountName
                            resolvedType.equals("Transfer", ignoreCase = true) -> fallbackToName ?: existing.toAccountName
                            else -> null
                        },
                        remarks = dto.remarks.ifBlank { existing.remarks },
                        isBookmarked = dto.isBookmarked ?: existing.isBookmarked,
                        isSynced = true,
                        syncAction = "NONE"
                    )
                    if (updated != existing) {
                        dao.update(updated)
                        localByRemoteTimestamp[timestamp] = updated
                    }
                }
                return@forEachIndexed
            }

            // For non-transfer rows, drop records that cannot be mapped safely.
            if (!resolvedType.equals("Transfer", ignoreCase = true) && mappedAccountId == null) {
                return@forEachIndexed
            }

            val remoteComparable = ComparableTx(
                date = resolvedDate,
                amount = dto.amount,
                type = resolvedType,
                description = dto.description,
                accountId = mappedAccountId
            )

            val isDuplicate = localComparable.any { local -> isCompositeDuplicate(local, remoteComparable) }

            val mappedRecord = ExpenseRecord(
                date = resolvedDate,
                type = resolvedType,
                category = mappedCategory,
                description = dto.description,
                amount = dto.amount,
                accountId = mappedAccountId,
                remarks = dto.remarks,
                isBookmarked = dto.isBookmarked ?: false,
                fromAccountId = when {
                    resolvedType.equals("Expense", ignoreCase = true) -> mappedAccountId
                    resolvedType.equals("Transfer", ignoreCase = true) -> fromAccountId
                    else -> null
                },
                toAccountId = when {
                    resolvedType.equals("Income", ignoreCase = true) -> mappedAccountId
                    resolvedType.equals("Transfer", ignoreCase = true) -> toAccountId
                    else -> null
                },
                accountName = when {
                    resolvedType.equals("Transfer", ignoreCase = true) -> null
                    else -> fallbackAccountName
                },
                fromAccountName = when {
                    resolvedType.equals("Expense", ignoreCase = true) -> fallbackAccountName
                    resolvedType.equals("Transfer", ignoreCase = true) -> fallbackFromName
                    else -> null
                },
                toAccountName = when {
                    resolvedType.equals("Income", ignoreCase = true) -> fallbackAccountName
                    resolvedType.equals("Transfer", ignoreCase = true) -> fallbackToName
                    else -> null
                },
                isSynced = true,
                remoteTimestamp = timestamp,
                syncAction = "NONE"
            )

            if (!isDuplicate) {
                toInsert += mappedRecord
                localComparable += remoteComparable
                timestamp?.let { localRemoteTimestamps += it }
            } else {
                duplicateCandidates += SkippedDuplicateCandidate(
                    id = timestamp ?: "idx-$index-${resolvedType.lowercase()}-${dto.amount}",
                    timestamp = timestamp,
                    date = resolvedDate,
                    type = resolvedType,
                    category = mappedCategory,
                    description = dto.description,
                    amount = dto.amount,
                    accountName = fallbackAccountName ?: "",
                    fromAccountName = fallbackFromName,
                    toAccountName = fallbackToName,
                    remarks = dto.remarks,
                    recordToImport = mappedRecord
                )
            }
        }

        if (toInsert.isNotEmpty()) dao.insertAll(toInsert)
        return ImportRemoteRecordsOutcome(
            imported = toInsert.size,
            duplicates = duplicateCandidates
        )
    }

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

        val txResponse = apiService.importRecords(
            url = scriptUrl,
            target = "transactions"
        )
        if (!txResponse.isSuccessful) {
            throw IllegalStateException("Transaction import failed: HTTP ${txResponse.code()}")
        }

        val txBody = txResponse.body()
        if (!txBody?.status.equals("ok", ignoreCase = true)) {
            throw IllegalStateException(txBody?.message ?: "Transaction import failed")
        }

        val dtos = txBody?.data.orEmpty()
        val importOutcome = importRemoteRecordsInternal(dtos)
        val imported = importOutcome.imported
        val skipped = importOutcome.duplicates.size

        return GoogleSheetsImportResult(
            imported = imported,
            skipped = skipped,
            restoredDropdowns = restoredDropdowns,
            restoredBudgets = restoredBudgets,
            restoredAccounts = restoredAccounts,
            duplicateCandidates = importOutcome.duplicates
        )
    }

    override suspend fun resolveSkippedRemoteDuplicates(
        candidates: List<SkippedDuplicateCandidate>,
        acceptedCandidateIds: Set<String>
    ): DuplicateResolutionResult {
        if (candidates.isEmpty()) {
            return DuplicateResolutionResult(
                acceptedImported = 0,
                skippedDeletedFromSheets = 0,
                skippedDeleteFailed = 0
            )
        }

        val acceptedRecords = candidates
            .filter { acceptedCandidateIds.contains(it.id) }
            .map { it.recordToImport.copy(id = 0) }

        if (acceptedRecords.isNotEmpty()) {
            dao.insertAll(acceptedRecords)
        }

        val scriptUrl = preferenceRepository.scriptUrl.first()
            ?: throw SyncUrlNotConfiguredException()

        var deletedFromSheets = 0
        var deleteFailed = 0

        val skippedCandidates = candidates.filterNot { acceptedCandidateIds.contains(it.id) }
        for (candidate in skippedCandidates) {
            val ts = normalizeTimestampKey(candidate.timestamp)
            if (ts.isNullOrBlank()) {
                deleteFailed++
                continue
            }

            val response = apiService.syncRecords(
                scriptUrl,
                SyncRequest(
                    action = "delete",
                    target = "transactions",
                    targetTimestamp = ts
                )
            )
            val body = response.body()
            val wasDeleted = response.isSuccessful &&
                body?.status.equals("ok", ignoreCase = true) &&
                (body?.count ?: 0) > 0

            if (wasDeleted) {
                deletedFromSheets++
            } else {
                deleteFailed++
            }
        }

        return DuplicateResolutionResult(
            acceptedImported = acceptedRecords.size,
            skippedDeletedFromSheets = deletedFromSheets,
            skippedDeleteFailed = deleteFailed
        )
    }

    private data class ComparableTx(
        val date: String,
        val amount: Double,
        val type: String,
        val description: String,
        val accountId: Long?
    )

    private data class ImportRemoteRecordsOutcome(
        val imported: Int,
        val duplicates: List<SkippedDuplicateCandidate>
    )

    private fun isCompositeDuplicate(local: ComparableTx, remote: ComparableTx): Boolean {
        return local.date == remote.date &&
            amountsEqual(local.amount, remote.amount) &&
            local.description.trim().equals(remote.description.trim(), ignoreCase = true) &&
            local.accountId == remote.accountId
    }

    private fun amountsEqual(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) < 0.000001

    private fun normalizeAccountKey(raw: String): String = raw.trim().lowercase()

    private suspend fun repairLegacyRecords() {
        val snapshot = dao.getAllRecordsSnapshot()
        val normalized = snapshot.map { record ->
            record.copy(
                date = normalizeDate(record.date, record.remoteTimestamp),
                type = canonicalType(record.type),
                remoteTimestamp = normalizeTimestampKey(record.remoteTimestamp),
                syncAction = if (record.isSynced && record.syncAction != "DELETE") "NONE" else record.syncAction
            )
        }
        if (normalized != snapshot) {
            dao.insertAll(normalized)
        }
    }

    private fun canonicalType(rawType: String): String {
        val t = rawType.trim().lowercase()
        return when {
            t == "expense" -> "Expense"
            t == "income" -> "Income"
            t == "transfer" -> "Transfer"
            else -> rawType.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun normalizeDate(rawDate: String, rawTimestamp: String?): String {
        parseFlexibleDate(rawDate)?.let { return it.toString() }
        rawTimestamp?.let { ts ->
            parseFlexibleDate(ts)?.let { return it.toString() }
        }
        return rawDate.trim()
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
        private val MONTH_YEAR_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
        private val LEGACY_MONTH_YEAR_PATTERNS = listOf("MMM yyyy", "MMMM yyyy", "MM/yyyy", "M/yyyy", "yyyy/MM", "yyyy/M")
    }
}
