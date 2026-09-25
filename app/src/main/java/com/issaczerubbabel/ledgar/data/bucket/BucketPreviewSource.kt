package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.data.repository.BucketBudgetRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import javax.inject.Inject

/** Feeds the bucket strip on Add Transaction and Quick Add, built the same way as the Budget tab. */
class BucketPreviewSource @Inject constructor(
    private val budgets: BucketBudgetRepository,
    private val expenses: ExpenseRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val context: Flow<BucketPreviewContext?> = budgets.observeRunningCycle().flatMapLatest { cycle ->
        if (cycle == null) {
            flowOf(null)
        } else {
            combine(
                budgets.observeBuckets(cycle.id),
                budgets.observeCategoryAssignments(cycle.id),
                expenses.getAllRecords()
            ) { buckets, assignments, records ->
                BucketPreviewContext(
                    summary = CycleSummaryBuilder.build(cycle, buckets, assignments, records, LocalDate.now()),
                    assignments = assignments
                )
            }
        }
    }
}
