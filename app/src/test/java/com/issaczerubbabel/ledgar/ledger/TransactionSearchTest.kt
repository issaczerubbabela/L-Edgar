package com.issaczerubbabel.ledgar.ledger

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.viewmodel.StatsRoles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TransactionSearchTest {

    private val accounts = listOf(
        AccountRecord(id = 1, groupName = "Bank", accountName = "HDFC Savings", initialBalance = 0.0),
        AccountRecord(id = 2, groupName = "Savings", accountName = "PPF", initialBalance = 0.0),
        AccountRecord(id = 3, groupName = "Cash", accountName = "Wallet", initialBalance = 0.0)
    )
    private val today = LocalDate.of(2026, 10, 13)

    private var nextId = 1L
    private fun txn(
        type: String, amount: Double, category: String = "", description: String = "", date: String = "2026-10-10",
        accountId: Long? = null, from: Long? = null, to: Long? = null, note: String = "", syncAction: String = "NONE"
    ) = ExpenseRecord(id = nextId++, date = date, type = type, category = category, description = description,
        amount = amount, accountId = accountId, remarks = note, fromAccountId = from, toAccountId = to, syncAction = syncAction)

    private val plumber = txn("Expense", 1499.0, "Home", "Plumber", accountId = 1, note = "kitchen tap")
    private val groceries = txn("Expense", 812.40, "Groceries", "DMart", accountId = 3)
    private val salary = txn("Income", 52000.0, "Salary", "October salary", accountId = 1)
    private val refund = txn("Income", 300.0, "Refund", "Myntra return", accountId = 1)
    private val sip = txn("Expense", 5000.0, "Investments", "SIP", accountId = 1)
    private val toPpf = txn("Transfer", 2000.0, "Transfer", "", from = 1, to = 2)
    private val all = listOf(plumber, groceries, salary, refund, sip, toPpf)

    private fun search(query: String = "", filter: SearchFilter = SearchFilter(), records: List<ExpenseRecord> = all) =
        TransactionSearch.search(records, query, filter, accounts)

    @Test
    fun theBoxMatchesDescriptionRemarksCategoryAndAccount() {
        assertEquals(listOf(plumber), search("plumb"))
        assertEquals(listOf(plumber), search("kitchen"))
        assertEquals(listOf(groceries), search("grocer"))
        assertEquals(listOf(groceries), search("wallet"))
        assertEquals(listOf(toPpf), search("ppf"))
    }

    @Test
    fun aTransfersFromAccountMatchesToo() {
        assertTrue(toPpf in search("HDFC"))
    }

    @Test
    fun anAmountMatchesWithOrWithoutGroupingOrPaise() {
        assertEquals(listOf(plumber), search("1499"))
        assertEquals(listOf(plumber), search("1,499"))
        assertEquals(listOf(plumber), search("1499.00"))
        assertEquals(listOf(groceries), search("812"))
        assertEquals(listOf(groceries), search("812.4"))
        assertTrue(search("812.5").isEmpty())
    }

    @Test
    fun aNumberIsReadAsAnAmountOnlyWhenItIsOne() {
        assertEquals(1499.0, TransactionSearch.amountQuery("1,499")!!, 0.0)
        assertNull(TransactionSearch.amountQuery("SIP 2"))
        assertNull(TransactionSearch.amountQuery(""))
    }

    @Test
    fun deletedTransactionsNeverAppear() {
        val deleted = txn("Expense", 1499.0, "Home", "Plumber", syncAction = "DELETE")
        assertEquals(listOf(plumber), search("plumber", records = all + deleted))
    }

    @Test
    fun theTypeChipNarrowsToOneType() {
        assertEquals(listOf(salary, refund), search(filter = SearchFilter(type = "Income")))
    }

    @Test
    fun choosingATypeDropsCategoriesOfTheOtherType() {
        val filter = SearchFilter(picks = LedgerFilter(expenseCategories = setOf("Home"), incomeCategories = setOf("Salary")))

        val narrowed = filter.withType("Expense")

        assertEquals(setOf("Home"), narrowed.picks.expenseCategories)
        assertTrue(narrowed.picks.incomeCategories.isEmpty())
        assertTrue(filter.withType("Transfer").picks.hasCategories.not())
    }

    @Test
    fun dateCategoryAccountAndAmountCombine() {
        val filter = SearchFilter(
            start = LocalDate.of(2026, 10, 1), end = LocalDate.of(2026, 10, 31),
            picks = LedgerFilter(accountIds = setOf(1)), minAmount = 1000.0, maxAmount = 6000.0
        )
        assertEquals(listOf(plumber, sip, toPpf), search(filter = filter))
    }

    @Test
    fun nothingIsSearchedUntilThereIsAQueryOrAFilter() {
        assertFalse(TransactionSearch.isActive("  ", SearchFilter()))
        assertTrue(TransactionSearch.isActive("", SearchFilter(type = "Expense")))
    }

    @Test
    fun totalsFollowStatsRules() {
        val roles = StatsRoles(savingCategories = setOf("investments"), refundCategories = setOf("refund"), savingsGroups = setOf("savings"))

        val totals = TransactionSearch.totals(all, accounts, roles)

        assertEquals(6, totals.count)
        // Spent leaves out the Saving Category and is reduced by the Refund.
        assertEquals(1499.0 + 812.40 - 300.0, totals.spent, 0.001)
        assertEquals(52000.0, totals.earned, 0.0)
    }

    @Test
    fun everyChipIsShownAndTheChosenOnesSayWhatTheyHold() {
        val chips = TransactionSearch.chips(
            SearchFilter(type = "Expense", picks = LedgerFilter(expenseCategories = setOf("Home"), accountIds = setOf(1)), minAmount = 500.0),
            accounts
        )

        assertEquals(SearchFilterKind.entries, chips.map { it.kind })
        assertEquals(listOf("Expense", null, "Home", "HDFC Savings", "₹500 or more"), chips.map { it.value })
    }

    @Test
    fun anAmountRangeReadsNaturally() {
        assertEquals("₹500 – ₹2,000", TransactionSearch.amountLabel(500.0, 2000.0))
        assertEquals("Up to ₹2,000", TransactionSearch.amountLabel(null, 2000.0))
    }

    @Test
    fun datePresetsIncludeTheRunningCycleWhenThereIsOne() {
        val cycle = BudgetCycle(id = 1, startDate = "2026-09-28", endDate = "2026-10-27", spendableAmount = 40000.0)

        val presets = TransactionSearch.datePresets(today, cycle)

        assertEquals(listOf("This cycle", "This month", "Last month", "Last 3 months"), presets.map { it.label })
        assertEquals(LocalDate.of(2026, 9, 28) to LocalDate.of(2026, 10, 27), presets[0].start to presets[0].end)
        assertEquals(LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30), presets[2].start to presets[2].end)
        assertEquals(LocalDate.of(2026, 8, 1) to today, presets[3].start to presets[3].end)
        assertEquals(listOf("This month", "Last month", "Last 3 months"), TransactionSearch.datePresets(today, null).map { it.label })
    }

    @Test
    fun recentCategoriesAreTheLatestDistinctOnes() {
        val records = listOf(
            txn("Expense", 1.0, "Food", date = "2026-10-12"),
            txn("Expense", 1.0, "Fuel", date = "2026-10-11"),
            txn("Expense", 1.0, "Food", date = "2026-10-10"),
            txn("Transfer", 1.0, "Transfer", date = "2026-10-13"),
            txn("Income", 1.0, "Salary", date = "2026-10-01")
        )
        assertEquals(
            listOf(RecentCategory("Food", false), RecentCategory("Fuel", false), RecentCategory("Salary", true)),
            TransactionSearch.recentCategories(records, 4)
        )
    }
}
