package com.sheetsync.data.repository

import android.util.Log
import com.sheetsync.BuildConfig
import com.sheetsync.data.local.dao.AccountDao
import com.sheetsync.data.local.dao.BudgetDao
import com.sheetsync.data.local.dao.ExpenseDao
import com.sheetsync.data.local.entity.Budget
import com.sheetsync.data.local.entity.DropdownOption
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.local.entity.AccountRecord
import com.sheetsync.data.remote.ApiService
import com.sheetsync.data.remote.ImportRecordDto
import com.sheetsync.util.parseFlexibleDate
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExpenseRepositoryImpl @Inject constructor(
    private val dao: ExpenseDao,
    private val accountDao: AccountDao,
    private val apiService: ApiService,
    private val dropdownOptionRepository: DropdownOptionRepository,
    private val budgetDao: BudgetDao
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

    override suspend fun delete(record: ExpenseRecord) = dao.delete(record)

    override suspend fun deleteAll() = dao.deleteAll()

    override suspend fun isDuplicate(date: String, type: String, category: String, amount: Double): Boolean =
        dao.findDuplicate(date, type, category, amount) != null

    override suspend fun importRemoteRecords(records: List<ImportRecordDto>): Int {
        repairLegacyRecords()
        if (records.isEmpty()) return 0

        val initialAccounts = accountDao.getAllAccountsSnapshot().toMutableList()
        if (initialAccounts.isEmpty()) {
            val fallbackId = accountDao.insert(
                com.sheetsync.data.local.entity.AccountRecord(
                    groupName = "Cash",
                    accountName = "Cash",
                    initialBalance = 0.0,
                    isHidden = false
                )
            )
            initialAccounts += com.sheetsync.data.local.entity.AccountRecord(
                id = fallbackId,
                groupName = "Cash",
                accountName = "Cash",
                initialBalance = 0.0,
                isHidden = false
            )
        }

        val accountsByName = initialAccounts.associateBy { normalizeAccountKey(it.accountName) }
        val accountsByGroup = initialAccounts.associateBy { normalizeAccountKey(it.groupName) }
        val fallbackAccountId = initialAccounts
            .firstOrNull { it.accountName.equals("Cash", ignoreCase = true) || it.groupName.equals("Cash", ignoreCase = true) }
            ?.id
            ?: initialAccounts.firstOrNull()?.id

        val localComparable = dao.getAllRecordsSnapshot().map { local ->
            ComparableTx(
                date = normalizeDate(local.date, local.remoteTimestamp),
                amount = local.amount,
                type = canonicalType(local.type),
                description = local.description,
                accountId = local.accountId
            )
        }.toMutableList()

        val toInsert = mutableListOf<ExpenseRecord>()

        var unexpectedDateLogCount = 0
        records.forEach { dto ->
            val resolvedType = canonicalType(dto.type)
            val resolvedDate = normalizeDate(dto.date, dto.timestamp)
            if (parseFlexibleDate(resolvedDate) == null && unexpectedDateLogCount < 5) {
                Log.w(
                    importLogTag,
                    "Unparseable imported date. rawDate='${dto.date}', rawTimestamp='${dto.timestamp}', normalized='$resolvedDate', type='${dto.type}'"
                )
                unexpectedDateLogCount++
            }
            val mappedCategory = when {
                resolvedType.equals("Expense", ignoreCase = true) -> dto.expCategory
                resolvedType.equals("Income", ignoreCase = true) -> dto.incCategory
                else -> dto.expCategory ?: dto.incCategory
            }.orEmpty()

            val timestamp = dto.timestamp?.trim().takeUnless { it.isNullOrBlank() }
            val remoteAccountName = dto.accountName?.trim().takeUnless { it.isNullOrBlank() }
                ?: dto.paymentMode?.trim().takeUnless { it.isNullOrBlank() }
            val transferParts = remoteAccountName
                ?.split("->")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()

            val fromAccountId = transferParts.getOrNull(0)
                ?.let { token ->
                    val key = normalizeAccountKey(token)
                    accountsByName[key]?.id ?: accountsByGroup[key]?.id
                }
            val toAccountId = transferParts.getOrNull(1)
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

            // For non-transfer rows, drop records that cannot be mapped safely.
            if (!resolvedType.equals("Transfer", ignoreCase = true) && mappedAccountId == null) {
                return@forEach
            }

            val remoteComparable = ComparableTx(
                date = resolvedDate,
                amount = dto.amount,
                type = resolvedType,
                description = dto.description,
                accountId = mappedAccountId
            )

            val isDuplicate = localComparable.any { local -> isCompositeDuplicate(local, remoteComparable) }

            if (!isDuplicate) {
                toInsert += ExpenseRecord(
                    date = resolvedDate,
                    type = resolvedType,
                    category = mappedCategory,
                    description = dto.description,
                    amount = dto.amount,
                    accountId = mappedAccountId,
                    remarks = dto.remarks,
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
                    isSynced = true,
                    remoteTimestamp = timestamp,
                    syncAction = "NONE"
                )
                localComparable += remoteComparable
            }
        }

        if (toInsert.isNotEmpty()) dao.insertAll(toInsert)
        return toInsert.size
    }

    override suspend fun importFromGoogleSheets(): GoogleSheetsImportResult {
        val dropdownResponse = apiService.importDropdownOptions(
            url = BuildConfig.APPS_SCRIPT_URL,
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
            url = BuildConfig.APPS_SCRIPT_URL,
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
            url = BuildConfig.APPS_SCRIPT_URL,
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
            val mapped = remoteBudgets.map { dto ->
                Budget(
                    id = 0,
                    monthYear = dto.monthYear,
                    category = dto.category,
                    amount = dto.amount
                )
            }
            budgetDao.clearAll()
            if (mapped.isNotEmpty()) {
                budgetDao.insertAll(mapped)
            }
            mapped.size
        }

        val txResponse = apiService.importRecords(
            url = BuildConfig.APPS_SCRIPT_URL,
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
        val imported = importRemoteRecords(dtos)
        val skipped = (dtos.size - imported).coerceAtLeast(0)

        return GoogleSheetsImportResult(
            imported = imported,
            skipped = skipped,
            restoredDropdowns = restoredDropdowns,
            restoredBudgets = restoredBudgets,
            restoredAccounts = restoredAccounts
        )
    }

    private data class ComparableTx(
        val date: String,
        val amount: Double,
        val type: String,
        val description: String,
        val accountId: Long?
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
}
