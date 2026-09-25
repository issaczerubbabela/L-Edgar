package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import java.time.LocalDate

/**
 * Works out what a cycle looks like right now. Spend is never stored: it is derived here from
 * the expense ledger, so backdating a cycle or moving a category re-homes spend automatically.
 */
object CycleSummaryBuilder {

    fun build(
        cycle: BudgetCycle,
        buckets: List<BudgetBucket>,
        assignments: List<BucketCategory>,
        records: List<ExpenseRecord>,
        today: LocalDate
    ): CycleSummary {
        val start = LocalDate.parse(cycle.startDate)
        val end = LocalDate.parse(cycle.endDate)
        val isClosed = cycle.closedAt != null

        val spendByCategory = spendByCategory(records, start, if (isClosed) end else null)

        val bucketIdByCategory = assignments.associate { categoryKey(it.category) to it.bucketId }
        val knownBucketIds = buckets.map { it.id }.toSet()

        val bucketSummaries = buckets.map { bucket ->
            val inBucket = spendByCategory.filter { (key, _) -> bucketIdByCategory[key] == bucket.id }
            val categories = inBucket.values.sortedByDescending { it.amount }
            BucketSummary(bucket = bucket, categories = categories, spent = categories.sumOf { it.amount })
        }

        // A category whose bucket no longer exists is treated as unassigned rather than dropped.
        val unbucketed = spendByCategory
            .filter { (key, _) -> bucketIdByCategory[key] !in knownBucketIds }
            .values
            .sortedByDescending { it.amount }

        val totalSpent = spendByCategory.values.sumOf { it.amount }
        val allocated = buckets.sumOf { it.allocatedAmount }
        val leftToSpend = cycle.spendableAmount - totalSpent
        val daysLeft = CycleCalendar.daysLeft(end, today)
        val daysOverdue = CycleCalendar.daysOverdue(end, today, isClosed)

        return CycleSummary(
            cycle = cycle,
            totalDays = CycleCalendar.totalDays(start, end),
            dayNumber = CycleCalendar.dayNumber(start, end, today),
            daysLeft = daysLeft,
            daysOverdue = daysOverdue,
            daysUntilStart = CycleCalendar.daysUntilStart(start, today),
            elapsedFraction = CycleCalendar.elapsedFraction(start, end, today),
            totalSpent = totalSpent,
            leftToSpend = leftToSpend,
            allocated = allocated,
            unallocated = cycle.spendableAmount - allocated,
            dailyPace = if (daysOverdue > 0 || leftToSpend <= 0.0) 0.0 else leftToSpend / daysLeft.coerceAtLeast(1),
            buckets = bucketSummaries,
            unbucketed = unbucketed
        )
    }

    /**
     * Expense totals per category from [start] on. A running cycle passes a null [end]: it keeps
     * absorbing spend past its end date until the next cycle is started, so nothing is orphaned.
     * Categories are keyed ignoring case, matching how they are routed into buckets.
     */
    private fun spendByCategory(
        records: List<ExpenseRecord>,
        start: LocalDate,
        end: LocalDate?
    ): Map<String, CategorySpend> {
        val totals = LinkedHashMap<String, CategorySpend>()
        records.forEach { record ->
            if (record.type != "Expense") return@forEach
            val date = parseFlexibleDate(record.date)
                ?: record.remoteTimestamp?.let(::parseFlexibleDate)
                ?: return@forEach
            if (date.isBefore(start) || (end != null && date.isAfter(end))) return@forEach

            val key = categoryKey(record.category)
            val existing = totals[key]
            totals[key] = if (existing == null) {
                CategorySpend(record.category.trim().ifBlank { UNCATEGORISED }, record.amount)
            } else {
                existing.copy(amount = existing.amount + record.amount)
            }
        }
        return totals
    }

    /** How a category is compared for routing and spend: ignoring case and surrounding space. */
    fun categoryKey(category: String): String = category.trim().lowercase()


    const val UNCATEGORISED = "Uncategorised"
}
