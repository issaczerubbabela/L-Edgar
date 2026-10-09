package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The Net worth screen (was Overall Stats): every figure comes from [netWorthScreen]. */
@HiltViewModel
class NetWorthViewModel @Inject constructor(
    accountRepository: AccountRepository
) : ViewModel() {

    val historyChart = CartesianChartModelProducer()
    val cashFlowChart = CartesianChartModelProducer()

    private val period = MutableStateFlow(NetWorthPeriod.SIX_MONTHS)

    val uiState: StateFlow<NetWorthUiState> = combine(accountRepository.getAccountBook(), period) { book, chosen ->
        netWorthScreen(book, LocalDate.now(), chosen)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetWorthUiState())

    init {
        viewModelScope.launch {
            uiState.collectLatest { state ->
                if (state.showHistoryChart) {
                    historyChart.runTransaction {
                        lineSeries { series(state.history.indices.map(Int::toDouble), state.history.map { it.netWorth }) }
                    }
                }
                if (state.cashFlow.isNotEmpty()) {
                    val x = state.cashFlow.indices.map(Int::toDouble)
                    cashFlowChart.runTransaction {
                        columnSeries {
                            series(x, state.cashFlow.map { it.moneyIn })
                            series(x, state.cashFlow.map { it.moneyOut })
                        }
                    }
                }
            }
        }
    }

    fun setPeriod(value: NetWorthPeriod) {
        period.value = value
    }
}
