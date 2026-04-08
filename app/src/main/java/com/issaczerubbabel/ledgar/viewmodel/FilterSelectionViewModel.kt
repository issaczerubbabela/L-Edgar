package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

enum class FilterSelectionTab {
    INCOME,
    EXPENSES,
    ACCOUNT
}

data class FilterOptionItem(
    val id: Long,
    val label: String,
    val amount: Double
)

data class AccountFilterOptionItem(
    val id: Long,
    val label: String,
    val incomeAmount: Double,
    val expenseAmount: Double
)

data class FilterSelectionUiState(
    val selectedYear: Int = LocalDate.now().year,
    val selectedMonth: Int = LocalDate.now().monthValue,
    val monthLabel: String = "",
    val activeTab: FilterSelectionTab = FilterSelectionTab.INCOME,
    val incomeTotal: Double = 0.0,
    val expenseTotal: Double = 0.0,
    val selectedIncomeSum: Double = 0.0,
    val selectedExpenseSum: Double = 0.0,
    val selectedIncomeIds: Set<Long> = emptySet(),
    val selectedExpenseIds: Set<Long> = emptySet(),
    val selectedAccountIds: Set<Long> = emptySet(),
    val incomeOptions: List<FilterOptionItem> = emptyList(),
    val expenseOptions: List<FilterOptionItem> = emptyList(),
    val accountOptions: List<AccountFilterOptionItem> = emptyList()
)

private data class FilterSelectionHeaderState(
    val ym: YearMonth,
    val tab: FilterSelectionTab,
    val incomeSelected: Set<Long>,
    val expenseSelected: Set<Long>,
    val accountSelected: Set<Long>
)

private data class FilterSelectionDataState(
    val records: List<ExpenseRecord>,
    val accounts: List<AccountRecord>,
    val incomeCategories: List<DropdownOption>,
    val expenseCategories: List<DropdownOption>
)

