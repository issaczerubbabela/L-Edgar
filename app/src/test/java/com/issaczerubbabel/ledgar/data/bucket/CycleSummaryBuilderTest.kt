package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CycleSummaryBuilderTest {

    private val today = LocalDate.of(2026, 9, 13)

    private fun cycle(
        start: String = "2026-08-26",
        end: String = "2026-09-25",
        spendable: Double = 70000.0,
        closedAt: String? = null
    ) = BudgetCycle(id = 1, startDate = start, endDate = end, spendableAmount = spendable, closedAt = closedAt)

    private fun bucket(id: Long, name: String, allocated: Double) =
        BudgetBucket(id = id, cycleId = 1, name = name, colorIndex = 0, allocatedAmount = allocated, sortOrder = id.toInt())

    private fun route(bucketId: Long, category: String) =
        BucketCategory(cycleId = 1, bucketId = bucketId, category = category)

    private fun expense(date: String, category: String, amount: Double, type: String = "Expense") =
        ExpenseRecord(date = date, type = type, category = category, description = "", amount = amount, remarks = "")

    private fun build(
        cycle: BudgetCycle = cycle(),
        buckets: List<BudgetBucket>,
        assignments: List<BucketCategory>,
        records: List<ExpenseRecord>,
        today: LocalDate = this.today
    ) = CycleSummaryBuilder.build(cycle, buckets, assignments, records, today)

    @Test
    fun routesSpendIntoBucketsAndCollectsTheRestAsUnbucketed() {
        val summary = build(
            buckets = listOf(bucket(1, "Essentials", 18000.0), bucket(2, "Fun", 6000.0)),
            assignments = listOf(route(1, "Rent"), route(1, "Utilities"), route(2, "Food")),
            records = listOf(
                expense("2026-09-01", "Rent", 14000.0),
                expense("2026-09-02", "Utilities", 200.0),
                expense("2026-09-03", "Food", 6940.0),
                expense("2026-09-04", "Gifts", 1240.0)
            )
        )

        assertEquals(14200.0, summary.buckets[0].spent, 0.0)
        assertEquals(6940.0, summary.buckets[1].spent, 0.0)
        assertEquals(listOf("Gifts"), summary.unbucketed.map { it.category })
        assertEquals(1240.0, summary.unbucketedSpent, 0.0)
        // Every rupee lands in exactly one place, so the parts reconcile to the whole.
        assertEquals(
            summary.totalSpent,
            summary.buckets.sumOf { it.spent } + summary.unbucketedSpent,
            0.0001
        )
    }

    @Test
    fun leftToSpendCountsUnbucketedSpendAndUnallocatedIgnoresIt() {
        val summary = build(
            buckets = listOf(bucket(1, "Essentials", 18000.0)),
            assignments = listOf(route(1, "Rent")),
            records = listOf(expense("2026-09-01", "Rent", 14000.0), expense("2026-09-02", "Gifts", 1240.0))
        )

        assertEquals(70000.0 - 15240.0, summary.leftToSpend, 0.0)
        assertEquals(70000.0 - 18000.0, summary.unallocated, 0.0)
    }

    @Test
    fun categoryRoutingIgnoresCase() {
        val summary = build(
            buckets = listOf(bucket(1, "Food", 5000.0)),
            assignments = listOf(route(1, "Food & Snacks")),
            records = listOf(
                expense("2026-09-01", "food & snacks", 100.0),
                expense("2026-09-02", "FOOD & SNACKS", 50.0)
            )
        )

        assertEquals(150.0, summary.buckets.single().spent, 0.0)
        assertTrue(summary.unbucketed.isEmpty())
    }

    @Test
    fun onlyExpensesInsideTheCycleCount() {
        val summary = build(
            buckets = listOf(bucket(1, "Food", 5000.0)),
            assignments = listOf(route(1, "Food")),
            records = listOf(
                expense("2026-08-25", "Food", 999.0),                    // day before it starts
                expense("2026-09-01", "Food", 100.0),                    // counts
                expense("2026-09-02", "Food", 999.0, type = "Income"),   // not an expense
                expense("2026-09-03", "Food", 999.0, type = "Transfer"), // not an expense
                expense("not a date", "Food", 999.0)                     // unreadable date
            )
        )

        assertEquals(100.0, summary.totalSpent, 0.0)
    }

    @Test
    fun aRunningCycleKeepsAbsorbingSpendAfterItsEndDate() {
        val summary = build(
            buckets = listOf(bucket(1, "Food", 5000.0)),
            assignments = listOf(route(1, "Food")),
            records = listOf(expense("2026-09-25", "Food", 100.0), expense("2026-09-28", "Food", 200.0)),
            today = LocalDate.of(2026, 9, 29)
        )

        assertEquals(300.0, summary.totalSpent, 0.0)
        assertEquals(4, summary.daysOverdue)
        assertTrue(summary.isOverdue)
    }

    @Test
    fun aClosedCycleStopsAtItsEndDate() {
        val summary = build(
            cycle = cycle(closedAt = "2026-09-26"),
            buckets = listOf(bucket(1, "Food", 5000.0)),
            assignments = listOf(route(1, "Food")),
            records = listOf(expense("2026-09-25", "Food", 100.0), expense("2026-09-28", "Food", 200.0)),
            today = LocalDate.of(2026, 9, 29)
        )

        assertEquals(100.0, summary.totalSpent, 0.0)
        assertFalse(summary.isOverdue)
    }

    @Test
    fun anOverspentBucketIsFlaggedAndReportsItsOverflow() {
        val summary = build(
            buckets = listOf(bucket(1, "Eating Out", 6000.0), bucket(2, "Transport", 4500.0)),
            assignments = listOf(route(1, "Restaurants"), route(2, "Metro")),
            records = listOf(expense("2026-09-01", "Restaurants", 6940.0), expense("2026-09-02", "Metro", 2100.0))
        )

        val eatingOut = summary.buckets[0]
        assertTrue(eatingOut.isOver)
        assertEquals(-940.0, eatingOut.remaining, 0.0)
        assertEquals(6940f / 6000f, eatingOut.spentFraction, 0.0001f)
        assertFalse(summary.buckets[1].isOver)
        assertEquals(1, summary.overBucketCount)
    }

    @Test
    fun spendingInABucketWithNothingAllocatedCountsAsOver() {
        val summary = build(
            buckets = listOf(bucket(1, "Misc", 0.0)),
            assignments = listOf(route(1, "Gifts")),
            records = listOf(expense("2026-09-01", "Gifts", 10.0))
        )

        assertTrue(summary.buckets.single().isOver)
        assertEquals(Float.POSITIVE_INFINITY, summary.buckets.single().spentFraction, 0f)
    }

    @Test
    fun dailyPaceSpreadsWhatIsLeftOverTheDaysThatRemain() {
        val summary = build(
            buckets = emptyList(),
            assignments = emptyList(),
            records = listOf(expense("2026-09-01", "Food", 45620.0))
        )

        // 70000 - 45620 = 24380 left, 12 days remain after the 13th.
        assertEquals(24380.0, summary.leftToSpend, 0.0)
        assertEquals(12, summary.daysLeft)
        assertEquals(24380.0 / 12, summary.dailyPace, 0.0001)
    }

    @Test
    fun dailyPaceIsZeroOnceOverdueOrOverspent() {
        val overdue = build(
            buckets = emptyList(), assignments = emptyList(), records = emptyList(),
            today = LocalDate.of(2026, 9, 30)
        )
        val overspent = build(
            buckets = emptyList(), assignments = emptyList(),
            records = listOf(expense("2026-09-01", "Food", 80000.0))
        )

        assertEquals(0.0, overdue.dailyPace, 0.0)
        assertEquals(0.0, overspent.dailyPace, 0.0)
    }

    @Test
    fun onTheLastDayTheWholeRemainderIsTodaysPace() {
        val summary = build(
            buckets = emptyList(), assignments = emptyList(),
            records = listOf(expense("2026-09-01", "Food", 60000.0)),
            today = LocalDate.of(2026, 9, 25)
        )

        assertEquals(0, summary.daysLeft)
        assertEquals(10000.0, summary.dailyPace, 0.0)
    }

    @Test
    fun overAllocatingShowsAsANegativeUnallocated() {
        val summary = build(
            buckets = listOf(bucket(1, "A", 50000.0), bucket(2, "B", 24500.0)),
            assignments = emptyList(),
            records = emptyList()
        )

        assertEquals(-4500.0, summary.unallocated, 0.0)
    }

    @Test
    fun aCategoryPointingAtAMissingBucketFallsBackToUnbucketed() {
        val summary = build(
            buckets = listOf(bucket(1, "Food", 5000.0)),
            assignments = listOf(route(99, "Gifts")),
            records = listOf(expense("2026-09-01", "Gifts", 40.0))
        )

        assertEquals(listOf("Gifts"), summary.unbucketed.map { it.category })
    }

    @Test
    fun aBlankCategoryIsShownAsUncategorisedRatherThanEmpty() {
        val summary = build(
            buckets = emptyList(), assignments = emptyList(),
            records = listOf(expense("2026-09-01", "  ", 40.0))
        )

        assertEquals(listOf(CycleSummaryBuilder.UNCATEGORISED), summary.unbucketed.map { it.category })
    }

    @Test
    fun categoriesInsideABucketAreListedLargestFirst() {
        val summary = build(
            buckets = listOf(bucket(1, "Eating Out", 9000.0)),
            assignments = listOf(route(1, "Coffee"), route(1, "Restaurants"), route(1, "Delivery")),
            records = listOf(
                expense("2026-09-01", "Coffee", 1640.0),
                expense("2026-09-02", "Restaurants", 4100.0),
                expense("2026-09-03", "Delivery", 1200.0)
            )
        )

        assertEquals(listOf("Restaurants", "Coffee", "Delivery"), summary.buckets.single().categories.map { it.category })
    }
}
