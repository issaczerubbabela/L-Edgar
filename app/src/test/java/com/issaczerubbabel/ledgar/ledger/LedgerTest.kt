package com.issaczerubbabel.ledgar.ledger

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LedgerTest {

    private val accounts = listOf(
        AccountRecord(id = 1, groupName = "Bank", accountName = "HDFC Savings", initialBalance = 0.0),
        AccountRecord(id = 2, groupName = "Credit card", accountName = "HDFC Credit card", initialBalance = 0.0),
        AccountRecord(id = 3, groupName = "Cash", accountName = "Wallet", initialBalance = 0.0)
    )
    private val october = YearMonth.of(2026, 10)
    private val today = LocalDate.of(2026, 10, 9)

    private var nextId = 1L
    private fun txn(
        date: String, type: String, amount: Double, category: String = "", description: String = "",
        accountId: Long? = null, from: Long? = null, to: Long? = null, id: Long = nextId++, note: String = ""
    ) = ExpenseRecord(id = id, date = date, type = type, category = category, description = description,
        amount = amount, accountId = accountId, remarks = note, fromAccountId = from, toAccountId = to)

    private fun build(transactions: List<ExpenseRecord>, month: YearMonth = october, pending: Set<Long> = emptySet()) =
        Ledger.build(transactions, accounts, month, today, pending)

    @Test
    fun netIsIncomeMinusExpensesLeavingOutTransfersAndBalanceAdjustments() {
        val ledger = build(listOf(
            txn("2026-10-01", "Income", 50000.0, "Salary", accountId = 1),
            txn("2026-10-02", "Expense", 212.0, "Transport", accountId = 2),
            txn("2026-10-03", "Expense", 4038.0, "Food", accountId = 3),
            txn("2026-10-04", "Transfer", 10000.0, from = 1, to = 3),
            txn("2026-10-05", "Adjustment", -340.0, accountId = 3),
            txn("2026-09-30", "Expense", 999.0, "Food", accountId = 3)
        ))

        assertEquals("₹50,000", ledger.summary.income)
        assertEquals("₹4,250", ledger.summary.expenses)
        assertEquals("₹45,750", ledger.summary.net)
    }

    @Test
    fun savingCategoriesCountAsExpensesAndRefundsAsIncome() {
        val ledger = build(listOf(
            txn("2026-10-01", "Expense", 5000.0, "Savings", accountId = 1),
            txn("2026-10-02", "Income", 1200.0, "Refund", accountId = 2)
        ))

        assertEquals("₹1,200", ledger.summary.income)
        assertEquals("₹5,000", ledger.summary.expenses)
        assertEquals("−₹3,800", ledger.summary.net)
    }

    @Test
    fun daysRunNewestFirstAndADaysRowsNewestLoggedFirst() {
        val ledger = build(listOf(
            txn("2026-10-02", "Expense", 100.0, "Food", id = 7),
            txn("2026-10-05", "Expense", 200.0, "Food", id = 3),
            txn("2026-10-02", "Expense", 300.0, "Food", id = 12),
            txn("2026-10-02", "Income", 400.0, "Salary", id = 9)
        ))

        assertEquals(listOf(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 2)), ledger.days.map { it.date })
        assertEquals(listOf(12L, 9L, 7L), ledger.days[1].rows.map { it.id })
    }

    @Test
    fun aDayHeaderShowsItsDayAndWeekdayAndOnlyTheSidesThatMoved() {
        val ledger = build(listOf(
            txn("2026-10-09", "Expense", 212.0, "Transport"),
            txn("2026-10-09", "Expense", 1000.5, "Food"),
            txn("2026-10-08", "Income", 125000.0, "Salary"),
            txn("2026-10-08", "Transfer", 500.0, from = 1, to = 3)
        ))

        val friday = ledger.days[0]
        assertEquals("9", friday.dayNumber)
        assertEquals("Fri", friday.weekday)
        assertEquals(null, friday.income)
        assertEquals("₹1,212.50", friday.expense)
        val thursday = ledger.days[1]
        assertEquals("₹1,25,000", thursday.income)
        assertEquals(null, thursday.expense)
    }

    @Test
    fun aRowCarriesItsCategoryAccountAndAmountInFull() {
        val ledger = build(listOf(
            txn("2026-10-09", "Expense", 125000.0, "Rent", "October rent", accountId = 1, id = 1),
            txn("2026-10-09", "Transfer", 5000.0, from = 1, to = 3, id = 2),
            txn("2026-10-09", "Adjustment", -340.0, "Food", accountId = 3, id = 3),
            txn("2026-10-09", "Income", 50.0, "Cashback", accountId = 99, id = 4)
        ))
        val rows = ledger.days.single().rows.associateBy { it.id }

        assertEquals("Rent", rows[1L]!!.category)
        assertEquals("October rent", rows[1L]!!.description)
        assertEquals("HDFC Savings", rows[1L]!!.accountLabel)
        assertEquals("₹1,25,000", rows[1L]!!.amount)
        assertEquals("HDFC Savings → Wallet", rows[2L]!!.accountLabel)
        assertEquals("Balance adjustment", rows[3L]!!.category)
        assertEquals("Balance adjustment", rows[3L]!!.description)
        assertEquals("−₹340", rows[3L]!!.amount)
        assertEquals("Cashback", rows[4L]!!.description)
    }

    @Test
    fun aDaySheetHasItsTitleAndAnEmptyDayStillOpens() {
        val ledger = build(listOf(txn("2026-10-09", "Expense", 212.0, "Transport")))

        val friday = Ledger.day(ledger, LocalDate.of(2026, 10, 9))
        assertEquals("Fri, 9 Oct", friday.title)
        assertEquals("9 Oct", friday.shortDate)
        assertEquals("₹212", friday.expense)
        assertEquals(1, friday.rows.size)

        val empty = Ledger.day(ledger, LocalDate.of(2026, 10, 1))
        assertEquals("Thu, 1 Oct", empty.title)
        assertEquals(null, empty.income)
        assertEquals(null, empty.expense)
        assertEquals(emptyList<LedgerRow>(), empty.rows)
    }

    @Test
    fun aMonthStartingOnSundayFillsFiveWeeksFromItsFirst() {
        val cells = build(emptyList(), month = YearMonth.of(2026, 2)).calendar

        assertEquals(35, cells.size)
        assertEquals(LocalDate.of(2026, 2, 1), cells.first().date)
        assertEquals(28, cells.count { it.isInMonth })
        assertEquals(LocalDate.of(2026, 3, 7), cells.last().date)
    }

    @Test
    fun aThirtyOneDayMonthStartingOnSaturdayNeedsSixWeeks() {
        val cells = build(emptyList(), month = YearMonth.of(2026, 8)).calendar

        assertEquals(42, cells.size)
        assertEquals(LocalDate.of(2026, 7, 26), cells.first().date)
        assertEquals(false, cells[5].isInMonth)
        assertEquals(LocalDate.of(2026, 8, 1), cells[6].date)
    }

    @Test
    fun octoberStartsOnThursdayAndMarksToday() {
        val cells = build(emptyList()).calendar

        assertEquals(35, cells.size)
        assertEquals(LocalDate.of(2026, 9, 27), cells.first().date)
        assertEquals(listOf(LocalDate.of(2026, 10, 9)), cells.filter { it.isToday }.map { it.date })
    }

    @Test
    fun aCellShowsShortAmountsAndANegativeNetKeepsItsMinus() {
        val ledger = build(listOf(
            txn("2026-10-09", "Income", 200.0, "Refund"),
            txn("2026-10-09", "Expense", 1500.0, "Food"),
            txn("2026-10-09", "Transfer", 9000.0, from = 1, to = 3),
            txn("2026-10-08", "Expense", 150000.0, "Rent")
        ))
        val friday = ledger.calendar.single { it.date == LocalDate.of(2026, 10, 9) }
        val thursday = ledger.calendar.single { it.date == LocalDate.of(2026, 10, 8) }

        assertEquals(-1300.0, friday.net, 0.0)
        assertEquals("200", friday.incomeLabel)
        assertEquals("1.5k", friday.expenseLabel)
        assertEquals("−1.3k", friday.netLabel)
        assertEquals(3, friday.transactionCount)
        assertEquals(null, thursday.incomeLabel)
        assertEquals("1.5L", thursday.expenseLabel)
        assertEquals("−1.5L", thursday.netLabel)
    }

    @Test
    fun aDayOutsideTheMonthCarriesNoFigures() {
        val ledger = build(listOf(txn("2026-09-30", "Expense", 500.0, "Food")))
        val lastOfSeptember = ledger.calendar.single { it.date == LocalDate.of(2026, 9, 30) }

        assertEquals(false, lastOfSeptember.isInMonth)
        assertEquals(0, lastOfSeptember.transactionCount)
        assertEquals(null, lastOfSeptember.expenseLabel)
    }

    @Test
    fun aTransactionWaitingOnUndoIsLeftOutOfEveryFigure() {
        val transactions = listOf(
            txn("2026-10-09", "Expense", 212.0, "Transport", id = 1),
            txn("2026-10-09", "Expense", 300.0, "Food", id = 2),
            txn("2026-10-08", "Income", 1000.0, "Salary", id = 3)
        )
        val ledger = build(transactions, pending = setOf(2L, 3L))

        assertEquals("₹0", ledger.summary.income)
        assertEquals("₹212", ledger.summary.expenses)
        assertEquals("−₹212", ledger.summary.net)
        assertEquals(listOf(LocalDate.of(2026, 10, 9)), ledger.days.map { it.date })
        assertEquals(listOf(1L), ledger.days.single().rows.map { it.id })
        assertEquals("₹212", ledger.days.single().expense)
        val friday = ledger.calendar.single { it.date == LocalDate.of(2026, 10, 9) }
        assertEquals("212", friday.expenseLabel)
        assertEquals(1, friday.transactionCount)
        assertEquals(null, ledger.calendar.single { it.date == LocalDate.of(2026, 10, 8) }.netLabel)
    }

    @Test
    fun aRowOutsideTheLedgerLooksTheSameAsOnIt() {
        val transfer = txn("2026-03-14", "Transfer", 5000.0, from = 1, to = 3)

        val row = Ledger.row(transfer, accounts)

        assertEquals(build(listOf(transfer), month = YearMonth.of(2026, 3)).days.single().rows.single(), row)
    }

    @Test
    fun theSheetShowsEverythingAboutAnExpense() {
        val details = Ledger.details(
            txn("2026-10-09", "Expense", 1212.5, "Transport", "Uber to office", accountId = 2, note = "Client visit"),
            accounts
        )

        assertEquals("₹1,212.50", details.amount)
        assertEquals("Expense", details.typeLabel)
        assertEquals("Uber to office", details.description)
        assertEquals("Fri, 9 Oct 2026", details.date)
        assertEquals("9 Oct 2026", details.shortDate)
        assertEquals("HDFC Credit card", details.accounts)
        assertEquals("Transport", details.category)
        assertEquals("Client visit", details.note)
    }

    @Test
    fun aTransferOrBalanceAdjustmentHasNoCategoryInTheSheet() {
        val transfer = Ledger.details(txn("2026-10-09", "Transfer", 5000.0, "Food", from = 1, to = 3), accounts)
        val adjustment = Ledger.details(txn("2026-10-09", "Adjustment", -340.0, accountId = 3), accounts)

        assertEquals(null, transfer.category)
        assertEquals("HDFC Savings → Wallet", transfer.accounts)
        assertEquals("Transfer", transfer.description)
        assertEquals(null, adjustment.category)
        assertEquals("Balance adjustment", adjustment.typeLabel)
        assertEquals("−₹340", adjustment.amount)
        assertEquals(null, adjustment.note)
    }

    @Test
    fun theUndoMessageNamesWhatWasDeleted() {
        assertEquals("Deleted “Uber to office” · ₹212",
            Ledger.deletedMessage(txn("2026-10-09", "Expense", 212.0, "Transport", "Uber to office"), accounts))
        assertEquals("Deleted “Transport” · ₹1,250.50",
            Ledger.deletedMessage(txn("2026-10-09", "Expense", 1250.5, "Transport"), accounts))
    }

    @Test
    fun aBatchDeleteAsksWithTheCountAndTheNetOfTheSelection() {
        val selected = listOf(
            txn("2026-10-09", "Expense", 5000.0, "Rent"),
            txn("2026-10-08", "Income", 1000.0, "Refund"),
            txn("2026-10-08", "Expense", 250.0, "Food"),
            txn("2026-10-07", "Transfer", 9000.0, from = 1, to = 3),
            txn("2026-10-07", "Adjustment", -340.0, accountId = 3),
            txn("2026-10-06", "Expense", 0.0, "Food")
        )

        assertEquals(-4250.0, Ledger.net(selected), 0.0)
        assertEquals("Delete 6 transactions (−₹4,250)?", Ledger.deleteConfirm(selected))
        assertEquals("Delete 1 transaction (₹1,000)?", Ledger.deleteConfirm(selected.subList(1, 2)))
    }

    // A mixed selection: two Expenses, an Income, a Transfer and a Balance adjustment.
    private val mixed = listOf(
        txn("2026-10-09", "Expense", 212.0, "Transport", accountId = 2, id = 101),
        txn("2026-10-09", "Expense", 4038.0, "Food", accountId = 3, id = 102),
        txn("2026-10-08", "Income", 1200.0, "Refund", accountId = 1, id = 103),
        txn("2026-10-08", "Transfer", 5000.0, from = 1, to = 3, id = 104),
        txn("2026-10-07", "Adjustment", -340.0, accountId = 3, id = 105)
    )

    @Test
    fun anExpenseCategoryChangesOnlyTheExpenses() {
        val plan = Ledger.batchPlan(mixed, BatchAction.ChangeCategory(type = "Expense", category = "Shopping"))

        assertEquals(listOf(101L, 102L), plan.changeIds)
        assertEquals(
            mapOf(103L to "An Income keeps an income Category", 104L to "A Transfer has no Category",
                105L to "A Balance adjustment has no Category"),
            plan.skipped.associate { it.id to it.reason }
        )
        assertEquals("Changed 2 Expenses · skipped 1 Income, 1 Transfer, 1 Balance adjustment", plan.summary)
    }

    @Test
    fun anIncomeCategoryChangesOnlyTheIncomes() {
        val plan = Ledger.batchPlan(mixed, BatchAction.ChangeCategory(type = "Income", category = "Salary"))

        assertEquals(listOf(103L), plan.changeIds)
        assertEquals("Changed 1 Income · skipped 2 Expenses, 1 Transfer, 1 Balance adjustment", plan.summary)
    }

    @Test
    fun theCategoryPickerSaysWhatEachKindWillChange() {
        assertEquals(
            "An expense category changes the 2 Expenses, an income category the 1 Income. " +
                "The 1 Transfer and 1 Balance adjustment have no category and stay as they are.",
            Ledger.categoryPickerNote(mixed)
        )
        assertEquals("An expense category changes the 2 Expenses.", Ledger.categoryPickerNote(mixed.take(2)))
    }

    @Test
    fun anAccountChangeSkipsTransfersWhichKeepTheirAccounts() {
        val plan = Ledger.batchPlan(mixed, BatchAction.ChangeAccount(accountId = 1))

        assertEquals(listOf(101L, 102L, 103L, 105L), plan.changeIds)
        assertEquals(mapOf(104L to "A Transfer keeps its from and to Accounts"), plan.skipped.associate { it.id to it.reason })
        assertEquals("Changed 4 transactions · skipped 1 Transfer", plan.summary)
        assertEquals("Changes 4 transactions. The 1 Transfer keeps its Accounts.", Ledger.accountPickerNote(mixed))
    }

    @Test
    fun aDateOrDescriptionChangesEveryRow() {
        val date = Ledger.batchPlan(mixed, BatchAction.ChangeDate("2026-10-01"))
        val description = Ledger.batchPlan(mixed.take(2), BatchAction.ChangeDescription("Trip"))

        assertEquals(mixed.map { it.id }, date.changeIds)
        assertEquals(emptyList<BatchSkip>(), date.skipped)
        assertEquals("Changed 5 transactions", date.summary)
        assertEquals("Changed 2 Expenses", description.summary)
    }

    @Test
    fun aChangeThatFitsNoRowSaysNothingChanged() {
        val plan = Ledger.batchPlan(mixed.drop(3), BatchAction.ChangeCategory(type = "Income", category = "Salary"))

        assertEquals(emptyList<Long>(), plan.changeIds)
        assertEquals("Nothing changed · skipped 1 Transfer, 1 Balance adjustment", plan.summary)
    }

    @Test
    fun changeDateStartsOnTheSharedDateWhenThereIsOne() {
        assertEquals(LocalDate.of(2026, 10, 9), Ledger.sharedDate(mixed.take(2)))
        assertEquals(null, Ledger.sharedDate(mixed))
    }

    // October for filtering: HDFC Savings (1), HDFC Credit card (2), Wallet (3).
    private val filterMonth = listOf(
        txn("2026-10-02", "Expense", 400.0, "Food", accountId = 2, id = 201),
        txn("2026-10-03", "Expense", 212.0, "Transport", accountId = 2, id = 202),
        txn("2026-10-04", "Expense", 1000.0, "Food", accountId = 3, id = 203),
        txn("2026-10-01", "Income", 50000.0, "Salary", accountId = 1, id = 204),
        txn("2026-10-05", "Transfer", 5000.0, from = 1, to = 3, id = 205),
        txn("2026-10-06", "Adjustment", -340.0, accountId = 3, id = 206),
        txn("2026-10-07", "Expense", 50.0, "Snacks", accountId = 3, id = 207)
    )
    private val expenseList = listOf("Food", "Transport", "Rent", "Shopping")
    private val incomeList = listOf("Salary", "Refund")

    private fun filtered(filter: LedgerFilter) =
        Ledger.build(filterMonth, accounts, october, today, filter = filter)

    private fun ids(ledger: LedgerMonth) = ledger.days.flatMap { day -> day.rows.map { it.id } }.sorted()

    @Test
    fun choicesInASectionAreOrAndSectionsAreAnd() {
        val ledger = filtered(LedgerFilter(expenseCategories = setOf("Food", "Transport"), accountIds = setOf(2)))

        assertEquals(listOf(201L, 202L), ids(ledger))
        assertEquals("₹612", ledger.summary.expenses)
        assertEquals("₹0", ledger.summary.income)
    }

    @Test
    fun aCategoryOnlyMatchesTransactionsOfItsOwnType() {
        assertEquals(emptyList<Long>(), ids(filtered(LedgerFilter(incomeCategories = setOf("Food")))))
        assertEquals(listOf(201L, 203L, 204L), ids(filtered(LedgerFilter(expenseCategories = setOf("Food"), incomeCategories = setOf("Salary")))))
    }

    @Test
    fun anAccountMatchesOnItsOwnFromOrToSide() {
        assertEquals(listOf(203L, 205L, 206L, 207L), ids(filtered(LedgerFilter(accountIds = setOf(3)))))
    }

    @Test
    fun anEmptyFilterMatchesEverythingAndTheCalendarFollowsTheFilter() {
        assertEquals(filterMonth.map { it.id }.sorted(), ids(filtered(LedgerFilter())))
        val food = filtered(LedgerFilter(expenseCategories = setOf("Food")))
        assertEquals(null, food.calendar.single { it.date == LocalDate.of(2026, 10, 3) }.expenseLabel)
        assertEquals("400", food.calendar.single { it.date == LocalDate.of(2026, 10, 2) }.expenseLabel)
    }

    @Test
    fun filterOptionsAreSortedByThisMonthsAmountWithLeftoversFlaggedAndUnusedFolded() {
        val options = Ledger.filterOptions(filterMonth, accounts, expenseList, incomeList, october, draft = LedgerFilter())

        assertEquals(listOf("Food", "Transport", "Snacks"), options.expense.used.map { it.label })
        assertEquals(listOf("₹1,400", "₹212", "₹50"), options.expense.used.map { it.amount })
        assertEquals(listOf(false, false, true), options.expense.used.map { it.isLeftover })
        assertEquals(listOf("Rent", "Shopping"), options.expense.unused.map { it.label })
        assertEquals(listOf("Salary"), options.income.used.map { it.label })
        assertEquals(1, options.income.unused.size)
        assertEquals(listOf("HDFC Savings", "Wallet", "HDFC Credit card"), options.accounts.used.map { it.label })
        assertEquals(7, options.matchCount)
    }

    @Test
    fun theSheetCountsWhatTheDraftMatchesAndTicksItsChoices() {
        val draft = LedgerFilter(expenseCategories = setOf("Food"))
        val options = Ledger.filterOptions(filterMonth, accounts, expenseList, incomeList, october, draft = draft)

        assertEquals(2, options.matchCount)
        assertEquals(listOf(true, false, false), options.expense.used.map { it.isSelected })
    }

    @Test
    fun anActiveFilterShowsOneChipPerSection() {
        val filter = LedgerFilter(expenseCategories = setOf("Food", "Transport"), accountIds = setOf(2))

        assertEquals(
            listOf(FilterChipUi(FilterSection.CATEGORIES, "Food or Transport"), FilterChipUi(FilterSection.ACCOUNTS, "HDFC Credit card")),
            Ledger.filterChips(filter, accounts)
        )
        assertEquals(emptyList<FilterChipUi>(), Ledger.filterChips(LedgerFilter(), accounts))
    }

    @Test
    fun searchResultsGroupByDateNewestFirstWithTheMonthInEachHeader() {
        val days = Ledger.searchDays(listOf(
            txn("2026-09-30", "Expense", 20.0, "Food", "Chai", id = 1),
            txn("2026-10-02", "Expense", 30.0, "Food", "Chai", id = 2),
            txn("2026-09-30", "Expense", 40.0, "Food", "Chai", id = 3)
        ), accounts)

        assertEquals(listOf("Fri, 2 Oct 2026", "Wed, 30 Sep 2026"), days.map { it.header })
        assertEquals(listOf(3L, 1L), days[1].rows.map { it.id })
    }

    @Test
    fun aResultWithNoReadableDateIsListedLastUnderNoDate() {
        val days = Ledger.searchDays(listOf(
            txn("garbled", "Expense", 20.0, "Food", "Chai", id = 1),
            txn("2026-10-02", "Expense", 30.0, "Food", "Chai", id = 2)
        ), accounts)

        assertEquals(listOf("Fri, 2 Oct 2026", "No date"), days.map { it.header })
        assertEquals(listOf(1L), days.last().rows.map { it.id })
    }

    @Test
    fun theDateRangeReadsLikeASentence() {
        assertEquals("Any time", Ledger.dateRangeLabel(null, null))
        assertEquals("1 Sep – 9 Oct 2026", Ledger.dateRangeLabel(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 9)))
        assertEquals("28 Dec 2025 – 3 Jan 2026", Ledger.dateRangeLabel(LocalDate.of(2025, 12, 28), LocalDate.of(2026, 1, 3)))
        assertEquals("9 Oct 2026", Ledger.dateRangeLabel(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 9)))
    }

    @Test
    fun matchesAreFoundWhateverTheirCase() {
        assertEquals(listOf(0..3, 13..16), Ledger.matchRanges("Uber to Mall uber", "UBER"))
        assertEquals(emptyList<IntRange>(), Ledger.matchRanges("Uber", "  "))
        assertEquals(emptyList<IntRange>(), Ledger.matchRanges("Uber", "taxi"))
    }

    @Test
    fun theLatestMonthIsTheNewestWithATransaction() {
        assertEquals(YearMonth.of(2026, 11), Ledger.latestMonth(listOf(
            txn("2026-09-30", "Expense", 1.0), txn("2026-11-02", "Income", 1.0), txn("not a date", "Expense", 1.0)
        )))
        assertEquals(null, Ledger.latestMonth(emptyList()))
    }
}