@HiltViewModel
class FilterSelectionViewModel @Inject constructor(
    expenseRepository: ExpenseRepository,
    accountRepository: AccountRepository,
    dropdownOptionRepository: DropdownOptionRepository
) : ViewModel() {

    private val selectedYearMonth = MutableStateFlow(YearMonth.now())
    private val activeTab = MutableStateFlow(FilterSelectionTab.INCOME)
    private val selectedIncomeIds = MutableStateFlow<Set<Long>>(emptySet())
    private val selectedExpenseIds = MutableStateFlow<Set<Long>>(emptySet())
    private val selectedAccountIds = MutableStateFlow<Set<Long>>(emptySet())

    private val allRecords = expenseRepository
        .getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val accounts = accountRepository
        .getAllVisibleAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val incomeCategories = dropdownOptionRepository
        .getOptionsByType("INCOME_CATEGORY")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val expenseCategories = dropdownOptionRepository
        .getOptionsByType("EXPENSE_CATEGORY")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val headerState = combine(
        selectedYearMonth,
        activeTab,
        selectedIncomeIds,
        selectedExpenseIds,
        selectedAccountIds
    ) { ym, tab, incomeSelected, expenseSelected, accountSelected ->
        FilterSelectionHeaderState(
            ym = ym,
            tab = tab,
            incomeSelected = incomeSelected,
            expenseSelected = expenseSelected,
            accountSelected = accountSelected
        )
    }

    private val dataState = combine(
        allRecords,
        accounts,
        incomeCategories,
        expenseCategories
    ) { records, accountRecords, incomeOptions, expenseOptions ->
        FilterSelectionDataState(
            records = records,
            accounts = accountRecords,
            incomeCategories = incomeOptions,
            expenseCategories = expenseOptions
        )
    }

    val uiState: StateFlow<FilterSelectionUiState> = combine(headerState, dataState) { header, data ->
        buildUiState(
            ym = header.ym,
            tab = header.tab,
            incomeSelected = header.incomeSelected,
            expenseSelected = header.expenseSelected,
            accountSelected = header.accountSelected,
            records = data.records,
            accounts = data.accounts,
            incomeCategories = data.incomeCategories,
            expenseCategories = data.expenseCategories
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilterSelectionUiState())

    fun nextMonth() {
        selectedYearMonth.update { it.plusMonths(1) }
    }

    fun prevMonth() {
        selectedYearMonth.update { it.minusMonths(1) }
    }

    fun setActiveTab(tab: FilterSelectionTab) {
        activeTab.update { tab }
    }

    fun toggleIncomeCategory(id: Long) {
        selectedIncomeIds.update { current ->
            if (current.contains(id)) current - id else current + id
        }
    }

    fun clearIncomeCategories() {
        selectedIncomeIds.update { emptySet() }
    }

    fun toggleExpenseCategory(id: Long) {
        selectedExpenseIds.update { current ->
            if (current.contains(id)) current - id else current + id
        }
    }

    fun clearExpenseCategories() {
        selectedExpenseIds.update { emptySet() }
    }

    fun toggleAccount(id: Long) {
        selectedAccountIds.update { current ->
            if (current.contains(id)) current - id else current + id
        }
    }

    fun clearAccounts() {
        selectedAccountIds.update { emptySet() }
    }

    private fun buildUiState(
        ym: YearMonth,
        tab: FilterSelectionTab,
        incomeSelected: Set<Long>,
        expenseSelected: Set<Long>,
        accountSelected: Set<Long>,
        records: List<ExpenseRecord>,
        accounts: List<AccountRecord>,
        incomeCategories: List<DropdownOption>,
        expenseCategories: List<DropdownOption>
    ): FilterSelectionUiState {
        val monthRecords = records.filter { record ->
            val date = parseFlexibleDate(record.date) ?: return@filter false
            date.year == ym.year && date.monthValue == ym.monthValue
        }

        val incomeTotal = monthRecords.filter { it.type == "Income" }.sumOf { it.amount }
        val expenseTotal = monthRecords.filter { it.type == "Expense" }.sumOf { it.amount }

        val incomeByCategory = monthRecords
            .filter { it.type == "Income" }
            .groupBy { it.category }
            .mapValues { (_, values) -> values.sumOf { it.amount } }

        val expenseByCategory = monthRecords
            .filter { it.type == "Expense" }
            .groupBy { it.category }
            .mapValues { (_, values) -> values.sumOf { it.amount } }

        val incomeOptionItems = incomeCategories.map { option ->
            FilterOptionItem(
                id = option.id,
                label = option.name,
                amount = incomeByCategory[option.name] ?: 0.0
            )
        }

        val expenseOptionItems = expenseCategories.map { option ->
            FilterOptionItem(
                id = option.id,
                label = option.name,
                amount = expenseByCategory[option.name] ?: 0.0
            )
        }

        val accountOptionItems = accounts.map { account ->
            val incomeAmount = monthRecords.sumOf { record ->
                if (record.type != "Income") return@sumOf 0.0
                if (record.toAccountId == account.id || record.accountId == account.id) record.amount else 0.0
            }
            val expenseAmount = monthRecords.sumOf { record ->
                if (record.type != "Expense") return@sumOf 0.0
                if (record.fromAccountId == account.id || record.accountId == account.id) record.amount else 0.0
            }
            AccountFilterOptionItem(
                id = account.id,
                label = account.accountName,
                incomeAmount = incomeAmount,
                expenseAmount = expenseAmount
            )
        }

        val selectedIncomeSum = incomeOptionItems
            .filter { incomeSelected.contains(it.id) }
            .sumOf { it.amount }

        val selectedExpenseSum = expenseOptionItems
            .filter { expenseSelected.contains(it.id) }
            .sumOf { it.amount }

        return FilterSelectionUiState(
            selectedYear = ym.year,
            selectedMonth = ym.monthValue,
            monthLabel = ym.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)),
            activeTab = tab,
            incomeTotal = incomeTotal,
            expenseTotal = expenseTotal,
            selectedIncomeSum = selectedIncomeSum,
            selectedExpenseSum = selectedExpenseSum,
            selectedIncomeIds = incomeSelected,
            selectedExpenseIds = expenseSelected,
            selectedAccountIds = accountSelected,
            incomeOptions = incomeOptionItems,
            expenseOptions = expenseOptionItems,
            accountOptions = accountOptionItems
        )
    }
}
