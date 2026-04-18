package com.issaczerubbabel.ledgar.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.Budget
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.preferences.CashFlowChartStyle
import com.issaczerubbabel.ledgar.data.preferences.ThemePreferenceRepository
import com.issaczerubbabel.ledgar.data.repository.BudgetRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerValueFormatter
import com.patrykandpatrick.vico.core.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: BudgetRepository,
    private val themePreferenceRepository: ThemePreferenceRepository
) : ViewModel() {

    val cashFlowChartModelProducer = CartesianChartModelProducer()

    private val _cashFlowXAxisLabels = MutableStateFlow<List<String>>(emptyList())
    val cashFlowXAxisLabels: StateFlow<List<String>> = _cashFlowXAxisLabels.asStateFlow()

    private val _filterState = MutableStateFlow(StatsFilterState())
    val filterState: StateFlow<StatsFilterState> = _filterState.asStateFlow()

    private val _cashFlowMarkerPoints = MutableStateFlow<List<CashFlowMarkerPoint>>(emptyList())
    private val _useCompressedScale = MutableStateFlow(false)
    val useCompressedScale: StateFlow<Boolean> = _useCompressedScale.asStateFlow()

    private val rupeeFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }

    private val allRecords: StateFlow<List<ExpenseRecord>> = expenseRepository
        .getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashFlowChartStyle: StateFlow<CashFlowChartStyle> = themePreferenceRepository.cashFlowChartStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CashFlowChartStyle.BAR)

    val isLoading: StateFlow<Boolean> = allRecords
        .map { false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val resolvedDateRange: StateFlow<StatsDateRange> = filterState
        .map(::resolveDateRange)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), resolveDateRange(StatsFilterState()))

    private val scopedTransactionsInternal: StateFlow<List<ExpenseRecord>> = combine(
        allRecords,
        resolvedDateRange
    ) { records, range ->
        records.filter { record ->
            val recordDate = parseFlexibleDate(record.date) ?: return@filter false
            recordDate >= range.start && recordDate <= range.end
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTransactions: StateFlow<List<ExpenseRecord>> = scopedTransactionsInternal

    val expenseByCategory: StateFlow<List<CategoryTotal>> = scopedTransactionsInternal
        .map { records -> records.toCategoryTotals(type = "Expense") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeByCategory: StateFlow<List<CategoryTotal>> = scopedTransactionsInternal
        .map { records -> records.toCategoryTotals(type = "Income") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val breakdownCategoryTotals: StateFlow<List<CategoryTotal>> = combine(
        filterState.map { it.breakdownTab }.distinctUntilChanged(),
        expenseByCategory,
        incomeByCategory
    ) { selectedTab, expenseTotals, incomeTotals ->
        when (selectedTab) {
            StatsBreakdownTab.EXPENSE -> expenseTotals
            StatsBreakdownTab.INCOME -> incomeTotals
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashFlowCategoryOptions: StateFlow<List<String>> = scopedTransactionsInternal
        .map { records ->
            val categories = records
                .asSequence()
                .map { it.category.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
                .toList()

            listOf(ALL_CATEGORIES_OPTION) + categories
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(ALL_CATEGORIES_OPTION))

    private val budgetsForAnchorMonth: StateFlow<List<Budget>> = filterState
        .map { YearMonth.from(it.anchorDate).format(MONTH_YEAR_FORMATTER) }
        .distinctUntilChanged()
        .flatMapLatest { monthYear -> budgetRepository.observeBudgets(monthYear) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashFlowMarkerValueFormatter = CartesianMarkerValueFormatter { _, targets ->
        val xIndex = targets.firstOrNull()?.xValue?.toInt()
        val markerPoint = xIndex?.let { _cashFlowMarkerPoints.value.getOrNull(it) }
        if (markerPoint == null) {
            ""
        } else {
            buildString {
                append("Spent ")
                append(formatRupee(markerPoint.expense))
                append("\nIncome ")
                append(formatRupee(markerPoint.income))
                append("\nAvg/day ")
                append(formatRupee(markerPoint.avgPerDay))
                append("\nMax/day ")
                append(formatRupee(markerPoint.maxPerDay))
                append("\n")
                append(markerPoint.label)
            }
        }
    }

    init {
        observeCategorySelectionValidity()
        observeCashFlowChartData()
    }

    fun formatRupee(amount: Double): String {
        return synchronized(rupeeFormatter) {
            rupeeFormatter.format(amount)
        }
    }

    fun chartValueToAmount(value: Double): Double {
        return if (_useCompressedScale.value) {
            value * kotlin.math.abs(value)
        } else {
            value
        }
    }

    fun updateScope(scope: StatsScope) {
        _filterState.update { current ->
            val hasCustomRange = current.customStartDate != null && current.customEndDate != null
            if (scope == StatsScope.SELECT_PERIOD && !hasCustomRange) {
                current.copy(
                    scope = scope,
                    customStartDate = current.anchorDate,
                    customEndDate = current.anchorDate
                )
            } else {
                current.copy(scope = scope)
            }
        }
    }

    fun moveToPreviousPeriod() {
        _filterState.update { current ->
            when (current.scope) {
                StatsScope.WEEKLY -> current.copy(anchorDate = current.anchorDate.minusWeeks(1))
                StatsScope.MONTHLY -> current.copy(anchorDate = current.anchorDate.minusMonths(1))
                StatsScope.YEARLY -> current.copy(anchorDate = current.anchorDate.minusYears(1))
                StatsScope.SELECT_PERIOD -> current.shiftCustomPeriod(backward = true)
            }
        }
    }

    fun moveToNextPeriod() {
        _filterState.update { current ->
            when (current.scope) {
                StatsScope.WEEKLY -> current.copy(anchorDate = current.anchorDate.plusWeeks(1))
                StatsScope.MONTHLY -> current.copy(anchorDate = current.anchorDate.plusMonths(1))
                StatsScope.YEARLY -> current.copy(anchorDate = current.anchorDate.plusYears(1))
                StatsScope.SELECT_PERIOD -> current.shiftCustomPeriod(backward = false)
            }
        }
    }

    fun updateAnchorDate(anchorDate: LocalDate) {
        _filterState.update { it.copy(anchorDate = anchorDate) }
    }

    fun updateCustomPeriodStart(startDate: LocalDate) {
        _filterState.update { current ->
            val endDate = current.customEndDate ?: startDate
            val (orderedStart, orderedEnd) = orderDates(startDate, endDate)
            current.copy(
                customStartDate = orderedStart,
                customEndDate = orderedEnd,
                anchorDate = orderedEnd
            )
        }
    }

    fun updateCustomPeriodEnd(endDate: LocalDate) {
        _filterState.update { current ->
            val startDate = current.customStartDate ?: endDate
            val (orderedStart, orderedEnd) = orderDates(startDate, endDate)
            current.copy(
                customStartDate = orderedStart,
                customEndDate = orderedEnd,
                anchorDate = orderedEnd
            )
        }
    }

    fun updateBreakdownTab(tab: StatsBreakdownTab) {
        _filterState.update { it.copy(breakdownTab = tab) }
    }

    fun updateCashFlowCategory(category: String?) {
        _filterState.update { current ->
            current.copy(cashFlowCategory = normalizeCategory(category))
        }
    }

    fun updateCashFlowGranularity(granularity: CashFlowGranularity) {
        _filterState.update { it.copy(cashFlowGranularity = granularity) }
    }

    private fun observeCategorySelectionValidity() {
        viewModelScope.launch {
            combine(
                filterState,
                cashFlowCategoryOptions
            ) { filter, options ->
                filter.cashFlowCategory to options
            }.collectLatest { (selectedCategory, options) ->
                if (
                    selectedCategory != null &&
                    options.none { it.equals(selectedCategory, ignoreCase = true) }
                ) {
                    _filterState.update { it.copy(cashFlowCategory = null) }
                }
            }
        }
    }

    private fun observeCashFlowChartData() {
        viewModelScope.launch {
            combine(
                allRecords,
                scopedTransactionsInternal,
                filterState,
                budgetsForAnchorMonth,
                cashFlowChartStyle
            ) { allRecordsValue, scopedRecords, filter, budgets, style ->
                val chartSourceRecords = resolveChartSourceRecords(
                    allRecords = allRecordsValue,
                    scopedRecords = scopedRecords,
                    filter = filter
                )
                val chartData = buildCashFlowChartData(
                    records = chartSourceRecords,
                    filter = filter,
                    budgets = budgets
                )
                chartData to style
            }.collectLatest { (chartData, style) ->
                _cashFlowXAxisLabels.value = chartData.labels
                _cashFlowMarkerPoints.value = chartData.markerPoints

                if (chartData.labels.isEmpty()) {
                    _useCompressedScale.value = false
                    return@collectLatest
                }

                val xValues = chartData.labels.indices.map { index -> index.toDouble() }
                val allValues = buildList {
                    addAll(chartData.incomeValues)
                    addAll(chartData.expenseValues)
                    addAll(chartData.avgPerDayLine)
                    addAll(chartData.maxPerDayLine)
                }
                val compressedScale = shouldUseCompressedScale(allValues)
                _useCompressedScale.value = compressedScale

                val incomeSeries = chartData.incomeValues.map { it.toChartY(compressedScale) }
                val expenseSeries = chartData.expenseValues.map { it.toChartY(compressedScale) }
                val avgSeries = chartData.avgPerDayLine.map { it.toChartY(compressedScale) }
                val maxSeries = chartData.maxPerDayLine.map { it.toChartY(compressedScale) }

                cashFlowChartModelProducer.runTransaction {
                    when (style) {
                        CashFlowChartStyle.BAR -> {
                            columnSeries {
                                series(xValues, incomeSeries)
                                series(xValues, expenseSeries)
                            }
                            lineSeries {
                                series(xValues, avgSeries)
                                series(xValues, maxSeries)
                            }
                        }

                        CashFlowChartStyle.LINE -> {
                            lineSeries {
                                series(xValues, incomeSeries)
                                series(xValues, expenseSeries)
                                series(xValues, avgSeries)
                                series(xValues, maxSeries)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun resolveChartSourceRecords(
        allRecords: List<ExpenseRecord>,
        scopedRecords: List<ExpenseRecord>,
        filter: StatsFilterState
    ): List<ExpenseRecord> {
        return if (filter.cashFlowGranularity == CashFlowGranularity.MONTHLY) {
            allRecords
        } else {
            scopedRecords
        }
    }

    private fun buildCashFlowChartData(
        records: List<ExpenseRecord>,
        filter: StatsFilterState,
        budgets: List<Budget>
    ): CashFlowChartData {
        val categoryFilter = normalizeCategory(filter.cashFlowCategory)
        val filteredByCategory = if (categoryFilter == null) {
            records
        } else {
            records.filter { it.category.equals(categoryFilter, ignoreCase = true) }
        }

        val buckets = buildCashFlowBuckets(
            records = filteredByCategory,
            granularity = filter.cashFlowGranularity
        )

        if (buckets.isEmpty()) {
            return CashFlowChartData()
        }

        val avgPerDay = buckets
            .map { bucket -> bucket.expense / bucket.dayCount.coerceAtLeast(1).toDouble() }
            .average()
            .takeIf { !it.isNaN() }
            ?: 0.0

        val maxPerDay = resolveBudgetPerDay(
            filter = filter,
            budgets = budgets
        )

        return CashFlowChartData(
            labels = buckets.map { it.label },
            incomeValues = buckets.map { it.income },
            expenseValues = buckets.map { it.expense },
            avgPerDayLine = List(buckets.size) { avgPerDay },
            maxPerDayLine = List(buckets.size) { maxPerDay },
            markerPoints = buckets.map { bucket ->
                CashFlowMarkerPoint(
                    label = bucket.label,
                    income = bucket.income,
                    expense = bucket.expense,
                    avgPerDay = avgPerDay,
                    maxPerDay = maxPerDay
                )
            }
        )
    }

    private fun buildCashFlowBuckets(
        records: List<ExpenseRecord>,
        granularity: CashFlowGranularity
    ): List<CashFlowBucket> {
        val parsedRecords = records
            .asSequence()
            .mapNotNull { record -> parseFlexibleDate(record.date)?.let { parsedDate -> parsedDate to record } }
            .sortedBy { (date, _) -> date }
            .toList()

        return when (granularity) {
            CashFlowGranularity.DAILY -> {
                parsedRecords
                    .groupBy { (date, _) -> date }
                    .toSortedMap()
                    .map { (day, dayItems) ->
                        CashFlowBucket(
                            label = day.dayOfMonth.toString(),
                            income = dayItems.sumOfType(type = "Income"),
                            expense = dayItems.sumOfType(type = "Expense"),
                            dayCount = 1
                        )
                    }
            }

            CashFlowGranularity.WEEKLY -> {
                val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
                parsedRecords
                    .groupBy { (date, _) -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
                    .toSortedMap()
                    .map { (weekStart, weekItems) ->
                        val weekEnd = weekStart.plusDays(6)
                        CashFlowBucket(
                            label = "${weekStart.format(formatter)} - ${weekEnd.format(formatter)}",
                            income = weekItems.sumOfType(type = "Income"),
                            expense = weekItems.sumOfType(type = "Expense"),
                            dayCount = 7
                        )
                    }
            }

            CashFlowGranularity.MONTHLY -> {
                val formatter = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH)
                parsedRecords
                    .groupBy { (date, _) -> YearMonth.from(date) }
                    .toSortedMap()
                    .map { (yearMonth, monthItems) ->
                        CashFlowBucket(
                            label = yearMonth.format(formatter),
                            income = monthItems.sumOfType(type = "Income"),
                            expense = monthItems.sumOfType(type = "Expense"),
                            dayCount = yearMonth.lengthOfMonth()
                        )
                    }
            }

            CashFlowGranularity.YEARLY -> {
                parsedRecords
                    .groupBy { (date, _) -> date.year }
                    .toSortedMap()
                    .map { (year, yearItems) ->
                        val yearStart = LocalDate.of(year, 1, 1)
                        CashFlowBucket(
                            label = year.toString(),
                            income = yearItems.sumOfType(type = "Income"),
                            expense = yearItems.sumOfType(type = "Expense"),
                            dayCount = if (yearStart.isLeapYear) 366 else 365
                        )
                    }
            }
        }
    }

    private fun resolveBudgetPerDay(filter: StatsFilterState, budgets: List<Budget>): Double {
        if (budgets.isEmpty()) return 0.0

        val categoryBudget = normalizeCategory(filter.cashFlowCategory)?.let { selectedCategory ->
            budgets.firstOrNull { budget ->
                budget.category.equals(selectedCategory, ignoreCase = true)
            }?.amount
        }

        val totalBudget = budgets.firstOrNull {
            it.category == TOTAL_BUDGET_CATEGORY
        }?.amount ?: budgets
            .filterNot { it.category == TOTAL_BUDGET_CATEGORY }
            .sumOf { it.amount }

        val monthlyBudget = categoryBudget ?: totalBudget
        val daysInAnchorMonth = YearMonth.from(filter.anchorDate).lengthOfMonth().coerceAtLeast(1)
        return (monthlyBudget / daysInAnchorMonth).coerceAtLeast(0.0)
    }

    private fun resolveDateRange(filter: StatsFilterState): StatsDateRange {
        val anchor = filter.anchorDate
        return when (filter.scope) {
            StatsScope.WEEKLY -> {
                val weekStart = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                StatsDateRange(
                    start = weekStart,
                    end = weekStart.plusDays(6)
                )
            }

            StatsScope.MONTHLY -> {
                val month = YearMonth.from(anchor)
                StatsDateRange(
                    start = month.atDay(1),
                    end = month.atEndOfMonth()
                )
            }

            StatsScope.YEARLY -> {
                StatsDateRange(
                    start = LocalDate.of(anchor.year, 1, 1),
                    end = LocalDate.of(anchor.year, 12, 31)
                )
            }

            StatsScope.SELECT_PERIOD -> {
                val start = filter.customStartDate ?: anchor
                val end = filter.customEndDate ?: anchor
                val (orderedStart, orderedEnd) = orderDates(start, end)
                StatsDateRange(start = orderedStart, end = orderedEnd)
            }
        }
    }

    private fun categoryColorFor(categoryName: String): Color {
        return CATEGORY_COLORS[kotlin.math.abs(categoryName.hashCode()) % CATEGORY_COLORS.size]
    }

    private fun normalizeCategory(raw: String?): String? {
        val normalized = raw?.trim()?.takeIf { it.isNotBlank() }
        if (normalized == null) return null
        if (normalized.equals(ALL_CATEGORIES_OPTION, ignoreCase = true)) return null
        return normalized
    }

    private fun StatsFilterState.shiftCustomPeriod(backward: Boolean): StatsFilterState {
        val start = customStartDate ?: anchorDate
        val end = customEndDate ?: anchorDate
        val days = daysInclusive(start, end).toLong()
        val delta = if (backward) -days else days
        return copy(
            anchorDate = anchorDate.plusDays(delta),
            customStartDate = start.plusDays(delta),
            customEndDate = end.plusDays(delta)
        )
    }

    private fun List<ExpenseRecord>.toCategoryTotals(type: String): List<CategoryTotal> {
        return asSequence()
            .filter { it.type.trim().equals(type, ignoreCase = true) }
            .groupBy { it.category.ifBlank { "Uncategorized" } }
            .map { (categoryName, items) ->
                CategoryTotal(
                    categoryName = categoryName,
                    totalAmount = items.sumOf { it.amount },
                    assignedColor = categoryColorFor(categoryName)
                )
            }
            .sortedByDescending { it.totalAmount }
            .toList()
    }

    private fun List<Pair<LocalDate, ExpenseRecord>>.sumOfType(type: String): Double {
        return sumOf { (_, record) ->
            if (record.type.trim().equals(type, ignoreCase = true)) record.amount else 0.0
        }
    }

    private fun orderDates(first: LocalDate, second: LocalDate): Pair<LocalDate, LocalDate> {
        return if (first <= second) first to second else second to first
    }

    private fun daysInclusive(start: LocalDate, end: LocalDate): Int {
        if (end < start) return 1
        return ChronoUnit.DAYS.between(start, end).toInt() + 1
    }

    private fun shouldUseCompressedScale(values: List<Double>): Boolean {
        val positives = values.filter { it > 0.0 }
        if (positives.size < 3) return false

        val sorted = positives.sorted()
        val median = sorted[sorted.size / 2]
        val max = sorted.last()
        return median > 0.0 && max >= median * 6.0
    }

    private fun Double.toChartY(compressed: Boolean): Double {
        return if (compressed) {
            kotlin.math.sign(this) * kotlin.math.sqrt(kotlin.math.abs(this))
        } else {
            this
        }
    }

    private data class CashFlowBucket(
        val label: String,
        val income: Double,
        val expense: Double,
        val dayCount: Int
    )

    private data class CashFlowMarkerPoint(
        val label: String,
        val income: Double,
        val expense: Double,
        val avgPerDay: Double,
        val maxPerDay: Double
    )

    private data class CashFlowChartData(
        val labels: List<String> = emptyList(),
        val incomeValues: List<Double> = emptyList(),
        val expenseValues: List<Double> = emptyList(),
        val avgPerDayLine: List<Double> = emptyList(),
        val maxPerDayLine: List<Double> = emptyList(),
        val markerPoints: List<CashFlowMarkerPoint> = emptyList()
    )

    companion object {
        const val ALL_CATEGORIES_OPTION = "All Categories"
        private const val TOTAL_BUDGET_CATEGORY = "__TOTAL__"
        private val MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM")

        private val CATEGORY_COLORS = listOf(
            Color(0xFFFF1744),
            Color(0xFFFF9100),
            Color(0xFFFFEA00),
            Color(0xFF00E676),
            Color(0xFF00E5FF),
            Color(0xFF2979FF),
            Color(0xFFD500F9),
            Color(0xFFFF4081),
            Color(0xFF76FF03),
            Color(0xFF651FFF)
        )
    }
}

private val CartesianMarker.Target.xValue: Float
    get() = when (this) {
        is ColumnCartesianLayerMarkerTarget -> columns.firstOrNull()?.entry?.x?.toFloat() ?: 0f
        is LineCartesianLayerMarkerTarget -> points.firstOrNull()?.entry?.x?.toFloat() ?: 0f
        else -> 0f
    }
