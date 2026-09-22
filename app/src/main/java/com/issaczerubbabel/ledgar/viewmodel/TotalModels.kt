package com.issaczerubbabel.ledgar.viewmodel

import java.time.YearMonth

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
    val accountsSummary: AccountsSummaryUi = AccountsSummaryUi()
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
