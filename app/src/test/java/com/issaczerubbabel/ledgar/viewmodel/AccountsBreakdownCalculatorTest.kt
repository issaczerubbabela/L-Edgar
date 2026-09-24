package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AccountsBreakdownCalculatorTest {

    private val september = StatsDateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))

    private val groups = mapOf(1L to "Cash", 2L to "Card", 3L to "Debit Card", 4L to "Savings")

    private fun record(date: String, amount: Double, accountId: Long? = 1L, type: String = "Expense") =
        ExpenseRecord(
            date = date, type = type, category = "Food", description = "", amount = amount,
            accountId = accountId, remarks = ""
        )

    private fun build(records: List<ExpenseRecord>, range: StatsDateRange = september) =
        AccountsBreakdownCalculator.build(records, range, groups)

    @Test
    fun anExpenseFromACardAccountCountsAsCard() {
        // The regression: this used to read a payment-mode field the app never saves, so the card
        // total was always zero and everything landed in cash.
        val result = build(listOf(record("2026-09-05", 500.0, accountId = 2L)))

        assertEquals(500.0, result.cardExpense, 0.0)
        assertEquals(0.0, result.cashAndAccountsExpense, 0.0)
    }

    @Test
    fun anyGroupNamedCardCountsIncludingDebitCard() {
        val result = build(listOf(record("2026-09-05", 300.0, accountId = 3L)))

        assertEquals(300.0, result.cardExpense, 0.0)
    }

    @Test
    fun otherGroupsAndUnknownOrMissingAccountsCountAsCashAndAccounts() {
        val result = build(
            listOf(
                record("2026-09-01", 100.0, accountId = 1L),   // Cash
                record("2026-09-02", 200.0, accountId = 4L),   // Savings
                record("2026-09-03", 400.0, accountId = 99L),  // an account that no longer exists
                record("2026-09-04", 800.0, accountId = null)  // no account recorded
            )
        )

        assertEquals(1500.0, result.cashAndAccountsExpense, 0.0)
        assertEquals(0.0, result.cardExpense, 0.0)
    }

    @Test
    fun cardAndCashTogetherAccountForAllExpenses() {
        val result = build(
            listOf(record("2026-09-01", 100.0, accountId = 1L), record("2026-09-02", 250.0, accountId = 2L))
        )

        assertEquals(350.0, result.cashAndAccountsExpense + result.cardExpense, 0.0)
    }

    @Test
    fun transfersAreCountedByTypeAndNeverAsSpending() {
        val result = build(
            listOf(
                record("2026-09-01", 1000.0, type = "Transfer"),
                record("2026-09-02", 50.0)
            )
        )

        assertEquals(1000.0, result.transferTotal, 0.0)
        assertEquals(50.0, result.cashAndAccountsExpense, 0.0)
    }

    @Test
    fun onlyTheSelectedPeriodIsCounted() {
        val result = build(listOf(record("2026-08-31", 999.0), record("2026-09-01", 10.0), record("2026-10-01", 999.0)))

        assertEquals(10.0, result.cashAndAccountsExpense, 0.0)
    }

    @Test
    fun changeIsMeasuredAgainstThePrecedingPeriodOfTheSameLength() {
        // September is 30 days, so the comparison window is 2 Aug - 31 Aug.
        val result = build(listOf(record("2026-08-15", 100.0), record("2026-09-15", 150.0)))

        assertEquals(50, result.changePercent)
    }

    @Test
    fun aFallInSpendingIsNegative() {
        val result = build(listOf(record("2026-08-15", 200.0), record("2026-09-15", 150.0)))

        assertEquals(-25, result.changePercent)
    }

    @Test
    fun noEarlierSpendingMeansNoComparison() {
        assertNull(build(listOf(record("2026-09-15", 150.0))).changePercent)
    }

    @Test
    fun aOneDayPeriodComparesAgainstTheDayBefore() {
        val day = StatsDateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10))
        val result = build(listOf(record("2026-09-09", 100.0), record("2026-09-10", 200.0)), day)

        assertEquals(100, result.changePercent)
    }
}
