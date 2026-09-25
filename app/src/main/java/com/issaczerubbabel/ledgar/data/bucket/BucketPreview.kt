package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import java.time.LocalDate

/** The running cycle as the Add Transaction screens need it: its summary and category routing. */
data class BucketPreviewContext(
    val summary: CycleSummary,
    val assignments: List<BucketCategory>
)

/** How a bucket looks once the transaction being entered is counted in it. */
data class BucketPreview(
    val bucket: BudgetBucket,
    val spentWithDraft: Double,
    /** 0..1, where an even spend would be today. */
    val paceFraction: Float
) {
    val allocated: Double get() = bucket.allocatedAmount
    val remaining: Double get() = allocated - spentWithDraft
    val isOver: Boolean get() = spentWithDraft > allocated

    /** Uncapped share of the allocation spent, same rules as [BucketSummary.spentFraction]. */
    val spentFraction: Float
        get() = if (allocated <= 0.0) {
            if (spentWithDraft > 0.0) Float.POSITIVE_INFINITY else 0f
        } else {
            (spentWithDraft / allocated).toFloat()
        }
}

object BucketPreviewCalculator {

    /**
     * The bucket the transaction would land in, with [draftAmount] added to its spend. Null when
     * there is nothing to show: not an expense, no category yet, no running cycle, a date before
     * the cycle began, or a category no bucket claims (it would count as Unbucketed).
     *
     * A running cycle keeps absorbing spend past its end date, so only the start bounds [date].
     * When editing, [original] is already in the bucket's spend, so it is taken out first rather
     * than counted twice.
     */
    fun preview(
        context: BucketPreviewContext?,
        type: String,
        category: String,
        date: LocalDate,
        draftAmount: Double,
        original: ExpenseRecord? = null
    ): BucketPreview? {
        if (context == null || type != TransactionType.EXPENSE || category.isBlank()) return null

        val summary = context.summary
        if (summary.cycle.closedAt != null) return null
        val start = LocalDate.parse(summary.cycle.startDate)
        if (date.isBefore(start)) return null

        val bucketId = bucketIdOf(context.assignments, category) ?: return null
        val bucketSummary = summary.buckets.firstOrNull { it.bucket.id == bucketId } ?: return null

        val alreadyCounted = if (original != null && countsIn(original, bucketId, context.assignments, start)) {
            original.amount
        } else {
            0.0
        }

        return BucketPreview(
            bucket = bucketSummary.bucket,
            spentWithDraft = bucketSummary.spent - alreadyCounted + draftAmount.coerceAtLeast(0.0),
            paceFraction = summary.elapsedFraction
        )
    }

    private fun bucketIdOf(assignments: List<BucketCategory>, category: String): Long? {
        val key = CycleSummaryBuilder.categoryKey(category)
        return assignments.firstOrNull { CycleSummaryBuilder.categoryKey(it.category) == key }?.bucketId
    }

    private fun countsIn(
        record: ExpenseRecord,
        bucketId: Long,
        assignments: List<BucketCategory>,
        start: LocalDate
    ): Boolean {
        if (record.type != TransactionType.EXPENSE) return false
        val date = parseFlexibleDate(record.date) ?: record.remoteTimestamp?.let(::parseFlexibleDate) ?: return false
        return !date.isBefore(start) && bucketIdOf(assignments, record.category) == bucketId
    }
}
