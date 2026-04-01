package com.sheetsync.viewmodel

import java.time.YearMonth

enum class ExportInterval(val label: String) {
    CURRENT_MONTH("Monthly"),
    LAST_3_MONTHS("Last 3 Months"),
    CURRENT_YEAR("Annually"),
    LAST_YEAR("Last Year"),
    CUSTOM("Custom Date Range")
}

data class BudgetProgressUi(
    val title: String,
    val icon: String,
    val budgetAmount: Double,
    val spentAmount: Double,
    val remainingAmount: Double,
    val progressPercent: Int,
    val showTodayMarker: Boolean = false,
    val todayMarkerFraction: Float = 0f
)

data class AccountsSummaryUi(
    val dateRangeLabel: String = "",
    val comparedExpensesPercent: Int = 0,
    val cashAccountsExpense: Double = 0.0,
    val cardExpense: Double = 0.0,
    val transferExpense: Double = 0.0
)

data class TotalTabUiState(
    val selectedYearMonth: YearMonth = YearMonth.now(),
    val periodLabel: String = "",
    val summary: PeriodSummary = PeriodSummary(),
    val isBudgetExpanded: Boolean = true,
    val isAccountsExpanded: Boolean = true,
    val budgetItems: List<BudgetProgressUi> = emptyList(),
    val accountsSummary: AccountsSummaryUi = AccountsSummaryUi(),
    val showExportDialog: Boolean = false,
    val selectedExportInterval: ExportInterval = ExportInterval.CURRENT_MONTH,
    val customStartDateInput: String = "",
    val customEndDateInput: String = "",
    val pendingExportFileName: String? = null,
    val exportStatusMessage: String? = null
)

data class BudgetSettingItemUi(
    val id: Long,
    val category: String,
    val icon: String,
    val amount: Double
)

data class BudgetSettingUiState(
    val items: List<BudgetSettingItemUi> = emptyList(),
    val categoryOptions: List<String> = emptyList(),
    val totalBudgetInput: String = "",
    val totalBudgetAmount: Double = 0.0,
    val allocatedAmount: Double = 0.0,
    val otherAmount: Double = 0.0,
    val showEditorDialog: Boolean = false,
    val editingId: Long? = null,
    val selectedCategory: String = "",
    val amountInput: String = ""
)
