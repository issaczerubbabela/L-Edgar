package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.bucket.CycleSummary
import com.issaczerubbabel.ledgar.data.bucket.CycleSummaryBuilder
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.repository.BucketBudgetRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PlanBucketsUiState(
    val isLoading: Boolean = true,
    /** The running cycle being planned, or null when none has been started. */
    val summary: CycleSummary? = null,
    /** How many categories are routed into each bucket, including ones with no spend yet. */
    val categoryCountByBucket: Map<Long, Int> = emptyMap()
)

/** Planning happens on payday, against the running cycle: split what you have into buckets. */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PlanBucketsViewModel @Inject constructor(
    private val budgets: BucketBudgetRepository,
    private val expenses: ExpenseRepository
) : ViewModel() {

    val uiState: StateFlow<PlanBucketsUiState> = budgets.observeRunningCycle()
        .flatMapLatest { cycle ->
            if (cycle == null) {
                flowOf(PlanBucketsUiState(isLoading = false))
            } else {
                combine(
                    budgets.observeBuckets(cycle.id),
                    budgets.observeCategoryAssignments(cycle.id),
                    expenses.getAllRecords()
                ) { buckets, assignments, records ->
                    PlanBucketsUiState(
                        isLoading = false,
                        summary = CycleSummaryBuilder.build(cycle, buckets, assignments, records, LocalDate.now()),
                        categoryCountByBucket = assignments.groupingBy { it.bucketId }.eachCount()
                    )
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlanBucketsUiState())

    fun adjustAllocation(bucketId: Long, delta: Double) {
        val bucket = uiState.value.summary?.buckets?.firstOrNull { it.bucket.id == bucketId }?.bucket ?: return
        setAllocation(bucketId, bucket.allocatedAmount + delta)
    }

    fun setAllocation(bucketId: Long, amount: Double) {
        val bucket = uiState.value.summary?.buckets?.firstOrNull { it.bucket.id == bucketId }?.bucket ?: return
        viewModelScope.launch { budgets.updateBucket(bucket.copy(allocatedAmount = amount.coerceAtLeast(0.0))) }
    }

    fun setSpendable(amount: Double) {
        val cycle = uiState.value.summary?.cycle ?: return
        viewModelScope.launch { budgets.updateCycle(cycle.copy(spendableAmount = amount.coerceAtLeast(0.0))) }
    }

    fun addBucket(name: String) {
        val summary = uiState.value.summary ?: return
        val trimmed = name.trim().ifBlank { return }
        val used = summary.buckets.map { it.bucket.colorIndex }.toSet()
        viewModelScope.launch {
            budgets.insertBucket(
                BudgetBucket(
                    cycleId = summary.cycle.id,
                    name = trimmed,
                    colorIndex = (0 until BudgetBucket.COLOR_COUNT).firstOrNull { it !in used }
                        ?: (used.size % BudgetBucket.COLOR_COUNT),
                    allocatedAmount = 0.0,
                    sortOrder = (summary.buckets.maxOfOrNull { it.bucket.sortOrder } ?: -1) + 1
                )
            )
        }
    }
}
