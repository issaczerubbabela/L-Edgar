package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.Budget
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.BudgetRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TotalViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())
    private val _isBudgetExpanded = MutableStateFlow(true)
    private val _isAccountsExpanded = MutableStateFlow(true)
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

    val uiState: StateFlow<TotalTabUiState> = combine(
        expenseRepository.getAllRecords(),
        budgetsForSelectedMonth,
        uiChromeState
    ) { records, budgets, chrome ->
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
            accountsSummary = accountsSummary
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

    companion object {
        private const val TOTAL_BUDGET_CATEGORY = "__TOTAL__"
        private val MONTH_YEAR_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    }

    private data class UiChromeState(
        val selectedYearMonth: YearMonth,
        val isBudgetExpanded: Boolean,
        val isAccountsExpanded: Boolean
    )
}
