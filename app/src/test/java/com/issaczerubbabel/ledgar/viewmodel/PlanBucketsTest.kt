package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.Fund
import com.issaczerubbabel.ledgar.data.local.entity.FundPlan
import com.issaczerubbabel.ledgar.fund.FundsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Plan this cycle: Buckets and Planned contributions share the spendable amount (#41). */
class PlanBucketsTest {

    private val today = LocalDate.of(2026, 10, 10)
    private val cycle = BudgetCycle(id = 1, startDate = "2026-09-26", endDate = "2026-10-25", spendableAmount = 115_842.0)
    private val buckets = listOf(
        BudgetBucket(id = 1, cycleId = 1, name = "Essentials", colorIndex = 0, allocatedAmount = 60_000.0, sortOrder = 0)
    )
    private val assignments = listOf(
        BucketCategory(cycleId = 1, bucketId = 1, category = "Rent"),
        BucketCategory(cycleId = 1, bucketId = 1, category = "Food")
    )

    private val emergency = Fund(id = 10, name = "Emergency", createdDate = "2026-09-01", sortOrder = 1)
    private val ministry = Fund(id = 11, name = "Ministry", createdDate = "2026-09-01", sortOrder = 0)
    private val old = Fund(id = 12, name = "Old car", createdDate = "2026-01-01", archivedAt = "2026-06-01")
    private val funds = FundsSnapshot(
        funds = listOf(emergency, ministry, old),
        plans = listOf(
            FundPlan(fundId = 10, cycleId = 1, plannedAmount = 15_000.0),
            FundPlan(fundId = 12, cycleId = 1, plannedAmount = 1_000.0),
            FundPlan(fundId = 11, cycleId = 2, plannedAmount = 9_000.0)
        )
    )

    private fun state(funds: FundsSnapshot = this.funds) = planBuckets(cycle, buckets, assignments, emptyList(), funds, today)

    @Test
    fun everyActiveFundIsInTheSetAsideGroupInFundOrder() {
        val rows = state().contributions

        assertEquals(listOf("Ministry", "Emergency"), rows.map { it.fund.name })
        assertEquals(listOf(0.0, 15_000.0), rows.map { it.planned })
    }

    @Test
    fun unallocatedSubtractsTheContributions() {
        val summary = state().summary!!

        assertEquals(115_842.0 - 60_000.0 - 15_000.0, summary.unallocated, 0.001)
    }

    @Test
    fun withoutFundsTheGroupIsEmptyAndUnallocatedIsAsBefore() {
        val state = state(FundsSnapshot.EMPTY)

        assertEquals(emptyList<PlannedContributionRow>(), state.contributions)
        assertEquals(115_842.0 - 60_000.0, state.summary!!.unallocated, 0.001)
        assertEquals(mapOf(1L to 2), state.categoryCountByBucket)
    }

    @Test
    fun aStepNeverTakesAContributionBelowZero() {
        assertEquals(15_500.0, steppedContribution(15_000.0, 500.0), 0.0)
        assertEquals(0.0, steppedContribution(300.0, -500.0), 0.0)
    }
}
