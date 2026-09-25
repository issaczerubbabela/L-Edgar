package com.issaczerubbabel.ledgar.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.preferences.CashFlowChartStyle
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Wires the repositories to [StatsReport]; all the arithmetic lives there. */
@HiltViewModel
class StatsViewModel @Inject constructor(
    expenseRepository: ExpenseRepository,
    accountRepository: AccountRepository,
    dropdownOptionRepository: DropdownOptionRepository,
    bucketBudgetRepository: BucketBudgetRepository,
    themePreferenceRepository: ThemePreferenceRepository
) : ViewModel() {

    private val _filterState = MutableStateFlow(StatsFilterState())
    val filterState: StateFlow<StatsFilterState> = _filterState.asStateFlow()

    private val rupeeFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }

    private val categoryOptions = combine(
        dropdownOptionRepository.getOptionsByType("EXPENSE_CATEGORY"),
        dropdownOptionRepository.getOptionsByType("INCOME_CATEGORY"),
        dropdownOptionRepository.getOptionsByType("ACCOUNT_GROUP")
    ) { expense, income, groups -> expense + income + groups }

    private val records = expenseRepository.getAllRecords()

    val cashFlowChartStyle: StateFlow<CashFlowChartStyle> = themePreferenceRepository.cashFlowChartStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CashFlowChartStyle.BAR)

    val resolvedDateRange: StateFlow<StatsDateRange> = filterState
        .map { it.period.range }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsFilterState().period.range)

    /** Null until the first report is ready. */
    private val reportOrNull: StateFlow<Pair<StatsReportUi, List<DropdownOption>>?> = combine(
        records,
        accountRepository.getAllAccounts(),
        categoryOptions,
        bucketBudgetRepository.observeAllCycles(),
        filterState
    ) { records, accounts, options, cycles, filter ->
        StatsReport.build(
            StatsInput(
                records = records,
                accounts = accounts,
                roles = StatsRoles.from(options),
                cycles = cycles,
                period = filter.period,
                today = LocalDate.now(),
                timelineCategory = filter.cashFlowCategory
            )
        ) to options
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isLoading: StateFlow<Boolean> = reportOrNull
        .map { it == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val report: StateFlow<StatsReportUi> = reportOrNull
        .map { it?.first ?: StatsReportUi() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsReportUi())

    val breakdownCategoryTotals: StateFlow<List<CategoryTotal>> = combine(reportOrNull, filterState) { pair, filter ->
        val (report, options) = pair ?: return@combine emptyList()
        when (filter.breakdownTab) {
            StatsBreakdownTab.EXPENSE -> report.expenseCategories.withColors(options, "EXPENSE_CATEGORY")
            StatsBreakdownTab.INCOME -> report.incomeCategories.withColors(options, "INCOME_CATEGORY")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashFlowCategoryOptions: StateFlow<List<String>> = reportOrNull
        .map { pair ->
            val report = pair?.first ?: return@map listOf(ALL_CATEGORIES_OPTION)
            listOf(ALL_CATEGORIES_OPTION) +
                (report.expenseCategories + report.incomeCategories).map { it.category }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(ALL_CATEGORIES_OPTION))

    fun formatRupee(amount: Double): String = synchronized(rupeeFormatter) { rupeeFormatter.format(amount) }

    fun updateScope(scope: StatsScope) {
        _filterState.update { current ->
            val hasCustomRange = current.customStartDate != null && current.customEndDate != null
            if (scope == StatsScope.SELECT_PERIOD && !hasCustomRange) {
                current.copy(scope = scope, customStartDate = current.anchorDate, customEndDate = current.anchorDate)
            } else {
                current.copy(scope = scope)
            }
        }
    }

    fun moveToPreviousPeriod() = moveTo(filterState.value.period.previous())

    fun moveToNextPeriod() = moveTo(filterState.value.period.next())

    private fun moveTo(period: StatsPeriod) {
        _filterState.update { current ->
            if (period is StatsPeriod.Custom) {
                current.copy(anchorDate = period.end, customStartDate = period.start, customEndDate = period.end)
            } else {
                current.copy(anchorDate = period.start)
            }
        }
    }

    fun updateAnchorDate(anchorDate: LocalDate) {
        _filterState.update { it.copy(anchorDate = anchorDate) }
    }

    fun updateCustomPeriodStart(startDate: LocalDate) = updateCustomRange(startDate, filterState.value.customEndDate ?: startDate)

    fun updateCustomPeriodEnd(endDate: LocalDate) = updateCustomRange(filterState.value.customStartDate ?: endDate, endDate)

    private fun updateCustomRange(a: LocalDate, b: LocalDate) {
        val (start, end) = if (a <= b) a to b else b to a
        _filterState.update { it.copy(customStartDate = start, customEndDate = end, anchorDate = end) }
    }

    fun updateBreakdownTab(tab: StatsBreakdownTab) {
        _filterState.update { it.copy(breakdownTab = tab) }
    }

    fun updateCashFlowCategory(category: String?) {
        val normalized = category?.trim()?.takeIf { it.isNotBlank() && !it.equals(ALL_CATEGORIES_OPTION, ignoreCase = true) }
        _filterState.update { it.copy(cashFlowCategory = normalized) }
    }

    /**
     * Colours follow the Category, not its rank: each Category keeps its place in the user's own
     * Category order. Only eight colours stay distinguishable, so any Category past the eighth is grey.
     */
    private fun List<CategoryAmount>.withColors(options: List<DropdownOption>, type: String): List<CategoryTotal> {
        val order = options.filter { it.optionType == type }
            .sortedBy { it.displayOrder }
            .map { it.name.trim().lowercase() }
        val present = map { it.category }
            .sortedWith(compareBy({ order.indexOf(it.lowercase()).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it }))
        return map { amount ->
            val slot = present.indexOf(amount.category)
            CategoryTotal(amount.category, amount.amount, CATEGORY_COLORS.getOrElse(slot) { OVERFLOW_COLOR })
        }
    }

    companion object {
        const val ALL_CATEGORIES_OPTION = "All Categories"

        /** Neighbouring colours in this order stay apart for red-green colour blindness. */
        private val CATEGORY_COLORS = listOf(
            Color(0xFF3987E5),
            Color(0xFFD95926),
            Color(0xFF199E70),
            Color(0xFFC98500),
            Color(0xFFD55181),
            Color(0xFF008300),
            Color(0xFF9085E9),
            Color(0xFFE66767)
        )
        private val OVERFLOW_COLOR = Color(0xFF8F94A0)
    }
}
