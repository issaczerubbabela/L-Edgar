package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.issaczerubbabel.ledgar.account.AccountMath
import com.issaczerubbabel.ledgar.data.repository.AccountBook
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
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
    accountRepository: AccountRepository,
) : ViewModel() {

    val lineChartModelProducer = CartesianChartModelProducer()
    val barChartModelProducer = CartesianChartModelProducer()

    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())
    val selectedYearMonth: StateFlow<YearMonth> = _selectedYearMonth.asStateFlow()

    private val monthLabelFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

    private val monthlyStats: StateFlow<List<MonthlyStatsPoint>> = combine(
        selectedYearMonth,
        accountRepository.getAccountBook(),
        ::buildMonthlyStats
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private fun buildMonthlyStats(selectedYm: YearMonth, book: AccountBook): List<MonthlyStatsPoint> {
        val months = (5 downTo 0).map { selectedYm.minusMonths(it.toLong()) }
        val netWorth = AccountMath.netWorthHistory(book.accounts, book.transactions, months)
        val cashFlow = AccountMath.cashFlow(book.accounts, book.transactions, months)
        return months.indices.map { i ->
            MonthlyStatsPoint(
                month = months[i],
                label = months[i].format(monthLabelFormatter),
                balance = netWorth[i].netWorth,
                income = cashFlow[i].moneyIn,
                expense = cashFlow[i].moneyOut,
            )
        }
    }
}
