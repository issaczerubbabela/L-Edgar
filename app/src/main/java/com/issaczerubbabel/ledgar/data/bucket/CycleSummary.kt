package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.Fund

data class CategorySpend(val category: String, val amount: Double)

data class BucketSummary(
    val bucket: BudgetBucket,
    /** Categories routed into this bucket that have spend this cycle, largest first. */
    val categories: List<CategorySpend>,
    val spent: Double
) {
    val remaining: Double get() = bucket.allocatedAmount - spent
    val isOver: Boolean get() = spent > bucket.allocatedAmount

    /** Share of the allocation spent, uncapped so an overspend can be drawn as overflowing. */
    val spentFraction: Float
        get() = if (bucket.allocatedAmount <= 0.0) {
            if (spent > 0.0) Float.POSITIVE_INFINITY else 0f
        } else {
            (spent / bucket.allocatedAmount).toFloat()
        }
}

/** One Fund's Planned contribution in a cycle against what the cycle has Set aside into it. */
data class FundContribution(
    val fund: Fund,
    val planned: Double,
    val setAside: Double
) {
    val stillToTransfer: Double get() = (planned - setAside).coerceAtLeast(0.0)
    val isMet: Boolean get() = setAside >= planned
}

data class CycleSummary(
    val cycle: BudgetCycle,
    val totalDays: Int,
    /** 1-based day of the cycle today falls on; 0 before it starts. */
    val dayNumber: Int,
    val daysLeft: Int,
    val daysOverdue: Int,
    /** Days until a future-dated cycle begins; zero once it has started. */
    val daysUntilStart: Int,
    /** 0..1, where the pace tick sits. */
    val elapsedFraction: Float,
    /** Every Expense in the cycle except those drawn from a Fund. */
    val totalSpent: Double,
    /**
     * Left to spend, or This cycle: spendable minus [totalSpent] (bucketed or not) minus [setAside].
     * Negative when overspent.
     */
    val leftToSpend: Double,
    val allocated: Double,
    /** Spendable minus what buckets have claimed and [plannedContributions]. Negative means over-allocated. */
    val unallocated: Double,
    val buckets: List<BucketSummary>,
    /** Spend in categories that no bucket claims. The virtual "Unbucketed" bucket. */
    val unbucketed: List<CategorySpend>,
    /** Transfers in the cycle into Funds' whole Accounts (Set aside). Zero without Funds. */
    val setAside: Double = 0.0,
    /** Expenses in the cycle paid from Funds' whole Accounts: they come out of the Fund, not [totalSpent]. */
    val drawnFromFunds: Double = 0.0,
    /** The cycle's Planned contributions to active Funds, together. */
    val plannedContributions: Double = 0.0,
    /** Each active Fund with a Planned contribution or Set aside this cycle, in Fund order. */
    val fundContributions: List<FundContribution> = emptyList()
) {
    val isOverdue: Boolean get() = daysOverdue > 0
    val unbucketedSpent: Double get() = unbucketed.sumOf { it.amount }
    val overBucketCount: Int get() = buckets.count { it.isOver }
}
