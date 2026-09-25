package com.issaczerubbabel.ledgar.data.local.migration

import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.migration.BucketBudgetMigration.LegacyBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BucketBudgetMigrationTest {

    private fun row(month: String, category: String, amount: Double) = LegacyBudget(month, category, amount)

    @Test
    fun noBudgetsMeansNoCycle() {
        assertNull(BucketBudgetMigration.plan(emptyList()))
    }

    @Test
    fun onlyTheMostRecentMonthIsCarriedOver() {
        val plan = BucketBudgetMigration.plan(
            listOf(
                row("2026-03", "Food", 1000.0),
                row("2026-09", "Rent", 15000.0),
                row("2026-08", "Travel", 500.0)
            )
        )

        assertNotNull(plan)
        assertEquals("2026-09-01", plan!!.startDate)
        assertEquals("2026-09-30", plan.endDate)
        assertEquals(listOf("Rent"), plan.buckets.map { it.name })
    }

    @Test
    fun februaryEndsOnItsLastDayIncludingLeapYears() {
        assertEquals("2026-02-28", BucketBudgetMigration.plan(listOf(row("2026-02", "Food", 1.0)))!!.endDate)
        assertEquals("2028-02-29", BucketBudgetMigration.plan(listOf(row("2028-02", "Food", 1.0)))!!.endDate)
    }

    @Test
    fun malformedMonthsAreIgnoredRatherThanCrashing() {
        val plan = BucketBudgetMigration.plan(
            listOf(
                row("garbage", "Food", 999.0),
                row("", "Food", 999.0),
                row("2026-13", "Food", 999.0),
                row("2026-07", "Rent", 100.0)
            )
        )

        assertEquals("2026-07-01", plan!!.startDate)
        assertEquals(listOf("Rent"), plan.buckets.map { it.name })
    }

    @Test
    fun onlyMalformedMonthsMeansNoCycle() {
        assertNull(BucketBudgetMigration.plan(listOf(row("garbage", "Food", 1.0))))
    }

    @Test
    fun totalRowBecomesSpendableAndIsNotABucket() {
        val plan = BucketBudgetMigration.plan(
            listOf(
                row("2026-09", "__TOTAL__", 70000.0),
                row("2026-09", "Food", 6000.0),
                row("2026-09", "Rent", 18000.0)
            )
        )!!

        assertEquals(70000.0, plan.spendableAmount, 0.0)
        assertEquals(listOf("Food", "Rent"), plan.buckets.map { it.name })
    }

    @Test
    fun withoutATotalRowSpendableIsTheSumOfTheBuckets() {
        val plan = BucketBudgetMigration.plan(
            listOf(row("2026-09", "Food", 6000.0), row("2026-09", "Rent", 18000.0))
        )!!

        assertEquals(24000.0, plan.spendableAmount, 0.0)
    }

    @Test
    fun aTotalOnlyMonthStillGivesACycleWithNoBuckets() {
        val plan = BucketBudgetMigration.plan(listOf(row("2026-09", "__TOTAL__", 50000.0)))!!

        assertEquals(50000.0, plan.spendableAmount, 0.0)
        assertTrue(plan.buckets.isEmpty())
    }

    @Test
    fun categoriesDifferingOnlyByCaseAreMergedAndSummed() {
        // The old unique index was case-sensitive so both rows can exist; the new one is not.
        val plan = BucketBudgetMigration.plan(
            listOf(row("2026-09", "Food", 5000.0), row("2026-09", "food", 2000.0))
        )!!

        assertEquals(1, plan.buckets.size)
        assertEquals(7000.0, plan.buckets.single().allocatedAmount, 0.0)
    }

    @Test
    fun blankCategoriesAreDropped() {
        val plan = BucketBudgetMigration.plan(
            listOf(row("2026-09", "  ", 100.0), row("2026-09", "", 100.0), row("2026-09", "Food", 100.0))
        )!!

        assertEquals(listOf("Food"), plan.buckets.map { it.name })
    }

    @Test
    fun nonsenseAmountsAreClampedToZero() {
        val plan = BucketBudgetMigration.plan(
            listOf(row("2026-09", "Food", -50.0), row("2026-09", "Rent", Double.NaN))
        )!!

        assertTrue(plan.buckets.all { it.allocatedAmount == 0.0 })
    }

    @Test
    fun bucketsFollowAlphabeticalOrderWithSequentialSortOrder() {
        val plan = BucketBudgetMigration.plan(
            listOf(row("2026-09", "Rent", 1.0), row("2026-09", "Food", 1.0), row("2026-09", "Travel", 1.0))
        )!!

        assertEquals(listOf("Food", "Rent", "Travel"), plan.buckets.map { it.name })
        assertEquals(listOf(0, 1, 2), plan.buckets.map { it.sortOrder })
    }

    @Test
    fun colourIndexStaysInsideThePalette() {
        listOf("Food", "Rent", "", "a", "Education & Courses, Events", "polygenelubricants").forEach {
            val index = BudgetBucket.colorIndexFor(it)
            assertTrue("$it -> $index", index in 0 until BudgetBucket.COLOR_COUNT)
        }
    }

    @Test
    fun colourIndexSurvivesTheHashCodeThatBreaksAbs() {
        // "polygenelubricants".hashCode() == Int.MIN_VALUE, where abs() alone stays negative.
        assertEquals(Int.MIN_VALUE, "polygenelubricants".hashCode())
        assertTrue(BudgetBucket.colorIndexFor("polygenelubricants") in 0 until BudgetBucket.COLOR_COUNT)
    }

    @Test
    fun colourIndexMatchesTheCalendarDotFormulaForOrdinaryNames() {
        listOf("Food", "Rent", "Transport", "Shopping").forEach {
            assertEquals(Math.abs(it.hashCode()) % BudgetBucket.COLOR_COUNT, BudgetBucket.colorIndexFor(it))
        }
    }
}
