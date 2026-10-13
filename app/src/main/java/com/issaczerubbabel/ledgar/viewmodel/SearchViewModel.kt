package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.BucketBudgetRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.data.repository.UndoableActions
import com.issaczerubbabel.ledgar.ledger.DatePreset
import com.issaczerubbabel.ledgar.ledger.FilterOptions
import com.issaczerubbabel.ledgar.ledger.Ledger
import com.issaczerubbabel.ledgar.ledger.LedgerFilter
import com.issaczerubbabel.ledgar.ledger.RecentCategory
import com.issaczerubbabel.ledgar.ledger.SearchChip
import com.issaczerubbabel.ledgar.ledger.SearchFilter
import com.issaczerubbabel.ledgar.ledger.SearchFilterKind
import com.issaczerubbabel.ledgar.ledger.SearchTotals
import com.issaczerubbabel.ledgar.ledger.TransactionSearch
import com.issaczerubbabel.ledgar.ledger.filterOptionsFor
import com.issaczerubbabel.ledgar.util.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter(),
    val chips: List<SearchChip> = emptyList(),
    val results: List<ExpenseRecord> = emptyList(),
    val isActive: Boolean = false,
    /** Null until something is searched. */
    val totals: SearchTotals? = null,
    val datePresets: List<DatePreset> = emptyList(),
    /** For the empty screen: tap one to filter by it. */
    val recentCategories: List<RecentCategory> = emptyList()
)

private data class SearchData(
    val records: List<ExpenseRecord>,
    val accounts: List<AccountRecord>,
    val expenseCategories: List<String>,
    val incomeCategories: List<String>,
    val roles: StatsRoles
)

/**
 * Search: the box matches notes, Categories, Accounts and amounts; chips narrow by Type, Date,
 * Category, Account and Amount. The query and chips survive rotation and the app being killed.
 */
