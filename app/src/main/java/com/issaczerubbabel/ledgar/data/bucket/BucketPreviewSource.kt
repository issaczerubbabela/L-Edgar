package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.data.repository.BucketBudgetRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.data.repository.FundRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import javax.inject.Inject

/** Feeds the bucket strip on Add Transaction and Quick Add, built the same way as the Budget tab, Funds included,
 * so the daily reminder's Left to spend agrees with it too. */
class BucketPreviewSource @Inject constructor(
    private val budgets: BucketBudgetRepository,
    private val expenses: ExpenseRepository,
    private val funds: FundRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val context: Flow<BucketPreviewContext?> = budgets.observeRunningCycle().flatMapLatest { cycle ->
        if (cycle == null) {
            flowOf(null)
        } else {
            combine(
                budgets.observeBuckets(cycle.id),
                budgets.observeCategoryAssignments(cycle.id),
                expenses.getAllRecords(),
                funds.observeSnapshot()
            ) { buckets, assignments, records, snapshot ->
                BucketPreviewContext(
                    summary = CycleSummaryBuilder.build(cycle, buckets, assignments, records, LocalDate.now(), snapshot),
                    assignments = assignments
                )
            }
        }
    }
}
