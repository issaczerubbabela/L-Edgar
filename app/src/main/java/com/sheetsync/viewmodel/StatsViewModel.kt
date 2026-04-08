package com.sheetsync.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheetsync.data.local.entity.AccountRecord
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.repository.AccountRepository
import com.sheetsync.data.repository.ExpenseRepository
import com.sheetsync.util.parseFlexibleDate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _filterState = MutableStateFlow(StatsFilterState())
    val filterState: StateFlow<StatsFilterState> = _filterState.asStateFlow()

    val accountGroupOptions: StateFlow<List<String>> = accountRepository.getAllAccounts()
        .map { accounts ->
            accounts
                .map { it.groupName.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isLoading: StateFlow<Boolean> = combine(
        expenseRepository.getAllRecords(),
        accountRepository.getAllAccounts()
    ) { _, _ -> false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val filteredTransactions: StateFlow<List<ExpenseRecord>> = combine(
        filterState,
        expenseRepository.getAllRecords(),
        accountRepository.getAllAccounts()
    ) { filter, transactions, accounts ->
        applyFilters(filter, transactions, accounts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseByCategory: StateFlow<List<CategoryTotal>> = filteredTransactions
        .map { records ->
            records.asSequence()
                .filter { it.type.equals("Expense", ignoreCase = true) }
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashFlowOverTime: StateFlow<Map<String, Pair<Double, Double>>> = combine(
        filteredTransactions,
        filterState
    ) { records, filter ->
        buildCashFlowOverTime(records, filter.timeframe)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun updateTimeframe(timeframe: StatsTimeframe) {
        _filterState.update { it.copy(timeframe = timeframe) }
    }

    fun updateAccountGroup(accountGroupId: String?) {
        _filterState.update { it.copy(accountGroupId = accountGroupId) }
    }

    fun updateTransactionType(transactionType: StatsTransactionType) {
        _filterState.update { it.copy(transactionType = transactionType) }
    }

    private fun applyFilters(
        filter: StatsFilterState,
        transactions: List<ExpenseRecord>,
        accounts: List<AccountRecord>
    ): List<ExpenseRecord> {
        val today = LocalDate.now()
        val (rangeStart, rangeEnd) = dateRangeFor(filter.timeframe, today)

        val accountIdsInGroup: Set<Long>? = filter.accountGroupId
            ?.let { groupId ->
                accounts.asSequence()
                    .filter { it.groupName == groupId }
                    .map { it.id }
                    .toSet()
            }

        return transactions.filter { record ->
            if (!matchesTransactionType(filter.transactionType, record.type)) {
                return@filter false
            }

            if (rangeStart != null && rangeEnd != null) {
                val recordDate = parseFlexibleDate(record.date) ?: return@filter false
                if (recordDate < rangeStart || recordDate > rangeEnd) {
                    return@filter false
                }
            }

            if (accountIdsInGroup != null) {
                val relatedAccountIds = setOfNotNull(record.accountId, record.fromAccountId, record.toAccountId)
                if (relatedAccountIds.none { it in accountIdsInGroup }) {
                    return@filter false
                }
            }

            true
        }
    }

    private fun matchesTransactionType(selected: StatsTransactionType, rawType: String): Boolean {
        return when (selected) {
            StatsTransactionType.EXPENSE -> rawType.equals("Expense", ignoreCase = true)
            StatsTransactionType.INCOME -> rawType.equals("Income", ignoreCase = true)
            StatsTransactionType.BOTH -> {
                rawType.equals("Expense", ignoreCase = true) || rawType.equals("Income", ignoreCase = true)
            }
        }
    }

    private fun dateRangeFor(timeframe: StatsTimeframe, today: LocalDate): Pair<LocalDate?, LocalDate?> {
        return when (timeframe) {
            StatsTimeframe.THIS_MONTH -> YearMonth.from(today).atDay(1) to today
            StatsTimeframe.LAST_MONTH -> {
                val lastMonth = YearMonth.from(today).minusMonths(1)
                lastMonth.atDay(1) to lastMonth.atEndOfMonth()
            }
            StatsTimeframe.LAST_3_MONTHS -> {
                val start = YearMonth.from(today).minusMonths(2).atDay(1)
                start to today
            }
            StatsTimeframe.YTD -> LocalDate.of(today.year, 1, 1) to today
            StatsTimeframe.ALL_TIME -> null to null
        }
    }

    private fun buildCashFlowOverTime(
        records: List<ExpenseRecord>,
        timeframe: StatsTimeframe
    ): Map<String, Pair<Double, Double>> {
        return when (timeframe) {
            StatsTimeframe.THIS_MONTH,
            StatsTimeframe.LAST_MONTH -> buildDailyCashFlow(records, dayLabelMode = DayLabelMode.DAY_NUMBER)

            StatsTimeframe.LAST_3_MONTHS -> buildDailyCashFlow(records, dayLabelMode = DayLabelMode.MONTH_DAY)

            StatsTimeframe.YTD,
            StatsTimeframe.ALL_TIME -> buildMonthlyCashFlow(records)
        }
    }

    private fun buildDailyCashFlow(
        records: List<ExpenseRecord>,
        dayLabelMode: DayLabelMode
    ): Map<String, Pair<Double, Double>> {
        val totalsByDate = linkedMapOf<LocalDate, MutableCashFlow>()

        records
            .asSequence()
            .mapNotNull { record -> parseFlexibleDate(record.date)?.let { it to record } }
            .sortedBy { (date, _) -> date }
            .forEach { (date, record) ->
                val bucket = totalsByDate.getOrPut(date) { MutableCashFlow() }
                when {
                    record.type.equals("Income", ignoreCase = true) -> bucket.income += record.amount
                    record.type.equals("Expense", ignoreCase = true) -> bucket.expense += record.amount
                }
            }

        val monthDayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

        return totalsByDate.entries.associate { (date, flow) ->
            val label = when (dayLabelMode) {
                DayLabelMode.DAY_NUMBER -> "Day ${date.dayOfMonth}"
                DayLabelMode.MONTH_DAY -> date.format(monthDayFormatter)
            }
            label to (flow.income to flow.expense)
        }
    }

    private fun buildMonthlyCashFlow(records: List<ExpenseRecord>): Map<String, Pair<Double, Double>> {
        val totalsByMonth = linkedMapOf<YearMonth, MutableCashFlow>()

        records
            .asSequence()
            .mapNotNull { record -> parseFlexibleDate(record.date)?.let { it to record } }
            .sortedBy { (date, _) -> date }
            .forEach { (date, record) ->
                val month = YearMonth.from(date)
                val bucket = totalsByMonth.getOrPut(month) { MutableCashFlow() }
                when {
                    record.type.equals("Income", ignoreCase = true) -> bucket.income += record.amount
                    record.type.equals("Expense", ignoreCase = true) -> bucket.expense += record.amount
                }
            }

        val distinctYears = totalsByMonth.keys.map { it.year }.toSet().size
        val formatter = if (distinctYears > 1) {
            DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH)
        } else {
            DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
        }

        return totalsByMonth.entries.associate { (yearMonth, flow) ->
            yearMonth.format(formatter) to (flow.income to flow.expense)
        }
    }

    private fun categoryColorFor(categoryName: String): Color {
        return CATEGORY_COLORS[kotlin.math.abs(categoryName.hashCode()) % CATEGORY_COLORS.size]
    }

    private data class MutableCashFlow(
        var income: Double = 0.0,
        var expense: Double = 0.0
    )

    private enum class DayLabelMode {
        DAY_NUMBER,
        MONTH_DAY
    }

    companion object {
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
