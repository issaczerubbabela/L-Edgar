package com.sheetsync.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheetsync.data.local.entity.AccountRecord
import com.sheetsync.data.local.entity.Budget
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.repository.AccountRepository
import com.sheetsync.data.repository.BudgetRepository
import com.sheetsync.data.repository.ExpenseRepository
import com.sheetsync.util.parseFlexibleDate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
@OptIn(ExperimentalCoroutinesApi::class)
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
    private val budgetsForSelectedMonth = _selectedYearMonth.flatMapLatest { ym ->
        budgetRepository.observeBudgets(ym.format(MONTH_YEAR_FORMATTER))
    }

    private val uiChromeState = combine(
        _selectedYearMonth,
        _isBudgetExpanded,
        _isAccountsExpanded
    ) { selectedYearMonth, isBudgetExpanded, isAccountsExpanded ->
        UiChromeState(
            selectedYearMonth = selectedYearMonth,
            isBudgetExpanded = isBudgetExpanded,
            isAccountsExpanded = isAccountsExpanded
        )
    }

    private val exportDialogState = combine(
        _showExportDialog,
        _selectedExportInterval,
        _customStartDateInput,
        _customEndDateInput
    ) { showExportDialog, selectedExportInterval, customStartDateInput, customEndDateInput ->
        ExportDialogState(
            showExportDialog = showExportDialog,
            selectedExportInterval = selectedExportInterval,
            customStartDateInput = customStartDateInput,
            customEndDateInput = customEndDateInput
        )
    }

    private val exportUiState = combine(
        exportDialogState,
        _pendingExportFileName,
        _exportStatusMessage
    ) { exportDialog, pendingExportFileName, exportStatusMessage ->
        ExportUiState(
            dialogState = exportDialog,
            pendingExportFileName = pendingExportFileName,
            exportStatusMessage = exportStatusMessage
        )
    }

    val uiState: StateFlow<TotalTabUiState> = combine(
        expenseRepository.getAllRecords(),
        budgetsForSelectedMonth,
        uiChromeState,
        exportUiState
    ) { records, budgets, chrome, export ->
        val selectedYm = chrome.selectedYearMonth

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
            isBudgetExpanded = chrome.isBudgetExpanded,
            isAccountsExpanded = chrome.isAccountsExpanded,
            budgetItems = budgetItems,
            accountsSummary = accountsSummary,
            showExportDialog = export.dialogState.showExportDialog,
            selectedExportInterval = export.dialogState.selectedExportInterval,
            customStartDateInput = export.dialogState.customStartDateInput,
            customEndDateInput = export.dialogState.customEndDateInput,
            pendingExportFileName = export.pendingExportFileName,
            exportStatusMessage = export.exportStatusMessage
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TotalTabUiState())

    fun nextMonth() = _selectedYearMonth.update {
        it.plusMonths(1)
    }

    fun prevMonth() = _selectedYearMonth.update {
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
        budgets: List<Budget>
    ): List<BudgetProgressUi> {
        val expenseRecords = monthRecords.filter { it.type == "Expense" }
        val monthExpense = expenseRecords.sumOf { it.amount }
        val configuredBudgets = budgets.filterNot { it.category == TOTAL_BUDGET_CATEGORY }
        val allocatedBudgetAmount = configuredBudgets.sumOf { it.amount }
        val totalBudgetAmount = budgets.firstOrNull { it.category == TOTAL_BUDGET_CATEGORY }?.amount
            ?: allocatedBudgetAmount
        val idealFraction = calculateIdealFraction(selectedYm)

        val totalItem = BudgetProgressUi(
            title = "Total Budget",
            icon = "",
            budgetAmount = totalBudgetAmount,
            spentAmount = monthExpense,
            remainingAmount = totalBudgetAmount - monthExpense,
            progressPercent = percent(monthExpense, totalBudgetAmount),
            showTodayMarker = true,
            todayMarkerFraction = idealFraction
        )

        val categoryItems = configuredBudgets
            .map { budget ->
            val categorySpend = expenseRecords
                .filter { it.type == "Expense" && it.category.equals(budget.category, ignoreCase = true) }
                .sumOf { it.amount }

            BudgetProgressUi(
                title = budget.category,
                icon = iconForCategory(budget.category),
                budgetAmount = budget.amount,
                spentAmount = categorySpend,
                remainingAmount = budget.amount - categorySpend,
                progressPercent = percent(categorySpend, budget.amount),
                showTodayMarker = true,
                todayMarkerFraction = idealFraction
            )
        }

        val matchedCategoryExpense = categoryItems.sumOf { it.spentAmount }
        val otherSpentAmount = (monthExpense - matchedCategoryExpense).coerceAtLeast(0.0)
        val otherBudgetAmount = totalBudgetAmount - allocatedBudgetAmount

        val otherItem = BudgetProgressUi(
            title = "Other",
            icon = "",
            budgetAmount = otherBudgetAmount,
            spentAmount = otherSpentAmount,
            remainingAmount = otherBudgetAmount - otherSpentAmount,
            progressPercent = percent(otherSpentAmount, otherBudgetAmount),
            showTodayMarker = true,
            todayMarkerFraction = idealFraction
        )

        return listOf(totalItem) + categoryItems + otherItem
    }

    private fun calculateIdealFraction(selectedYm: YearMonth): Float {
        val now = LocalDate.now()
        val currentMonth = YearMonth.from(now)
        return when {
            selectedYm.isBefore(currentMonth) -> 1f
            selectedYm.isAfter(currentMonth) -> 0f
            else -> (now.dayOfMonth.toFloat() / selectedYm.lengthOfMonth().coerceAtLeast(1).toFloat())
        }
    }

    private fun iconForCategory(category: String): String = when (category) {
        "Food" -> "🍜"
        "Social Life" -> "🧑‍🤝‍🧑"
        "Transport" -> "🚌"
        "Shopping" -> "🛍️"
        "Utilities" -> "💡"
        "Health" -> "🏥"
        "Education" -> "📘"
        else -> "📒"
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

    companion object {
        private const val TOTAL_BUDGET_CATEGORY = "__TOTAL__"
        private val MONTH_YEAR_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    }

    private data class UiChromeState(
        val selectedYearMonth: YearMonth,
        val isBudgetExpanded: Boolean,
        val isAccountsExpanded: Boolean
    )

    private data class ExportDialogState(
        val showExportDialog: Boolean,
        val selectedExportInterval: ExportInterval,
        val customStartDateInput: String,
        val customEndDateInput: String
    )

    private data class ExportUiState(
        val dialogState: ExportDialogState,
        val pendingExportFileName: String?,
        val exportStatusMessage: String?
    )
}
