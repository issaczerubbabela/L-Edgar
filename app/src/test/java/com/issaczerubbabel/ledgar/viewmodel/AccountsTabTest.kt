package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.DropdownRole
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AccountsTabTest {

    private val asOf = "2026-09-30"
    private fun account(id: Long, group: String, name: String, balance: Double, included: Boolean = true, hidden: Boolean = false) =
        AccountRecord(id = id, groupName = group, accountName = name, initialBalance = balance, initialBalanceDate = asOf,
            includeInTotals = included, isHidden = hidden, displayOrder = id.toInt())

    // The design canvas's Accounts tab, as balances at the end of 30 Sep.
    private val records = listOf(
        account(1, "Bank", "HDFC Savings", 79110.0),
        account(2, "Bank", "SBI Salary", 8250.5),
        account(3, "Cash", "Wallet", 3390.0),
        account(4, "Cash", "Mom's wallet", 5000.0, included = false),
        account(5, "Credit card", "HDFC Millennia", -16490.0),
        account(6, "Investments", "PPF", 150000.0),
        account(7, "Investments", "Mutual funds", 62300.0),
        account(8, "Credit card", "Amazon Pay ICICI", 500.0),
        account(9, "Loan", "Bike loan", -45500.0),
        account(10, "Bank", "Old Axis account", 0.0, included = false, hidden = true)
    )
    private val roles = listOf(
        DropdownOption(optionType = "ACCOUNT_GROUP", name = "Credit card", displayOrder = 0, role = DropdownRole.LIABILITY),
        DropdownOption(optionType = "ACCOUNT_GROUP", name = "Loan", displayOrder = 1, role = DropdownRole.LIABILITY)
    )

    private var nextId = 1L
    private fun txn(type: String, amount: Double, accountId: Long? = null, from: Long? = null, to: Long? = null) =
        ExpenseRecord(id = nextId++, date = "2026-10-05", type = type, category = "", description = "", amount = amount,
            accountId = accountId, remarks = "", fromAccountId = from, toAccountId = to)

    // October moves: +5,140, +4,180, −2,150, −1,250 and +500, together +6,420.
    private val october = listOf(
        txn("Income", 5140.0, accountId = 1),
        txn("Income", 4180.0, accountId = 2),
        txn("Expense", 2150.0, accountId = 5),
        txn("Expense", 1250.0, accountId = 3),
        txn("Transfer", 500.0, from = 6, to = 9),
        txn("Income", 500.0, accountId = 6)
    )

    private val state = accountsTab(AccountBook.of(records, roles, october), LocalDate.of(2026, 10, 8))

    @Test
    fun theHeaderShowsNetWorthAndThisMonthsChange() {
        assertEquals("₹3,11,120.50", state.assets)
        assertEquals("₹63,140.00", state.liabilities)
        assertEquals("₹2,47,980.50", state.netWorth)
        assertEquals("+₹6,420.00 since 1 Oct", state.monthChange)
        assertFalse(state.isMonthChangeNegative)
    }

    @Test
    fun assetGroupsComeFirstThenLiabilitiesEachInDisplayOrder() {
        assertEquals(listOf("Bank", "Cash", "Investments", "Credit card", "Loan"), state.groups.map { it.name })
        assertEquals(listOf(false, false, false, true, true), state.groups.map { it.isLiability })
    }

    @Test
    fun subtotalsCountOnlyAccountsIncludedInTotals() {
        val subtotals = state.groups.associate { it.name to it.subtotal }
        assertEquals("₹96,680.50", subtotals["Bank"])
        assertEquals("₹2,140.00", subtotals["Cash"])
        assertEquals("−₹18,140.00", subtotals["Credit card"])
        assertTrue(state.groups.single { it.name == "Loan" }.isSubtotalNegative)
    }

    @Test
    fun rowsShowSignedBalancesAndWhetherTheyCount() {
        val rows = state.groups.flatMap { it.rows }.associateBy { it.name }
        assertEquals("−₹18,640.00", rows.getValue("HDFC Millennia").balance)
        assertTrue(rows.getValue("HDFC Millennia").isNegative)
        assertEquals("₹500.00", rows.getValue("Amazon Pay ICICI").balance)
        assertFalse(rows.getValue("Mom's wallet").isIncludedInTotals)
    }

    @Test
    fun hiddenAccountsLeaveTheirGroupButStayInEditOrder() {
        assertEquals(listOf("Old Axis account"), state.hiddenAccounts.map { it.name })
        val bank = state.groups.first()
        assertEquals(listOf("HDFC Savings", "SBI Salary"), bank.rows.map { it.name })
        assertEquals(listOf(1L, 2L, 10L), bank.allRows.map { it.id })
    }

    @Test
    fun rowsSayWhenTheyWereLastCheckedAndFlagStaleOnes() {
        val checked = records.map {
            when (it.id) {
                1L -> it.copy(reconciledAt = "2026-10-08")
                3L -> it.copy(reconciledAt = "2026-10-06")
                9L -> it.copy(reconciledAt = "2026-08-29")
                else -> it
            }
        }
        val rows = accountsTab(AccountBook.of(checked, roles, october), LocalDate.of(2026, 10, 8))
            .groups.flatMap { it.rows }.associateBy { it.name }
        assertEquals("Reconciled today", rows.getValue("HDFC Savings").lastChecked)
        assertEquals("Counted 2 days ago", rows.getValue("Wallet").lastChecked)
        assertEquals("Reconciled 40 days ago", rows.getValue("Bike loan").lastChecked)
        assertTrue(rows.getValue("Bike loan").isStale)
        assertFalse(rows.getValue("HDFC Savings").isStale)
        assertEquals("Not reconciled yet", rows.getValue("SBI Salary").lastChecked)
        assertFalse(rows.getValue("SBI Salary").isStale)
    }

    @Test
    fun transfersNamingAnAccountYouDontHaveAreListedToLink() {
        val unlinked = ExpenseRecord(id = 90, date = "2026-10-05", type = "Transfer", category = "Transfer", description = "",
            amount = 2000.0, remarks = "", fromAccountId = 1, fromAccountName = "HDFC Savings", toAccountName = "Axis Old")
        val linked = ExpenseRecord(id = 91, date = "2026-10-06", type = "Transfer", category = "Transfer", description = "",
            amount = 500.0, remarks = "", fromAccountId = 1, toAccountId = 2, toAccountName = "SBI Salary")
        val state = accountsTab(AccountBook.of(records, roles, october + unlinked + linked), LocalDate.of(2026, 10, 8))
        assertEquals(listOf(90L), state.unlinkedTransfers.map { it.transactionId })
        assertEquals("5 Oct 2026 · ₹2,000.00 · HDFC Savings → Axis Old", state.unlinkedTransfers.single().summary)
        assertTrue(this.state.unlinkedTransfers.isEmpty())
    }

    @Test
    fun movingAnAccountReordersOnlyItsGroup() {
        val order = orderAfterMovingAccount(state.groups, "Bank", from = 2, to = 0)
        assertEquals(listOf(10L, 1L, 2L, 3L, 4L, 6L, 7L, 5L, 8L, 9L), order)
    }

    @Test
    fun movingAGroupMovesAllItsAccounts() {
        val order = orderAfterMovingGroup(state.groups, from = 2, to = 0)
        assertEquals(listOf(6L, 7L, 1L, 2L, 10L, 3L, 4L, 5L, 8L, 9L), order)
    }

    @Test
    fun aMoveOutOfRangeLeavesTheOrderAlone() {
        val unchanged = state.groups.flatMap { group -> group.allRows.map { it.id } }
        assertEquals(unchanged, orderAfterMovingAccount(state.groups, "Bank", from = 7, to = 0))
    }
}
