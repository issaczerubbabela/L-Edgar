package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.BucketAccount
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle

data class CategorySpend(val category: String, val amount: Double)

/** The Accounts a cycle's Buckets track, and every Account (for names and for the bucket editor). */
data class TrackedAccounts(
    val routes: List<BucketAccount> = emptyList(),
    val accounts: List<AccountRecord> = emptyList()
) {
    val names: Map<Long, String> get() = accounts.associate { it.id to it.accountName }
}

/** Money moved into one tracked Account this cycle, net of what was moved back out. */
data class AccountTransfers(val accountId: Long, val name: String, val amount: Double)

data class BucketSummary(
    val bucket: BudgetBucket,
    /** Categories routed into this bucket that have spend this cycle, largest first. */
    val categories: List<CategorySpend>,
    val spent: Double,
    /** Accounts this Bucket tracks, with their net Transfers this cycle; included in [spent]. */
    val accounts: List<AccountTransfers> = emptyList()
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
    val totalSpent: Double,
    /** Spendable minus everything spent, bucketed or not. Negative when overspent. */
    val leftToSpend: Double,
    val allocated: Double,
    /** Spendable minus what buckets have claimed. Negative means over-allocated. */
    val unallocated: Double,
    /** What can be spent per remaining day to make the money last. Zero once overdue or overspent. */
    val dailyPace: Double,
    val buckets: List<BucketSummary>,
    /** Spend in categories that no bucket claims. The virtual "Unbucketed" bucket. */
    val unbucketed: List<CategorySpend>
) {
    val isOverdue: Boolean get() = daysOverdue > 0
    val unbucketedSpent: Double get() = unbucketed.sumOf { it.amount }
    val overBucketCount: Int get() = buckets.count { it.isOver }
}
