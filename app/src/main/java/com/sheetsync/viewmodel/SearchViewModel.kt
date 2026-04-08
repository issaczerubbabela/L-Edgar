package com.sheetsync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheetsync.data.local.entity.AccountRecord
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.repository.AccountRepository
import com.sheetsync.data.repository.DropdownOptionRepository
import com.sheetsync.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val filtersExpanded: Boolean = false,
    val startDate: String = "",
    val endDate: String = "",
    val selectedAccountId: Long? = null,
    val selectedCategory: String = "",
    val minAmount: String = "",
    val maxAmount: String = "",
    val accounts: List<AccountRecord> = emptyList(),
    val categories: List<String> = emptyList(),
    val results: List<ExpenseRecord> = emptyList(),
    val incomeTotal: Double = 0.0,
    val expenseTotal: Double = 0.0,
    val transferTotal: Double = 0.0
)

private data class SearchParams(
    val query: String?,
    val startDate: String?,
    val endDate: String?,
    val accountId: Long?,
    val category: String?,
    val minAmount: Double?,
    val maxAmount: Double?
)

private data class SearchFilterInputs(
    val startDate: String = "",
    val endDate: String = "",
    val selectedAccountId: Long? = null,
    val selectedCategory: String = "",
    val minAmount: String = "",
    val maxAmount: String = ""
)

private data class SearchHeaderState(
    val query: String,
    val filtersExpanded: Boolean,
    val filters: SearchFilterInputs
)

private data class SearchDataState(
    val accounts: List<AccountRecord>,
    val categories: List<String>,
    val results: List<ExpenseRecord>
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel @Inject constructor(
    expenseRepository: ExpenseRepository,
    accountRepository: AccountRepository,
    dropdownOptionRepository: DropdownOptionRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filtersExpanded = MutableStateFlow(false)
    private val filters = MutableStateFlow(SearchFilterInputs())

    private val accounts = accountRepository
        .getAllVisibleAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val categories = combine(
        dropdownOptionRepository.getOptionsByType("EXPENSE_CATEGORY"),
        dropdownOptionRepository.getOptionsByType("INCOME_CATEGORY")
    ) { expense, income ->
        (expense + income).map { it.name }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val searchParams = combine(query, filters) { q, input ->
        SearchParams(
            query = q.trim().ifBlank { null },
            startDate = input.startDate.trim().ifBlank { null },
            endDate = input.endDate.trim().ifBlank { null },
            accountId = input.selectedAccountId,
            category = input.selectedCategory.trim().ifBlank { null },
            minAmount = input.minAmount.toDoubleOrNull(),
            maxAmount = input.maxAmount.toDoubleOrNull()
        )
    }

    private val results = searchParams
        .flatMapLatest { params ->
            expenseRepository.searchTransactions(
                query = params.query,
                startDate = params.startDate,
                endDate = params.endDate,
                accountId = params.accountId,
                category = params.category,
                minAmount = params.minAmount,
                maxAmount = params.maxAmount
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val headerState = combine(
        query,
        filtersExpanded,
        filters
    ) { q, expanded, input ->
        SearchHeaderState(query = q, filtersExpanded = expanded, filters = input)
    }

    private val dataState = combine(
        accounts,
        categories,
        results
    ) { accountList, categoryList, searchResults ->
        SearchDataState(accounts = accountList, categories = categoryList, results = searchResults)
    }

    val uiState: StateFlow<SearchUiState> = combine(headerState, dataState) { header, data ->
        SearchUiState(
            query = header.query,
            filtersExpanded = header.filtersExpanded,
            startDate = header.filters.startDate,
            endDate = header.filters.endDate,
            selectedAccountId = header.filters.selectedAccountId,
            selectedCategory = header.filters.selectedCategory,
            minAmount = header.filters.minAmount,
            maxAmount = header.filters.maxAmount,
            accounts = data.accounts,
            categories = data.categories,
            results = data.results,
            incomeTotal = data.results.filter { it.type == "Income" }.sumOf { it.amount },
            expenseTotal = data.results.filter { it.type == "Expense" }.sumOf { it.amount },
            transferTotal = data.results.filter { it.type == "Transfer" }.sumOf { it.amount }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState())

    fun onQueryChange(value: String) = query.update { value }

    fun toggleFilters() = filtersExpanded.update { !it }

    fun onStartDateChange(value: String) = filters.update { it.copy(startDate = value) }

    fun onEndDateChange(value: String) = filters.update { it.copy(endDate = value) }

    fun onAccountSelected(accountId: Long?) = filters.update { it.copy(selectedAccountId = accountId) }

    fun onCategorySelected(category: String) = filters.update { it.copy(selectedCategory = category) }

    fun onMinAmountChange(value: String) = filters.update { it.copy(minAmount = value) }

    fun onMaxAmountChange(value: String) = filters.update { it.copy(maxAmount = value) }
}
