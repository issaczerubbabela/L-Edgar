package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.bucket.CycleSummary
import com.issaczerubbabel.ledgar.data.bucket.CycleSummaryBuilder
import com.issaczerubbabel.ledgar.data.repository.BucketBudgetRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class BudgetUiState(
    val isLoading: Boolean = true,
    val summary: CycleSummary? = null,
    /** 0 is the newest cycle; higher numbers step back through history. */
    val cycleIndex: Int = 0,
    val cycleCount: Int = 0
) {
    val hasCycles: Boolean get() = cycleCount > 0
    val canGoOlder: Boolean get() = cycleIndex < cycleCount - 1
    val canGoNewer: Boolean get() = cycleIndex > 0

    /** A closed cycle is history: frozen numbers, no pace tick, no actions. */
    val isRunning: Boolean get() = summary?.cycle?.closedAt == null
}

/** The Budget home screen: one cycle at a time, the running one first, older ones behind it. */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel @Inject constructor(
    private val budgets: BucketBudgetRepository,
    private val expenses: ExpenseRepository
) : ViewModel() {

    private val selectedIndex = MutableStateFlow(0)

    val uiState: StateFlow<BudgetUiState> = combine(budgets.observeAllCycles(), selectedIndex) { cycles, index ->
        cycles to index
    }
        .flatMapLatest { (cycles, index) ->
            if (cycles.isEmpty()) {
                flowOf(BudgetUiState(isLoading = false))
            } else {
                val position = index.coerceIn(0, cycles.lastIndex)
                val cycle = cycles[position]
                combine(
                    budgets.observeBuckets(cycle.id),
                    budgets.observeCategoryAssignments(cycle.id),
                    expenses.getAllRecords()
                ) { buckets, assignments, records ->
                    BudgetUiState(
                        isLoading = false,
                        summary = CycleSummaryBuilder.build(cycle, buckets, assignments, records, LocalDate.now()),
                        cycleIndex = position,
                        cycleCount = cycles.size
                    )
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetUiState())

    fun showOlderCycle() {
        val state = uiState.value
        if (state.canGoOlder) selectedIndex.value = state.cycleIndex + 1
    }

    fun showNewerCycle() {
        val state = uiState.value
        if (state.canGoNewer) selectedIndex.value = state.cycleIndex - 1
    }
}
