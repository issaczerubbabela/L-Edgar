package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.account.TransactionSnapshot
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.fund.FundMath
import com.issaczerubbabel.ledgar.fund.FundsSnapshot
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import java.time.LocalDate

/**
 * Works out what a cycle looks like right now. Spend is never stored: it is derived here from
 * the expense ledger, so backdating a cycle or moving a category re-homes spend automatically.
 *
 * With [FundsSnapshot.EMPTY] (the default) every figure is what it was before Funds existed. With
 * Funds, an Expense drawn from a Fund's whole Account leaves the cycle, Set aside comes off Left to
 * spend, and Planned contributions come off Unallocated (ADR-0011).
 */
object CycleSummaryBuilder {

    fun build(
        cycle: BudgetCycle,
        buckets: List<BudgetBucket>,
        assignments: List<BucketCategory>,
        records: List<ExpenseRecord>,
        today: LocalDate,
        funds: FundsSnapshot = FundsSnapshot.EMPTY
    ): CycleSummary {
        val start = LocalDate.parse(cycle.startDate)
        val end = LocalDate.parse(cycle.endDate)
        val isClosed = cycle.closedAt != null

        val holdings = funds.activeHoldings
        val snapshots = if (funds.isEmpty) emptyList() else records.map { it to snapshotOf(it) }
        val transactions = snapshots.mapNotNull { it.second }
        val counted = if (funds.isEmpty) records else snapshots.filter { (_, txn) ->
            txn == null || FundMath.drawnFrom(txn, holdings) == null
        }.map { it.first }
        val spendByCategory = spendByCategory(
            counted,
            start,
            CycleCalendar.countsUntil(end, today, isClosed)
        )
        val setAside = FundMath.setAside(cycle, transactions, holdings, today)
        val drawn = FundMath.drawn(cycle, transactions, holdings, today)
        val plans = funds.plansFor(cycle.id).associateBy { it.fundId }
        val fundContributions = funds.activeFunds
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .filter { it.id in plans || it.id in setAside.byFund }
            .map { fund ->
                FundContribution(fund, planned = plans[fund.id]?.plannedAmount ?: 0.0, setAside = setAside.byFund[fund.id] ?: 0.0)
            }
        val plannedContributions = plans.values.sumOf { it.plannedAmount }

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
        val leftToSpend = cycle.spendableAmount - totalSpent - setAside.total
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
            unallocated = cycle.spendableAmount - allocated - plannedContributions,
            dailyPace = if (daysOverdue > 0 || leftToSpend <= 0.0) 0.0 else leftToSpend / daysLeft.coerceAtLeast(1),
            buckets = bucketSummaries,
            unbucketed = unbucketed,
            setAside = setAside.total,
            drawnFromFunds = drawn.total,
            plannedContributions = plannedContributions,
            fundContributions = fundContributions
        )
    }

    /**
     * What [FundMath] needs of a record, dated the way [spendByCategory] dates it. Null for a record
     * without a readable date, which no cycle counts.
     */
    private fun snapshotOf(record: ExpenseRecord): TransactionSnapshot? {
        val date = parseFlexibleDate(record.date)
            ?: record.remoteTimestamp?.let(::parseFlexibleDate)
            ?: return null
        return TransactionSnapshot(
            id = record.id,
            type = record.type,
            date = date,
            amount = record.amount,
            accountId = record.accountId,
            fromAccountId = record.fromAccountId,
            toAccountId = record.toAccountId
        )
    }

    /**
     * Expense totals per category from [start] to [end], both included. For a running cycle [end] is
     * the later of its end date and today (see [CycleCalendar.countsUntil]).
     * Categories are keyed ignoring case, matching how they are routed into buckets.
     */
    private fun spendByCategory(
        records: List<ExpenseRecord>,
        start: LocalDate,
        end: LocalDate
    ): Map<String, CategorySpend> {
        val totals = LinkedHashMap<String, CategorySpend>()
        records.forEach { record ->
            if (record.type != "Expense") return@forEach
            val date = parseFlexibleDate(record.date)
                ?: record.remoteTimestamp?.let(::parseFlexibleDate)
                ?: return@forEach
            if (date.isBefore(start) || date.isAfter(end)) return@forEach

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
