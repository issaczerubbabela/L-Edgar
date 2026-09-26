package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.preferences.ChartPalette
import com.issaczerubbabel.ledgar.data.preferences.ThemePreferenceRepository
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.BucketBudgetRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** What the Stats screen draws. */
data class StatsUiState(
    val loading: Boolean = true,
    val scope: StatsScope = StatsScope.MONTHLY,
    val period: StatsPeriod = StatsPeriod.of(StatsScope.MONTHLY, LocalDate.now()),
    val hasCycles: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val report: StatsReportUi = StatsReportUi(),
    val buckets: List<BudgetBucket> = emptyList()
) {
    val canGoBack: Boolean get() = (period as? StatsPeriod.Cycle)?.hasPrevious ?: true
    val canGoForward: Boolean get() = (period as? StatsPeriod.Cycle)?.hasNext ?: true
}

/** A Category or Bucket the user opened from the breakdown. */
data class DetailRequest(val title: String, val categories: Set<String>, val bucketColorIndex: Int? = null)

/** Wires the repositories to [StatsReport]; all the arithmetic lives there. */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel @Inject constructor(
    expenseRepository: ExpenseRepository,
    accountRepository: AccountRepository,
    dropdownOptionRepository: DropdownOptionRepository,
    bucketBudgetRepository: BucketBudgetRepository,
    themePreferenceRepository: ThemePreferenceRepository
) : ViewModel() {

    private val filter = MutableStateFlow(StatsFilterState())
    val filterState: StateFlow<StatsFilterState> = filter.asStateFlow()

    private val detailRequest = MutableStateFlow<DetailRequest?>(null)

    private val rupeeFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }

    val chartPalette: StateFlow<ChartPalette> = themePreferenceRepository.chartPalette
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChartPalette.STANDARD)

    private val roles = combine(
        dropdownOptionRepository.getOptionsByType("EXPENSE_CATEGORY"),
        dropdownOptionRepository.getOptionsByType("INCOME_CATEGORY"),
        dropdownOptionRepository.getOptionsByType("ACCOUNT_GROUP")
    ) { expense, income, groups -> StatsRoles.from(expense + income + groups) }

    private val cycles = bucketBudgetRepository.observeAllCycles()

    /** The filter with its default scope applied: Cycle when a cycle exists, until the user picks one. */
    private val effectiveFilter = combine(filter, cycles) { f, c ->
        if (!f.scopeChosen && c.isNotEmpty()) f.copy(scope = StatsScope.CYCLE) else f
    }

    private val periodFlow: Flow<StatsPeriod> = combine(effectiveFilter, cycles) { f, c -> f.period(c, LocalDate.now()) }
        .distinctUntilChanged()

    private val bucketsForPeriod: Flow<Pair<List<BudgetBucket>, List<BucketCategory>>> = periodFlow
        .map { (it as? StatsPeriod.Cycle)?.span?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList<BudgetBucket>() to emptyList())
            } else {
                combine(bucketBudgetRepository.observeBuckets(id), bucketBudgetRepository.observeCategoryAssignments(id)) { b, a -> b to a }
            }
        }

    private val inputs: Flow<StatsInput> = combine(
        expenseRepository.getAllRecords(),
        accountRepository.getAllAccounts(),
        roles,
        combine(cycles, periodFlow, ::Pair),
        bucketsForPeriod
    ) { records, accounts, roles, (cycles, period), (buckets, assignments) ->
        StatsInput(records, accounts, roles, cycles, period, LocalDate.now(), buckets, assignments)
    }

    val uiState: StateFlow<StatsUiState> = combine(inputs, effectiveFilter) { input, f ->
        StatsUiState(
            loading = false,
            scope = f.scope,
            period = input.period,
            hasCycles = input.cycles.isNotEmpty(),
            today = input.today,
            report = StatsReport.build(input),
            buckets = input.buckets
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    val detail: StateFlow<Pair<DetailRequest, CategoryDetail>?> = combine(inputs, detailRequest) { input, request ->
        request?.let { it to StatsReport.categoryDetail(input, it.title, it.categories) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun formatRupee(amount: Double): String = synchronized(rupeeFormatter) { rupeeFormatter.format(amount) }

    fun selectScope(scope: StatsScope) {
        filter.update { current ->
            val anchor = if (current.scope == StatsScope.SELECT_PERIOD) current.customEndDate ?: current.anchorDate else current.anchorDate
            current.copy(scope = scope, scopeChosen = true, anchorDate = anchor)
        }
    }

    fun previousPeriod() = moveTo(uiState.value.period.previous())

    fun nextPeriod() = moveTo(uiState.value.period.next())

    private fun moveTo(period: StatsPeriod) {
        filter.update { current ->
            when (period) {
                is StatsPeriod.Custom -> if (current.scope == StatsScope.SELECT_PERIOD) {
                    current.copy(anchorDate = period.end, customStartDate = period.start, customEndDate = period.end)
                } else {
                    current // stepping past the first or last cycle
                }
                else -> current.copy(anchorDate = period.start, scopeChosen = true, scope = uiState.value.scope)
            }
        }
    }

    /** Jumps to the period of the current scope containing [date]. */
    fun jumpTo(date: LocalDate) {
        filter.update { it.copy(anchorDate = date, scopeChosen = true, scope = uiState.value.scope) }
    }

    fun selectCustomRange(start: LocalDate, end: LocalDate) {
        val (a, b) = if (start <= end) start to end else end to start
        filter.update { it.copy(scope = StatsScope.SELECT_PERIOD, scopeChosen = true, customStartDate = a, customEndDate = b, anchorDate = b) }
    }

    fun openDetail(request: DetailRequest) {
        detailRequest.value = request
    }

    fun closeDetail() {
        detailRequest.value = null
    }
}
