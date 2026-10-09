package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.account.StatementRowKind
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AccountPageTest {

    private val savings = AccountRecord(id = 1, groupName = "Bank", accountName = "HDFC Savings", initialBalance = 79110.0, initialBalanceDate = "2026-09-30")
    private val salary = AccountRecord(id = 2, groupName = "Bank", accountName = "SBI Salary", initialBalance = 0.0)
    private val card = AccountRecord(id = 3, groupName = "Credit card", accountName = "HDFC Millennia", initialBalance = 0.0)

    private var nextId = 1L
    private fun txn(date: String, type: String, amount: Double, category: String = "", description: String = "",
                    accountId: Long? = null, from: Long? = null, to: Long? = null) =
        ExpenseRecord(id = nextId++, date = date, type = type, category = category, description = description,
            amount = amount, accountId = accountId, remarks = "", fromAccountId = from, toAccountId = to)

    // The design canvas's October: 79,110 + 43,840 − 38,700 = 84,250.
    private val october = listOf(
        txn("2026-10-01", "Transfer", 40000.0, description = "Moved salary", from = 2, to = 1),
        txn("2026-10-02", "Expense", 22000.0, "Rent", "October rent", accountId = 1),
        txn("2026-10-03", "Transfer", 12500.0, description = "Card bill", from = 1, to = 3),
        txn("2026-10-05", "Expense", 1860.0, "Groceries", "Swiggy Instamart", accountId = 1),
        txn("2026-10-06", "Income", 3500.0, "Freelance", "Logo project", accountId = 1),
        txn("2026-10-07", "Expense", 2340.0, "Electricity", "BESCOM bill", accountId = 1),
        txn("2026-10-08", "Adjustment", 340.0, accountId = 1)
    )

    private fun page(transactions: List<ExpenseRecord>) =
        accountPage(AccountBook.of(listOf(savings, salary, card), emptyList(), transactions), accountId = 1)!!

    @Test
    fun theMonthHeaderAddsUpAcrossTransfersAndAdjustments() {
        val month = page(october).months.single()
        assertEquals("October 2026", month.label)
        assertEquals("₹79,110.00", month.opening)
        assertEquals("₹43,840.00", month.moneyIn)
        assertEquals("₹38,700.00", month.moneyOut)
        assertEquals("₹84,250.00", month.closing)
        assertEquals("Income ₹3,500.00 · Transfers ₹40,000.00 · Adjustments ₹340.00", month.inSplit)
        assertEquals("Expenses ₹26,200.00 · Transfers ₹12,500.00", month.outSplit)
    }

    @Test
    fun balanceTodayIsTheLatestClosing() {
        val state = page(october)
        assertEquals("₹84,250.00", state.balanceToday)
        assertFalse(state.isBalanceNegative)
        assertEquals("HDFC Savings", state.accountName)
        assertEquals("Bank", state.groupName)
    }

    @Test
    fun rowsAreNewestFirstWithSignedAmountsAndTheBalanceAfter() {
        val rows = page(october).months.single().rows
        assertEquals(StatementRowKind.ADJUSTMENT, rows.first().kind)
        assertEquals("Balance adjustment", rows.first().title)
        assertEquals("+₹340.00", rows.first().amount)
        assertEquals("Bal ₹84,250.00", rows.first().balanceAfter)
        val rent = rows.single { it.title == "Rent" }
        assertEquals("−₹22,000.00", rent.amount)
        assertEquals("2 Oct · October rent", rent.subtitle)
        assertEquals("Bal ₹97,110.00", rent.balanceAfter)
    }

    @Test
    fun transfersNameTheOtherAccount() {
        val rows = page(october).months.single().rows
        assertEquals("+₹40,000.00", rows.single { it.title == "← SBI Salary" }.amount)
        assertEquals("−₹12,500.00", rows.single { it.title == "→ HDFC Millennia" }.amount)
    }

    @Test
    fun rowsOnOrBeforeTheAsOfDateAreListedButDontCount() {
        val state = page(october + txn("2026-09-30", "Expense", 500.0, "Fuel", accountId = 1))
        val september = state.months.last()
        val fuel = september.rows.single()
        assertFalse(fuel.counts)
        assertEquals("30 Sep · Before the As-of date, not counted", fuel.subtitle)
        assertEquals("", fuel.balanceAfter)
        assertEquals("₹84,250.00", state.balanceToday)
    }

    @Test
    fun anAccountThatNoLongerExistsHasNoPage() {
        assertNull(accountPage(AccountBook.of(listOf(salary), emptyList(), emptyList()), accountId = 1))
    }
}
