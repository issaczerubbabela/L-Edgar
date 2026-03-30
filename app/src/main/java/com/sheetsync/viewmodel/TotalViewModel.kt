package com.sheetsync.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheetsync.data.local.entity.AccountRecord
import com.sheetsync.data.local.entity.BudgetRecord
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.repository.AccountRepository
import com.sheetsync.data.repository.BudgetRepository
import com.sheetsync.data.repository.ExpenseRepository
import com.sheetsync.util.parseFlexibleDate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TotalViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())
    private val _isBudgetExpanded = MutableStateFlow(true)
    private val _isAccountsExpanded = MutableStateFlow(true)
    private val _showExportDialog = MutableStateFlow(false)
    private val _selectedExportInterval = MutableStateFlow(ExportInterval.CURRENT_MONTH)
    private val _customStartDateInput = MutableStateFlow("")
    private val _customEndDateInput = MutableStateFlow("")
    private val _pendingExportFileName = MutableStateFlow<String?>(null)
    private val _exportStatusMessage = MutableStateFlow<String?>(null)

    private var userAdjustedMonth = false

    init {
        viewModelScope.launch {
            expenseRepository.getAllRecords().collect { all ->
                if (userAdjustedMonth || all.isEmpty()) return@collect

                val selectedHasData = all.any { record ->
                    parseRecordDate(record)?.let { d ->
                        d.year == _selectedYearMonth.value.year &&
                            d.monthValue == _selectedYearMonth.value.monthValue
                    } == true
                }

                if (!selectedHasData) {
                    val latest = all.mapNotNull { parseRecordDate(it) }.maxOrNull()
                    if (latest != null) {
                        _selectedYearMonth.value = YearMonth.of(latest.year, latest.monthValue)
                    }
                }
            }
        }
    }

    val uiState: StateFlow<TotalTabUiState> = combine(
        expenseRepository.getAllRecords(),
        budgetRepository.observeBudgets(),
        _selectedYearMonth,
        _isBudgetExpanded,
        _isAccountsExpanded,
        _showExportDialog,
        _selectedExportInterval,
        _customStartDateInput,
        _customEndDateInput,
        _pendingExportFileName,
        _exportStatusMessage
    ) { values ->
        val records = values[0] as List<ExpenseRecord>
        val budgets = values[1] as List<BudgetRecord>
        val selectedYm = values[2] as YearMonth
        val isBudgetExpanded = values[3] as Boolean
        val isAccountsExpanded = values[4] as Boolean
        val showExportDialog = values[5] as Boolean
        val selectedExportInterval = values[6] as ExportInterval
        val customStart = values[7] as String
        val customEnd = values[8] as String
        val pendingExportFileName = values[9] as String?
        val exportMessage = values[10] as String?

        val monthRecords = records.filterByYearMonth(selectedYm)
        val monthIncome = monthRecords.filter { it.type == "Income" }.sumOf { it.amount }
        val monthExpense = monthRecords.filter { it.type == "Expense" }.sumOf { it.amount }

        val budgetItems = buildBudgetItems(selectedYm, monthRecords, budgets)
        val accountsSummary = buildAccountsSummary(selectedYm, records)

        TotalTabUiState(
            selectedYearMonth = selectedYm,
            periodLabel = selectedYm.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)),
            summary = PeriodSummary(
                income = monthIncome,
                expense = monthExpense,
                total = monthIncome - monthExpense
            ),
            isBudgetExpanded = isBudgetExpanded,
            isAccountsExpanded = isAccountsExpanded,
            budgetItems = budgetItems,
            accountsSummary = accountsSummary,
            showExportDialog = showExportDialog,
            selectedExportInterval = selectedExportInterval,
            customStartDateInput = customStart,
            customEndDateInput = customEnd,
            pendingExportFileName = pendingExportFileName,
            exportStatusMessage = exportMessage
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TotalTabUiState())

    fun nextMonth() = _selectedYearMonth.update {
        userAdjustedMonth = true
        it.plusMonths(1)
    }

    fun prevMonth() = _selectedYearMonth.update {
        userAdjustedMonth = true
        it.minusMonths(1)
    }

    fun toggleBudgetSection() = _isBudgetExpanded.update { !it }

    fun toggleAccountsSection() = _isAccountsExpanded.update { !it }

    fun openExportDialog() {
        _showExportDialog.value = true
    }

    fun closeExportDialog() {
        _showExportDialog.value = false
    }

    fun selectExportInterval(interval: ExportInterval) {
        _selectedExportInterval.value = interval
    }

    fun updateCustomStart(input: String) {
        _customStartDateInput.value = input
    }

    fun updateCustomEnd(input: String) {
        _customEndDateInput.value = input
    }

    fun clearExportMessage() {
        _exportStatusMessage.value = null
    }

    fun requestExportDocument() {
        val ym = _selectedYearMonth.value
        val stamp = ym.format(DateTimeFormatter.ofPattern("yyyy_MM", Locale.ENGLISH))
        _pendingExportFileName.value = "sheetsync_export_${stamp}.csv"
    }

    fun consumeExportRequest() {
        _pendingExportFileName.value = null
    }

    fun exportDataToUri(uri: Uri) {
        viewModelScope.launch {
            val range = buildRange(_selectedExportInterval.value, _selectedYearMonth.value)

            if (range == null) {
                _exportStatusMessage.value = "Invalid export range"
                return@launch
            }

            val filteredRecords = expenseRepository
                .getRecordsByDateRange(range.first.toString(), range.second.toString())
                .first()
            val accounts = accountRepository.getAllAccounts().first()
            val accountMap = accounts.associateBy { it.id }

            val exported = runCatching {
                writeCsv(uri, filteredRecords, accountMap)
            }

            if (exported.isSuccess) {
                _exportStatusMessage.value = "Export completed"
                _showExportDialog.value = false
            } else {
                _exportStatusMessage.value = "Export failed"
            }
        }
    }

    private fun buildRange(interval: ExportInterval, selectedYm: YearMonth): Pair<LocalDate, LocalDate>? {
        return when (interval) {
            ExportInterval.CURRENT_MONTH -> selectedYm.atDay(1) to selectedYm.atEndOfMonth()
            ExportInterval.LAST_3_MONTHS -> selectedYm.minusMonths(2).atDay(1) to selectedYm.atEndOfMonth()
            ExportInterval.CURRENT_YEAR -> LocalDate.of(selectedYm.year, 1, 1) to LocalDate.of(selectedYm.year, 12, 31)
            ExportInterval.LAST_YEAR -> {
                val year = selectedYm.year - 1
                LocalDate.of(year, 1, 1) to LocalDate.of(year, 12, 31)
            }
            ExportInterval.CUSTOM -> {
                val start = runCatching { LocalDate.parse(_customStartDateInput.value) }.getOrNull()
                val end = runCatching { LocalDate.parse(_customEndDateInput.value) }.getOrNull()
                if (start != null && end != null && !end.isBefore(start)) start to end else null
            }
        }
    }

    private fun buildBudgetItems(
        selectedYm: YearMonth,
        monthRecords: List<ExpenseRecord>,
        budgets: List<BudgetRecord>
    ): List<BudgetProgressUi> {
        val monthExpense = monthRecords.filter { it.type == "Expense" }.sumOf { it.amount }
        val totalBudgetAmount = budgets.sumOf { it.amount }

        val todayFraction = if (selectedYm == YearMonth.now()) {
            LocalDate.now().dayOfMonth.toFloat() / selectedYm.lengthOfMonth().coerceAtLeast(1)
        } else 1f

        val totalItem = BudgetProgressUi(
            title = "Total Budget",
            icon = "",
            budgetAmount = totalBudgetAmount,
            spentAmount = monthExpense,
            remainingAmount = (totalBudgetAmount - monthExpense).coerceAtLeast(0.0),
            progressPercent = percent(monthExpense, totalBudgetAmount),
            showTodayMarker = true,
            todayMarkerFraction = todayFraction
        )

        val categoryItems = budgets.map { budget ->
            val categorySpend = monthRecords
                .filter { it.type == "Expense" && it.category.equals(budget.category, ignoreCase = true) }
                .sumOf { it.amount }

            BudgetProgressUi(
                title = budget.category,
                icon = budget.iconEmoji,
                budgetAmount = budget.amount,
                spentAmount = categorySpend,
                remainingAmount = (budget.amount - categorySpend).coerceAtLeast(0.0),
                progressPercent = percent(categorySpend, budget.amount)
            )
        }

        return listOf(totalItem) + categoryItems
    }

    private fun buildAccountsSummary(selectedYm: YearMonth, allRecords: List<ExpenseRecord>): AccountsSummaryUi {
        val currentMonthRecords = allRecords.filterByYearMonth(selectedYm)
        val prevYm = selectedYm.minusMonths(1)
        val previousMonthRecords = allRecords.filterByYearMonth(prevYm)

        val currentExpense = currentMonthRecords.filter { it.type == "Expense" }.sumOf { it.amount }
        val previousExpense = previousMonthRecords.filter { it.type == "Expense" }.sumOf { it.amount }

        val cashAccountsExpense = currentMonthRecords.filter {
            it.type == "Expense" && !it.paymentMode.contains("card", ignoreCase = true)
        }.sumOf { it.amount }

        val cardExpense = currentMonthRecords.filter {
            it.type == "Expense" && it.paymentMode.contains("card", ignoreCase = true)
        }.sumOf { it.amount }

        val transferExpense = currentMonthRecords.filter {
            it.category.contains("transfer", ignoreCase = true) || it.description.contains("transfer", ignoreCase = true)
        }.sumOf { it.amount }

        val comparedPercent = if (previousExpense <= 0.0) 100 else ((currentExpense / previousExpense) * 100).toInt()

        return AccountsSummaryUi(
            dateRangeLabel = "${selectedYm.monthValue}.1.${selectedYm.year % 100} ~ ${selectedYm.monthValue}.${selectedYm.lengthOfMonth()}.${selectedYm.year % 100}",
            comparedExpensesPercent = comparedPercent,
            cashAccountsExpense = cashAccountsExpense,
            cardExpense = cardExpense,
            transferExpense = transferExpense
        )
    }

    private fun percent(spent: Double, budget: Double): Int {
        if (budget <= 0.0) return 0
        return ((spent / budget) * 100).toInt().coerceIn(0, 999)
    }

    private fun List<ExpenseRecord>.filterByYearMonth(ym: YearMonth): List<ExpenseRecord> = filter { record ->
        parseRecordDate(record)?.let { date ->
            date.year == ym.year && date.monthValue == ym.monthValue
        } == true
    }

    private fun parseRecordDate(record: ExpenseRecord): LocalDate? =
        parseFlexibleDate(record.date) ?: record.remoteTimestamp?.let(::parseFlexibleDate)

    private suspend fun writeCsv(
        uri: Uri,
        records: List<ExpenseRecord>,
        accountMap: Map<Long, AccountRecord>
    ) = withContext(Dispatchers.IO) {
        appContext.contentResolver.openOutputStream(uri)?.use { stream ->
            OutputStreamWriter(stream).use { writer ->
                writer.appendLine("Date,Type,Category/Account,Amount,Note")
                records.forEach { record ->
                    val categoryOrAccount = if (record.type == "Transfer") {
                        val from = record.fromAccountId?.let { accountMap[it]?.accountName } ?: "Unknown"
                        val to = record.toAccountId?.let { accountMap[it]?.accountName } ?: "Unknown"
                        "Transfer: $from -> $to"
                    } else {
                        record.category
                    }
                    val note = record.remarks.ifBlank { record.description }
                    writer.appendLine(
                        listOf(
                            csvEscape(record.date),
                            csvEscape(record.type),
                            csvEscape(categoryOrAccount),
                            record.amount.toString(),
                            csvEscape(note)
                        ).joinToString(",")
                    )
                }
            }
        } ?: error("Unable to open output stream")
    }

    private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
