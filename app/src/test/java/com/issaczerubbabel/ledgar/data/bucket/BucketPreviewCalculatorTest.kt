package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BucketPreviewCalculatorTest {

    private val today = LocalDate.of(2026, 9, 13)
    private val cycle = BudgetCycle(id = 1, startDate = "2026-08-26", endDate = "2026-09-25", spendableAmount = 70000.0)
    private val food = BudgetBucket(id = 1, cycleId = 1, name = "Food", colorIndex = 0, allocatedAmount = 6000.0, sortOrder = 0)
    private val assignments = listOf(BucketCategory(cycleId = 1, bucketId = 1, category = "Food & Snacks"))

    private fun expense(category: String, amount: Double, date: String = "2026-09-02", type: String = "Expense") =
        ExpenseRecord(date = date, type = type, category = category, description = "", amount = amount, remarks = "")

    private fun context(
        records: List<ExpenseRecord> = listOf(expense("Food & Snacks", 3660.0)),
        cycle: BudgetCycle = this.cycle
    ) = BucketPreviewContext(
        summary = CycleSummaryBuilder.build(cycle, listOf(food), assignments, records, today),
        assignments = assignments
    )

    private fun preview(
        context: BucketPreviewContext? = context(),
        type: String = "Expense",
        category: String = "Food & Snacks",
        date: LocalDate = today,
        amount: Double = 0.0,
        original: ExpenseRecord? = null
    ) = BucketPreviewCalculator.preview(context, type, category, date, amount, original)

    @Test
    fun showsRemainingBeforeAnythingIsTyped() {
        val p = preview()!!
        assertEquals(2340.0, p.remaining, 0.001)
        assertEquals(6000.0, p.allocated, 0.001)
        assertFalse(p.isOver)
    }

    @Test
    fun countsTheAmountBeingTyped() {
        val p = preview(amount = 500.0)!!
        assertEquals(1840.0, p.remaining, 0.001)
        assertEquals(4160.0 / 6000.0, p.spentFraction.toDouble(), 0.001)
    }

    @Test
    fun flagsAnOverspend() {
        val p = preview(amount = 3000.0)!!
        assertTrue(p.isOver)
        assertEquals(-660.0, p.remaining, 0.001)
    }

    @Test
    fun matchesCategoryIgnoringCaseAndSpacing() {
        assertNotNull(preview(category = "  food & snacks "))
    }

    @Test
    fun hidesForIncomeAndTransfer() {
        assertNull(preview(type = "Income"))
        assertNull(preview(type = "Transfer"))
    }

    @Test
    fun hidesWithoutACategoryOrForAnUnbucketedOne() {
        assertNull(preview(category = ""))
        assertNull(preview(category = "Gifts"))
    }

    @Test
    fun hidesWithNoRunningCycleOrForAClosedOne() {
        assertNull(preview(context = null))
        assertNull(preview(context = context(cycle = cycle.copy(closedAt = "2026-09-25"))))
    }

    @Test
    fun hidesForADateBeforeTheCycleStarted() {
        assertNull(preview(date = LocalDate.of(2026, 8, 25)))
    }

    @Test
    fun aRunningCycleStillCountsDatesPastItsEnd() {
        assertNotNull(preview(date = LocalDate.of(2026, 9, 30)))
    }

    @Test
    fun editingDoesNotCountTheOriginalTwice() {
        val original = expense("Food & Snacks", 500.0)
        val ctx = context(records = listOf(expense("Food & Snacks", 3160.0), original))
        val p = preview(context = ctx, amount = 700.0, original = original)!!
        // 3660 spent already includes the 500 being edited: 3160 + 700 replaces it.
        assertEquals(6000.0 - 3860.0, p.remaining, 0.001)
    }

    @Test
    fun editingAnExpenseThatWasInAnotherBucketKeepsThisBucketsSpend() {
        val original = expense("Gifts", 500.0)
        val ctx = context(records = listOf(expense("Food & Snacks", 3660.0), original))
        val p = preview(context = ctx, amount = 100.0, original = original)!!
        assertEquals(6000.0 - 3760.0, p.remaining, 0.001)
    }
}
