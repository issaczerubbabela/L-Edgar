package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.DropdownRole
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NetWorthScreenTest {

    private val today = LocalDate.of(2026, 10, 8)
    private val accounts = listOf(
        AccountRecord(id = 1, groupName = "Bank", accountName = "HDFC Savings", initialBalance = 1000.0, initialBalanceDate = "2026-06-30"),
        AccountRecord(id = 2, groupName = "Credit card", accountName = "HDFC Millennia", initialBalance = -500.0, initialBalanceDate = "2026-06-30"),
        AccountRecord(id = 3, groupName = "Cash", accountName = "Mom's wallet", initialBalance = 200.0, initialBalanceDate = "2026-06-30", includeInTotals = false)
    )
    private val roles = listOf(DropdownOption(optionType = "ACCOUNT_GROUP", name = "Credit card", displayOrder = 0, role = DropdownRole.LIABILITY))

    private var nextId = 1L
    private fun txn(date: String, type: String, amount: Double, accountId: Long? = null, from: Long? = null, to: Long? = null) =
        ExpenseRecord(id = nextId++, date = date, type = type, category = "", description = "", amount = amount,
            accountId = accountId, remarks = "", fromAccountId = from, toAccountId = to)

    private val transactions = listOf(
        txn("2026-07-10", "Income", 300.0, accountId = 1),
        txn("2026-08-03", "Expense", 100.0, accountId = 1),
        txn("2026-08-20", "Transfer", 50.0, from = 1, to = 3),   // leaves the Accounts in totals: money out
        txn("2026-09-12", "Transfer", 200.0, from = 1, to = 2),  // card bill between included Accounts: no cash flow
        txn("2026-10-02", "Adjustment", 10.0, accountId = 1),
        txn("2026-10-05", "Expense", 40.0, accountId = 2)
    )

    private val state = netWorthScreen(AccountBook.of(accounts, roles, transactions), today, NetWorthPeriod.SIX_MONTHS)

    @Test
    fun historyStartsAtTheFirstMonthWithDataAndShowsEachMonthEnd() {
        assertEquals(listOf("Jun", "Jul", "Aug", "Sep", "Oct"), state.history.map { it.shortLabel })
        assertEquals(listOf(500.0, 800.0, 650.0, 650.0, 620.0), state.history.map { it.netWorth })
        assertNull(state.history.first().change)
        assertEquals("−₹30.00 vs Sep", state.history.last().change)
        assertTrue(state.showHistoryChart)
        assertEquals("Net worth went from ₹500.00 in Jun 2026 to ₹620.00 in Oct 2026.", state.historySummary)
    }

    @Test
    fun theHeaderShowsNetWorthAndThisMonthsChange() {
        assertEquals("₹620.00", state.netWorth)
        assertEquals("−₹30.00 since 1 Oct", state.monthChange)
        assertTrue(state.isMonthChangeNegative)
    }

    @Test
    fun cashFlowSkipsTransfersBetweenIncludedAccountsAndKeepsAdjustmentsApart() {
        val flows = state.cashFlow.associateBy { it.shortLabel }
        assertEquals("₹300.00", flows.getValue("Jul").moneyInText)
        assertEquals("₹150.00", flows.getValue("Aug").moneyOutText)
        assertEquals("₹0.00", flows.getValue("Sep").moneyOutText)
        assertEquals("adj +₹10.00", flows.getValue("Oct").adjustments)
        assertEquals("−₹30.00", flows.getValue("Oct").net)
        assertEquals("Oct 2026 so far: in ₹0.00, out ₹40.00, adjustments +₹10.00.", state.cashFlowSummary)
    }

    @Test
    fun sharesSplitAssetsAndLiabilitiesByGroupLeavingOutExcludedAccounts() {
        assertEquals(listOf("Bank"), state.assetShares.map { it.name })
        assertEquals("₹960.00", state.assetShares.single().amount)
        assertEquals("100.0%", state.assetShares.single().percent)
        assertEquals(listOf("Credit card"), state.liabilityShares.map { it.name })
        assertEquals("₹340.00", state.liabilityShares.single().amount)
    }

    @Test
    fun aGroupBelowZeroIsStillListedSoTheRowsAddUpToAssets() {
        val overdrawn = accounts + AccountRecord(id = 4, groupName = "Cash", accountName = "Wallet", initialBalance = -60.0, initialBalanceDate = "2026-06-30")
        val shares = netWorthScreen(AccountBook.of(overdrawn, roles, transactions), today, NetWorthPeriod.SIX_MONTHS)
        assertEquals("₹900.00", shares.assetsTotal)
        assertEquals(listOf("Bank" to "100.0%", "Cash" to "below zero"), shares.assetShares.map { it.name to it.percent })
        assertEquals("−₹60.00", shares.assetShares.last().amount)
    }

    @Test
    fun moversAddUpToThisMonthsChange() {
        assertEquals(listOf("HDFC Millennia" to "−₹40.00", "HDFC Savings" to "+₹10.00"), state.movers.map { it.name to it.change })
        assertEquals("Change since 30 Sep. Together: −₹30.00", state.moversNote)
    }

    @Test
    fun underFourMonthsOfDataShowsFiguresInsteadOfALine() {
        val recent = accounts.map { it.copy(initialBalanceDate = "2026-08-31") }
        val short = netWorthScreen(AccountBook.of(recent, roles, transactions.filter { it.date >= "2026-09-01" }), today, NetWorthPeriod.ONE_YEAR)
        assertEquals(listOf("Aug", "Sep", "Oct"), short.history.map { it.shortLabel })
        assertFalse(short.showHistoryChart)
    }
}
