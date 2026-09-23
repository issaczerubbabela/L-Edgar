package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CycleTransitionsTest {

    private val today = LocalDate.of(2026, 9, 26)

    private val running = BudgetCycle(
        id = 1, startDate = "2026-08-26", endDate = "2026-09-25", spendableAmount = 68000.0
    )

    private fun request(
        start: LocalDate = LocalDate.of(2026, 9, 26),
        end: LocalDate = LocalDate.of(2026, 10, 25),
        amount: Double = 70000.0
    ) = StartCycleRequest(start, end, amount, carryOverBuckets = true)

    // ── validate ───────────────────────────────────────────────────────────────

    @Test
    fun aNormalHandoverIsValid() {
        assertNull(CycleTransitions.validate(request(), running))
    }

    @Test
    fun theVeryFirstCycleIsValidWithNothingRunning() {
        assertNull(CycleTransitions.validate(request(), null))
    }

    @Test
    fun rejectsAmountsThatAreNegativeOrNotNumbers() {
        assertEquals(StartCycleError.INVALID_AMOUNT, CycleTransitions.validate(request(amount = -1.0), running))
        assertEquals(StartCycleError.INVALID_AMOUNT, CycleTransitions.validate(request(amount = Double.NaN), running))
        assertEquals(
            StartCycleError.INVALID_AMOUNT,
            CycleTransitions.validate(request(amount = Double.POSITIVE_INFINITY), running)
        )
    }

    @Test
    fun aZeroSpendableAmountIsAllowed() {
        assertNull(CycleTransitions.validate(request(amount = 0.0), running))
    }

    @Test
    fun rejectsAnEndDateBeforeTheStart() {
        val bad = request(start = LocalDate.of(2026, 9, 26), end = LocalDate.of(2026, 9, 25))
        assertEquals(StartCycleError.END_BEFORE_START, CycleTransitions.validate(bad, running))
    }

    @Test
    fun aOneDayCycleIsValid() {
        val oneDay = request(start = LocalDate.of(2026, 9, 26), end = LocalDate.of(2026, 9, 26))
        assertNull(CycleTransitions.validate(oneDay, running))
    }

    @Test
    fun rejectsStartingOnOrBeforeTheDayTheRunningCycleBegan() {
        val same = request(start = LocalDate.of(2026, 8, 26), end = LocalDate.of(2026, 9, 25))
        val earlier = request(start = LocalDate.of(2026, 8, 1), end = LocalDate.of(2026, 9, 25))

        assertEquals(StartCycleError.START_NOT_AFTER_CURRENT, CycleTransitions.validate(same, running))
        assertEquals(StartCycleError.START_NOT_AFTER_CURRENT, CycleTransitions.validate(earlier, running))
    }

    // ── closeForNext ───────────────────────────────────────────────────────────

    @Test
    fun anOnTimeHandoverEndsTheOldCycleTheDayBeforeTheNewOne() {
        val closed = CycleTransitions.closeForNext(running, request(start = LocalDate.of(2026, 9, 26)), today)

        assertEquals("2026-09-25", closed.endDate)
        assertEquals("2026-09-26", closed.closedAt)
    }

    @Test
    fun anOverdueCycleStretchesToCoverTheGapItWasAbsorbing() {
        // The old cycle ran on past its 25 Sep end date, absorbing spend. The next one only starts
        // on the 29th, so the old one must cover the 26th-28th too rather than leave a gap.
        val closed = CycleTransitions.closeForNext(
            running, request(start = LocalDate.of(2026, 9, 29)), today = LocalDate.of(2026, 9, 29)
        )

        assertEquals("2026-09-28", closed.endDate)
    }

    @Test
    fun aBackdatedStartTrimsTheOldCycleSoSpendFromPaydayMovesOn() {
        val closed = CycleTransitions.closeForNext(
            running, request(start = LocalDate.of(2026, 9, 20)), today = LocalDate.of(2026, 9, 26)
        )

        assertEquals("2026-09-19", closed.endDate)
        assertEquals("2026-09-26", closed.closedAt)
    }

    @Test
    fun closingKeepsEverythingElseAboutTheOldCycle() {
        val closed = CycleTransitions.closeForNext(running, request(), today)

        assertEquals(running.id, closed.id)
        assertEquals(running.startDate, closed.startDate)
        assertEquals(running.spendableAmount, closed.spendableAmount, 0.0)
    }

    // ── SalaryDetector ─────────────────────────────────────────────────────────

    private fun record(date: String, category: String, amount: Double, type: String = "Income") =
        ExpenseRecord(date = date, type = type, category = category, description = "", amount = amount, remarks = "")

    private val payday = LocalDate.of(2026, 9, 26)

    @Test
    fun detectsASalaryLandingNearPayday() {
        val detected = SalaryDetector.detect(listOf(record("2026-09-25", "Salary", 71500.0)), payday)

        assertEquals(71500.0, detected!!, 0.0)
    }

    @Test
    fun sumsSeveralSalaryPayments() {
        val detected = SalaryDetector.detect(
            listOf(record("2026-09-26", "Salary", 60000.0), record("2026-09-27", "Salary", 11500.0)), payday
        )

        assertEquals(71500.0, detected!!, 0.0)
    }

    @Test
    fun ignoresAnythingThatIsNotAnIncomeCategorisedSalaryInsideTheWindow() {
        val detected = SalaryDetector.detect(
            listOf(
                record("2026-09-26", "Gift", 5000.0),                      // wrong category
                record("2026-09-26", "Salary", 5000.0, type = "Expense"),  // wrong type
                record("2026-08-26", "Salary", 5000.0),                    // a month early
                record("2026-10-26", "Salary", 5000.0)                     // a month late
            ),
            payday
        )

        assertNull(detected)
    }

    @Test
    fun categoryMatchingIgnoresCaseAndSurroundingSpace() {
        val detected = SalaryDetector.detect(listOf(record("2026-09-26", "  salary ", 100.0)), payday)

        assertEquals(100.0, detected!!, 0.0)
    }

    @Test
    fun theWindowIsInclusiveOnBothSides() {
        assertEquals(1.0, SalaryDetector.detect(listOf(record("2026-09-21", "Salary", 1.0)), payday, 5)!!, 0.0)
        assertEquals(1.0, SalaryDetector.detect(listOf(record("2026-10-01", "Salary", 1.0)), payday, 5)!!, 0.0)
        assertNull(SalaryDetector.detect(listOf(record("2026-10-02", "Salary", 1.0)), payday, 5))
    }
}
