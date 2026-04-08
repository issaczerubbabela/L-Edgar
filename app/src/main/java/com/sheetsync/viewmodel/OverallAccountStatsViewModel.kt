package com.sheetsync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.sheetsync.data.local.dao.AccountDao
import com.sheetsync.data.local.dao.ExpenseDao
import com.sheetsync.data.local.entity.AccountRecord
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.util.parseFlexibleDate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class MonthlyStatsPoint(
    val month: YearMonth,
    val label: String,
    val balance: Double,
    val income: Double,
    val expense: Double,
)

data class BalancePoint(
    val month: YearMonth,
    val label: String,
    val balance: Double,
)

data class IncomeExpensePoint(
    val month: YearMonth,
    val label: String,
    val income: Double,
    val expense: Double,
)

@HiltViewModel
class OverallAccountStatsViewModel @Inject constructor(
    accountDao: AccountDao,
    expenseDao: ExpenseDao,
) : ViewModel() {

    val lineChartModelProducer = CartesianChartModelProducer()
    val barChartModelProducer = CartesianChartModelProducer()

    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())
    val selectedYearMonth: StateFlow<YearMonth> = _selectedYearMonth.asStateFlow()

    private val monthLabelFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

    private val monthlyStats: StateFlow<List<MonthlyStatsPoint>> = combine(
        selectedYearMonth,
        accountDao.getAllAccounts(),
        expenseDao.getAllRecords(),
    ) { selectedYm, accounts, records ->
        buildMonthlyStats(selectedYm, accounts, records)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historicalBalance: StateFlow<List<BalancePoint>> = monthlyStats
        .map { points -> points.map { BalancePoint(it.month, it.label, it.balance) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyIncomeExpense: StateFlow<List<IncomeExpensePoint>> = monthlyStats
        .map { points -> points.map { IncomeExpensePoint(it.month, it.label, it.income, it.expense) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            monthlyStats.collectLatest { points ->
                if (points.isEmpty()) return@collectLatest

                val xValues = points.indices.map(Int::toDouble)
                val lineValues = points.map { it.balance }
                val incomeValues = points.map { it.income }
                val expenseValues = points.map { it.expense }

                lineChartModelProducer.runTransaction {
                    lineSeries { series(xValues, lineValues) }
                }

                barChartModelProducer.runTransaction {
                    columnSeries {
                        series(xValues, incomeValues)
                        series(xValues, expenseValues)
                    }
                }
            }
        }
    }

    fun prevMonth() {
        _selectedYearMonth.value = _selectedYearMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        _selectedYearMonth.value = _selectedYearMonth.value.plusMonths(1)
    }

    fun setMonthYear(year: Int, month: Int) {
        _selectedYearMonth.value = YearMonth.of(year, month.coerceIn(1, 12))
    }

    private fun buildMonthlyStats(
        selectedYm: YearMonth,
        accounts: List<AccountRecord>,
        records: List<ExpenseRecord>,
    ): List<MonthlyStatsPoint> {
        val months = (5 downTo 0).map { selectedYm.minusMonths(it.toLong()) }
        val firstMonth = months.firstOrNull() ?: selectedYm
        val monthSet = months.toSet()
        val asOfDateByAccount = accounts.associate { it.id to it.initialBalanceDate }

        val startingBalance = accounts.sumOf { it.initialBalance }
        var preRangeNet = 0.0

        val netByMonth = mutableMapOf<YearMonth, Double>()
        val incomeByMonth = mutableMapOf<YearMonth, Double>()
        val expenseByMonth = mutableMapOf<YearMonth, Double>()

        records.forEach { record ->
            val date = parseFlexibleDate(record.date) ?: return@forEach
            val recordMonth = YearMonth.from(date)
            val deltas = accountDeltas(record, asOfDateByAccount)
            if (deltas.isEmpty()) return@forEach

            val netDelta = deltas.sum()
            val positiveDelta = deltas.filter { it > 0.0 }.sum()
            val negativeDeltaAbs = deltas.filter { it < 0.0 }.sumOf { kotlin.math.abs(it) }

            if (recordMonth < firstMonth) {
                preRangeNet += netDelta
                return@forEach
            }

            if (recordMonth !in monthSet) return@forEach
            netByMonth[recordMonth] = (netByMonth[recordMonth] ?: 0.0) + netDelta
            incomeByMonth[recordMonth] = (incomeByMonth[recordMonth] ?: 0.0) + positiveDelta
            expenseByMonth[recordMonth] = (expenseByMonth[recordMonth] ?: 0.0) + negativeDeltaAbs
        }

        var runningBalance = startingBalance + preRangeNet

        return months.map { ym ->
            val monthNet = netByMonth[ym] ?: 0.0
            val income = incomeByMonth[ym] ?: 0.0
            val expense = expenseByMonth[ym] ?: 0.0
            runningBalance += monthNet
            MonthlyStatsPoint(
                month = ym,
                label = ym.format(monthLabelFormatter),
                balance = runningBalance,
                income = income,
                expense = expense,
            )
        }
    }

    private fun accountDeltas(
        record: ExpenseRecord,
        asOfDateByAccount: Map<Long, String>
    ): List<Double> {
        fun isAfterAsOf(accountId: Long?): Boolean {
            if (accountId == null) return false
            val asOf = asOfDateByAccount[accountId] ?: "1970-01-01"
            val txDate = parseFlexibleDate(record.date)
            val asOfDate = parseFlexibleDate(asOf)
            return when {
                txDate != null && asOfDate != null -> !txDate.isBefore(asOfDate)
                else -> record.date >= asOf
            }
        }

        val deltas = mutableListOf<Double>()
        when {
            record.type.equals("Income", ignoreCase = true) -> {
                val target = record.toAccountId ?: record.accountId
                if (isAfterAsOf(target)) deltas += record.amount
            }

            record.type.equals("Expense", ignoreCase = true) -> {
                val source = record.fromAccountId ?: record.accountId
                if (isAfterAsOf(source)) deltas += -record.amount
            }

            record.type.equals("Transfer", ignoreCase = true) -> {
                if (isAfterAsOf(record.fromAccountId)) deltas += -record.amount
                if (isAfterAsOf(record.toAccountId)) deltas += record.amount
            }
        }
        return deltas
    }
}