@HiltViewModel
@OptIn(FlowPreview::class)
class SearchViewModel @Inject constructor(
    expenseRepository: ExpenseRepository,
    accountRepository: AccountRepository,
    dropdownOptionRepository: DropdownOptionRepository,
    bucketBudgetRepository: BucketBudgetRepository,
    undoableActions: UndoableActions,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val query = MutableStateFlow(savedStateHandle.get<String>(KEY_QUERY).orEmpty())
    private val filter = MutableStateFlow(restoreFilter())

    private val data = combine(
        expenseRepository.getAllRecords(),
        accountRepository.getAllVisibleAccounts(),
        dropdownOptionRepository.getOptionsByType(TransactionType.EXPENSE_CATEGORY_OPTION),
        dropdownOptionRepository.getOptionsByType(TransactionType.INCOME_CATEGORY_OPTION),
        dropdownOptionRepository.getOptionsByType("ACCOUNT_GROUP")
    ) { records, accounts, expense, income, groups ->
        SearchData(records, accounts, expense.map { it.name }, income.map { it.name }, StatsRoles.from(expense + income + groups))
    }.combine(undoableActions.hiddenTransactionIds) { data, hidden ->
        // A delete offering Undo is already gone from every list.
        data.copy(records = data.records.filterNot { it.id in hidden })
    }

    // Typing settles for a moment before the list is worked out; the previous results stay until then.
    private val settledQuery = query.debounce(150)

    val uiState: StateFlow<SearchUiState> = combine(
        data,
        settledQuery,
        filter,
        bucketBudgetRepository.observeRunningCycle()
    ) { data, q, f, cycle ->
        val results = TransactionSearch.search(data.records, q, f, data.accounts)
        val active = TransactionSearch.isActive(q, f)
        SearchUiState(
            query = q,
            filter = f,
            chips = TransactionSearch.chips(f, data.accounts),
            results = results,
            isActive = active,
            totals = if (active) TransactionSearch.totals(results, data.accounts, data.roles) else null,
            datePresets = TransactionSearch.datePresets(LocalDate.now(), cycle),
            recentCategories = TransactionSearch.recentCategories(data.records, RECENT_CATEGORIES)
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState(query = query.value, filter = filter.value))

    /** What's typed right now, ahead of the settled query the results use. */
    val typedQuery: StateFlow<String> = query.asStateFlow()

    // ── Category and Account sheet: the Ledger's filter sheet, applied with "Show" ──

    private val _picksDraft = MutableStateFlow(LedgerFilter())
    val picksDraft: StateFlow<LedgerFilter> = _picksDraft.asStateFlow()

    /** The sheet's choices, with amounts from what the box and the other chips leave. */
    val filterOptions: StateFlow<FilterOptions?> = combine(data, settledQuery, filter, _picksDraft) { data, q, f, draft ->
        val withoutPicks = f.copy(picks = LedgerFilter())
        val scope = if (TransactionSearch.isActive(q, withoutPicks)) {
            TransactionSearch.search(data.records, q, withoutPicks, data.accounts)
        } else {
            data.records.filter { it.syncAction != "DELETE" }
        }
        val expense = if (f.type == null || f.type == TransactionType.EXPENSE) data.expenseCategories else emptyList()
        val income = if (f.type == null || f.type == TransactionType.INCOME) data.incomeCategories else emptyList()
        Ledger.filterOptionsFor(scope, data.accounts, expense, income, draft)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun startPicksDraft() {
        _picksDraft.value = filter.value.picks
    }

    fun toggleExpenseCategory(name: String) = _picksDraft.update { it.copy(expenseCategories = it.expenseCategories.toggle(name)) }
    fun toggleIncomeCategory(name: String) = _picksDraft.update { it.copy(incomeCategories = it.incomeCategories.toggle(name)) }
    fun toggleAccount(id: Long) = _picksDraft.update { it.copy(accountIds = it.accountIds.toggle(id)) }
    fun clearDraftCategories(income: Boolean) = _picksDraft.update {
        if (income) it.copy(incomeCategories = emptySet()) else it.copy(expenseCategories = emptySet())
    }
    fun clearDraftAccounts() = _picksDraft.update { it.copy(accountIds = emptySet()) }
    fun clearDraft() {
        _picksDraft.value = LedgerFilter()
    }

    fun applyPicksDraft() = setFilter(filter.value.copy(picks = _picksDraft.value))

    // ── The box and the other chips ──

    fun onQueryChange(value: String) {
        query.value = value
        savedStateHandle[KEY_QUERY] = value
    }

    fun setType(type: String?) = setFilter(filter.value.withType(type))

    /** The picked range, or both null for any time. */
    fun setDateRange(start: LocalDate?, end: LocalDate?) = setFilter(filter.value.copy(start = start, end = end))

    fun setAmountRange(min: Double?, max: Double?) = setFilter(filter.value.copy(minAmount = min, maxAmount = max))

    /** A suggestion on the empty screen: that Category, from its own list. */
    fun filterByCategory(category: RecentCategory) = setFilter(
        filter.value.copy(
            picks = if (category.isIncome) LedgerFilter(incomeCategories = setOf(category.name))
            else LedgerFilter(expenseCategories = setOf(category.name))
        )
    )

    fun clear(kind: SearchFilterKind) = setFilter(filter.value.without(kind))

    fun clearAll() = setFilter(SearchFilter())

    private fun setFilter(value: SearchFilter) {
        filter.value = value
        savedStateHandle[KEY_TYPE] = value.type
        savedStateHandle[KEY_START] = value.start?.toString()
        savedStateHandle[KEY_END] = value.end?.toString()
        savedStateHandle[KEY_EXPENSE] = ArrayList(value.picks.expenseCategories)
        savedStateHandle[KEY_INCOME] = ArrayList(value.picks.incomeCategories)
        savedStateHandle[KEY_ACCOUNTS] = value.picks.accountIds.toLongArray()
        savedStateHandle[KEY_MIN] = value.minAmount
        savedStateHandle[KEY_MAX] = value.maxAmount
    }

    private fun restoreFilter() = SearchFilter(
        type = savedStateHandle.get<String>(KEY_TYPE),
        start = savedStateHandle.get<String>(KEY_START)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        end = savedStateHandle.get<String>(KEY_END)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        picks = LedgerFilter(
            expenseCategories = savedStateHandle.get<ArrayList<String>>(KEY_EXPENSE).orEmpty().toSet(),
            incomeCategories = savedStateHandle.get<ArrayList<String>>(KEY_INCOME).orEmpty().toSet(),
            accountIds = savedStateHandle.get<LongArray>(KEY_ACCOUNTS)?.toSet().orEmpty()
        ),
        minAmount = savedStateHandle.get<Double>(KEY_MIN),
        maxAmount = savedStateHandle.get<Double>(KEY_MAX)
    )

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

    private companion object {
        const val RECENT_CATEGORIES = 4
        const val KEY_QUERY = "searchQuery"
        const val KEY_TYPE = "searchType"
        const val KEY_START = "searchStart"
        const val KEY_END = "searchEnd"
        const val KEY_EXPENSE = "searchExpenseCategories"
        const val KEY_INCOME = "searchIncomeCategories"
        const val KEY_ACCOUNTS = "searchAccounts"
        const val KEY_MIN = "searchMin"
        const val KEY_MAX = "searchMax"
    }
}
