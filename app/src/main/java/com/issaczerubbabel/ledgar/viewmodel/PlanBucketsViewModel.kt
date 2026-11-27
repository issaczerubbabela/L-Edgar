package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.bucket.CycleSummary
import com.issaczerubbabel.ledgar.data.bucket.CycleSummaryBuilder
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.Fund
import com.issaczerubbabel.ledgar.data.repository.BucketBudgetRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.data.repository.FundRepository
import com.issaczerubbabel.ledgar.fund.FundsSnapshot
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

/** One Fund in Plan this cycle's Set aside group, with its Planned contribution (0 when none). */
data class PlannedContributionRow(val fund: Fund, val planned: Double)

data class PlanBucketsUiState(
    val isLoading: Boolean = true,
    /** The running cycle being planned, or null when none has been started. */
    val summary: CycleSummary? = null,
    /** How many categories are routed into each bucket, including ones with no spend yet. */
    val categoryCountByBucket: Map<Long, Int> = emptyMap(),
    /** Every active Fund, in Fund order. Empty without Funds. */
    val contributions: List<PlannedContributionRow> = emptyList()
)

/** Plan this cycle for [cycle]: its Buckets, and a Planned contribution row for every active Fund. Pure. */
fun planBuckets(
    cycle: BudgetCycle,
    buckets: List<BudgetBucket>,
    assignments: List<BucketCategory>,
    records: List<ExpenseRecord>,
    funds: FundsSnapshot,
    today: LocalDate
): PlanBucketsUiState {
    val plans = funds.plansFor(cycle.id).associate { it.fundId to it.plannedAmount }
    return PlanBucketsUiState(
        isLoading = false,
        summary = CycleSummaryBuilder.build(cycle, buckets, assignments, records, today, funds),
        categoryCountByBucket = assignments.groupingBy { it.bucketId }.eachCount(),
        contributions = funds.activeFunds
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .map { PlannedContributionRow(it, plans[it.id] ?: 0.0) }
    )
}

/** A stepper tap on a Planned contribution, never below zero. */
fun steppedContribution(current: Double, delta: Double): Double = (current + delta).coerceAtLeast(0.0)

/** Planning happens on payday, against the running cycle: split what you have into buckets and Funds. */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PlanBucketsViewModel @Inject constructor(
    private val budgets: BucketBudgetRepository,
    private val expenses: ExpenseRepository,
    private val funds: FundRepository
) : ViewModel() {

    val uiState: StateFlow<PlanBucketsUiState> = budgets.observeRunningCycle()
        .flatMapLatest { cycle ->
            if (cycle == null) {
                flowOf(PlanBucketsUiState(isLoading = false))
            } else {
                combine(
                    budgets.observeBuckets(cycle.id),
                    budgets.observeCategoryAssignments(cycle.id),
                    expenses.getAllRecords(),
                    funds.observeSnapshot()
                ) { buckets, assignments, records, snapshot ->
                    planBuckets(cycle, buckets, assignments, records, snapshot, LocalDate.now())
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

    fun adjustContribution(fundId: Long, delta: Double) {
        val row = uiState.value.contributions.firstOrNull { it.fund.id == fundId } ?: return
        setContribution(fundId, steppedContribution(row.planned, delta))
    }

    /** Zero removes the Planned contribution. */
    fun setContribution(fundId: Long, amount: Double) {
        val cycle = uiState.value.summary?.cycle ?: return
        viewModelScope.launch { funds.setPlannedContribution(fundId, cycle.id, amount.coerceAtLeast(0.0)) }
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
