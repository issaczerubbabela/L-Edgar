package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.DropdownRole
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StatsReportTest {

    private val september = StatsPeriod.Month(YearMonth.of(2026, 9))
    private val afterSeptember = LocalDate.of(2026, 10, 5)

    private val accounts = listOf(
        account(1, "Cash", "Wallet"),
        account(2, "Card", "ICICI Credit Card"),
        account(3, "Debit Card", "HDFC Debit"),
        account(4, "Accounts", "HDFC Savings"),
        account(5, "Investments", "Mutual funds"),
        account(6, "Savings", "Fixed deposit")
    )

    private val roles = StatsRoles.from(
        listOf(
            option("EXPENSE_CATEGORY", "Investments/Savings", DropdownRole.SAVING),
            option("INCOME_CATEGORY", "Return", DropdownRole.REFUND),
            option("ACCOUNT_GROUP", "Investments", DropdownRole.SAVINGS),
            option("ACCOUNT_GROUP", "Savings", DropdownRole.SAVINGS),
            option("EXPENSE_CATEGORY", "Food", "")
        )
    )

    private fun build(
        records: List<ExpenseRecord>,
        period: StatsPeriod = september,
        today: LocalDate = afterSeptember,
        cycles: List<BudgetCycle> = emptyList()
    ) = StatsReport.build(StatsInput(records, accounts, roles, cycles, period, today))

    // ---- comparison ----------------------------------------------------------------------------

    @Test
    fun rentOnTheFirstOfBothMonthsIsNoChange() {
        // The +58% bug: September used to be compared with Aug 2-31, which skipped August's rent.
        val result = build(listOf(expense("2026-08-01", 18000.0), expense("2026-09-01", 18000.0)))

        assertEquals(0, result.spentChangePercent)
    }

    @Test
    fun aMonthStillRunningIsComparedWithTheSameDaysOfTheLastMonth() {
        val result = build(
            listOf(expense("2026-08-10", 100.0), expense("2026-08-28", 900.0), expense("2026-09-10", 150.0)),
            today = LocalDate.of(2026, 9, 25)
        )

        assertEquals(50, result.spentChangePercent) // 150 against Aug 1-25's 100, not the whole of August
    }

    @Test
    fun theLastDayOfALongMonthIsComparedWithTheEndOfAShortOne() {
        val range = StatsReport.comparisonRange(StatsPeriod.Month(YearMonth.of(2026, 3)), LocalDate.of(2026, 3, 30))

        assertEquals(LocalDate.of(2026, 2, 1), range.start)
        assertEquals(LocalDate.of(2026, 2, 28), range.endInclusive)
    }

    @Test
    fun aFallInSpendingIsNegative() {
        assertEquals(-25, build(listOf(expense("2026-08-15", 200.0), expense("2026-09-15", 150.0))).spentChangePercent)
    }

    @Test
    fun noEarlierSpendingMeansNoComparison() {
        assertNull(build(listOf(expense("2026-09-15", 150.0))).spentChangePercent)
    }

    // ---- money rules (ADR-0004) ------------------------------------------------------------------

    @Test
    fun savingCategoriesAreSavedNotSpent() {
        val totals = build(listOf(expense("2026-09-10", 10000.0, "Investments/Savings"), expense("2026-09-11", 500.0))).totals

        assertEquals(500.0, totals.spent, 0.0)
        assertEquals(10000.0, totals.saved, 0.0)
    }

    @Test
    fun refundsReduceSpendingAndAreNotEarnings() {
        val totals = build(
            listOf(income("2026-09-25", 68000.0, "Salary"), income("2026-09-09", 1499.0, " return "), expense("2026-09-02", 5000.0))
        ).totals

        assertEquals(68000.0, totals.earned, 0.0)
        assertEquals(1499.0, totals.refunds, 0.0)
        assertEquals(3501.0, totals.spent, 0.0)
    }

    @Test
    fun leftOverIsEarnedMinusSpentMinusSaved() {
        val totals = build(
            listOf(
                income("2026-09-25", 68000.0, "Salary"),
                expense("2026-09-01", 40000.0),
                expense("2026-09-10", 10000.0, "Investments/Savings")
            )
        ).totals

        assertEquals(18000.0, totals.leftOver, 0.0)
    }

    @Test
    fun transfersIntoASavingsGroupAreSavedAndOutOfOneAreTakenBack() {
        val totals = build(
            listOf(
                transfer("2026-09-05", 5000.0, from = 4, to = 5),  // bank -> mutual funds
                transfer("2026-09-06", 2000.0, from = 6, to = 4),  // fixed deposit -> bank
                transfer("2026-09-07", 700.0, from = 5, to = 6),   // between two savings groups
                transfer("2026-09-18", 9000.0, from = 4, to = 2)   // paying the card bill
            )
        ).totals

        assertEquals(3000.0, totals.saved, 0.0)
        assertEquals(0.0, totals.spent, 0.0)
    }

    @Test
    fun aTransferWithoutAccountIdsIsMatchedByAccountName() {
        val record = ExpenseRecord(
            date = "2026-09-05", type = "Transfer", category = "", description = "", amount = 1200.0, remarks = "",
            fromAccountName = "HDFC Savings", toAccountName = "mutual funds"
        )

        assertEquals(1200.0, build(listOf(record)).totals.saved, 0.0)
    }

    @Test
    fun categoryListsLeaveOutSavingAndRefundCategories() {
        val result = build(
            listOf(
                expense("2026-09-01", 300.0, "Food"),
                expense("2026-09-02", 900.0, "Rent"),
                expense("2026-09-03", 10000.0, "Investments/Savings"),
                income("2026-09-04", 68000.0, "Salary"),
                income("2026-09-05", 200.0, "Return")
            )
        )

        assertEquals(listOf("Rent", "Food"), result.categories.map { it.category })
    }

    // ---- paid from -------------------------------------------------------------------------------

    @Test
    fun anyAccountGroupNamedCardCountsAsCard() {
        val paid = build(
            listOf(
                expense("2026-09-05", 500.0, account = 2),
                expense("2026-09-06", 300.0, account = 3),
                expense("2026-09-07", 100.0, account = 1),
                expense("2026-09-08", 400.0, account = 99),
                expense("2026-09-09", 800.0, account = null)
            )
        ).paidFrom

        assertEquals(800.0, paid.card, 0.0)
        assertEquals(1300.0, paid.cashAndAccounts, 0.0)
    }

    @Test
    fun transfersAreCountedButNeverAsSpending() {
        val result = build(listOf(transfer("2026-09-01", 1000.0, from = 4, to = 2), expense("2026-09-02", 50.0)))

        assertEquals(1000.0, result.paidFrom.transfers, 0.0)
        assertEquals(50.0, result.totals.spent, 0.0)
    }

    // ---- timeline --------------------------------------------------------------------------------

    @Test
    fun aMonthHasOnePointPerDayIncludingDaysWithNoSpending() {
        val timeline = build(listOf(expense("2026-09-03", 100.0), expense("2026-09-05", 40.0))).timeline

        assertEquals(30, timeline.size)
        assertEquals("4", timeline[3].label)
        assertEquals(0.0, timeline[3].spent, 0.0)
        assertEquals(40.0, timeline[4].spent, 0.0)
    }

    @Test
    fun aYearHasTwelveMonthlyPoints() {
        val timeline = build(listOf(expense("2026-03-15", 100.0)), period = StatsPeriod.Year(2026), today = LocalDate.of(2027, 1, 2)).timeline

        assertEquals(12, timeline.size)
        assertEquals("Mar", timeline[2].label)
        assertEquals(100.0, timeline[2].spent, 0.0)
    }

    @Test
    fun aTwoMonthRangeIsWeeklyAndAHalfYearOrMoreIsMonthly() {
        val nineWeeks = StatsPeriod.Custom(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31))
        val tenMonths = StatsPeriod.Custom(LocalDate.of(2025, 11, 1), LocalDate.of(2026, 8, 31))

        assertEquals(StatsResolution.WEEK, nineWeeks.resolution)
        assertEquals(LocalDate.of(2026, 7, 1), build(emptyList(), period = nineWeeks).timeline.first().start)
        assertEquals(StatsResolution.MONTH, tenMonths.resolution)
        assertEquals(10, build(emptyList(), period = tenMonths).timeline.size)
    }

    @Test
    fun refundsComeOffTheDayTheyArrive() {
        val timeline = build(listOf(expense("2026-09-09", 2000.0), income("2026-09-09", 500.0, "Return"))).timeline

        assertEquals(1500.0, timeline[8].spent, 0.0)
        assertEquals(0.0, timeline[8].earned, 0.0)
    }

    // ---- pace ------------------------------------------------------------------------------------

    @Test
    fun paceRunsUpToTodayAndComparesTheSameDayLastPeriod() {
        val pace = build(
            listOf(expense("2026-08-01", 1000.0), expense("2026-08-20", 500.0), expense("2026-09-01", 1000.0), expense("2026-09-03", 200.0)),
            today = LocalDate.of(2026, 9, 10)
        ).pace

        assertEquals(10, pace.current.size)
        assertEquals(1200.0, pace.spentSoFar, 0.0)
        assertEquals(31, pace.previous.size)
        assertEquals(1000.0, pace.previousAtSamePoint!!, 0.0) // August by the 10th, before the 20th's 500
    }

    @Test
    fun theProjectionIgnoresTheBiggestDay() {
        val records = listOf(expense("2026-09-01", 18000.0)) + (2..10).map { expense("2026-09-%02d".format(it), 100.0) }
        val pace = build(records, today = LocalDate.of(2026, 9, 10)).pace

        // 18,900 so far; a typical day is 100 (rent left out), and 20 days remain.
        assertEquals(20900.0, pace.projection!!, 0.001)
    }

    @Test
    fun aFinishedPeriodHasNoProjection() {
        assertNull(build(listOf(expense("2026-09-01", 100.0))).pace.projection)
    }

    // ---- categories, calendar, trend, biggest -----------------------------------------------------

    @Test
    fun aCategorysUsualAmountIsTheAverageOfTheThreePeriodsBefore() {
        val result = build(
            listOf(
                expense("2026-06-05", 300.0, "Food"),
                expense("2026-07-05", 600.0, "Food"),
                expense("2026-09-05", 450.0, "Food"),
                expense("2026-09-06", 50.0, "Gifts")
            )
        )

        assertEquals(300.0, result.categories.first { it.category == "Food" }.usual!!, 0.001) // (300 + 600 + 0) / 3
        assertNull(result.categories.first { it.category == "Gifts" }.usual)
    }

    @Test
    fun calendarShadesByRankSoOneBigBillDoesNotWashOutTheRest() {
        val days = build(
            listOf(expense("2026-09-01", 18000.0), expense("2026-09-02", 100.0), expense("2026-09-03", 200.0)),
            today = LocalDate.of(2026, 9, 3)
        ).days

        assertEquals(30, days.size)
        assertEquals(5, days[0].step)
        assertTrue(days[1].step in 1..2)
        assertEquals(0, days[3].step)
        assertTrue(days[3].isFuture)
    }

    @Test
    fun aYearHasNoCalendar() {
        assertTrue(build(emptyList(), period = StatsPeriod.Year(2026)).days.isEmpty())
    }

    @Test
    fun theTrendShowsThisMonthAndTheSevenBefore() {
        val trend = build(listOf(expense("2026-02-10", 700.0), income("2026-09-25", 68000.0, "Salary"))).trend

        assertEquals(listOf("Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep"), trend.map { it.label })
        assertEquals(700.0, trend.first().spent, 0.0)
        assertTrue(trend.last().isCurrent)
        assertEquals(68000.0, trend.last().leftOver, 0.0)
    }

    @Test
    fun biggestExpensesLeaveOutSavings() {
        val biggest = build(
            listOf(expense("2026-09-10", 10000.0, "Investments/Savings"), expense("2026-09-01", 18000.0, "Rent"), expense("2026-09-02", 90.0))
        ).biggest

        assertEquals(listOf(18000.0, 90.0), biggest.map { it.amount })
    }

    // ---- cycle view ------------------------------------------------------------------------------

    @Test
    fun cycleViewCountsEverythingAgainstTheSpendableAmountLikeTheBudgetTab() {
        val cycle = cycle("2026-08-25", "2026-09-24", 43000.0, closedAt = "2026-09-25").copy(id = 7)
        val today = LocalDate.of(2026, 9, 26)
        val period = StatsPeriod.cycleOn(LocalDate.of(2026, 9, 1), listOf(cycle), today)!!
        val result = build(
            listOf(expense("2026-09-01", 18000.0, "Rent"), expense("2026-09-10", 10000.0, "Investments/Savings"), income("2026-09-09", 500.0, "Return")),
            period = period, cycles = listOf(cycle), today = today
        )

        val view = result.cycle!!
        assertEquals(15000.0, view.summary.leftToSpend, 0.0)
        assertEquals(10000.0, view.saved, 0.0)
        assertFalse(view.isRunning)
        assertEquals(28000.0, result.totals.spent, 0.0) // saving and no refund taken off, as in Budget
    }

    @Test
    fun cyclesStepThroughEachOtherAndPastTheEndsByTheirLength() {
        val cycles = listOf(
            cycle("2026-07-25", "2026-08-24", 1.0, closedAt = "2026-08-25").copy(id = 1),
            cycle("2026-08-25", "2026-09-24", 1.0).copy(id = 2)
        )
        val today = LocalDate.of(2026, 9, 30)
        val running = StatsPeriod.cycleOn(today, cycles, today)!!

        assertEquals(LocalDate.of(2026, 9, 30), running.end) // stays open past its end date
        assertEquals(1L, (running.previous() as StatsPeriod.Cycle).span.id)
        assertFalse((running.previous() as StatsPeriod.Cycle).hasPrevious)
        assertTrue(running.previous().previous() is StatsPeriod.Custom)
    }

    @Test
    fun aFirstCycleHasNothingToCompareWithAndNoMadeUpHistory() {
        val cycle = cycle("2026-09-25", "2026-10-24", 68000.0).copy(id = 1)
        val today = LocalDate.of(2026, 10, 5)
        val period = StatsPeriod.cycleOn(today, listOf(cycle), today)!!
        val result = build(
            listOf(expense("2026-09-01", 5000.0), expense("2026-09-26", 700.0)),
            period = period, cycles = listOf(cycle), today = today
        )

        assertNull(result.spentChangePercent)
        assertTrue(result.pace.previous.isEmpty())
        assertNull(result.pace.previousAtSamePoint)
        assertEquals(1, result.trend.size)
    }

    @Test
    fun aPeriodTwoDaysInIsNotComparedYet() {
        val result = build(
            listOf(expense("2026-08-01", 100.0), expense("2026-09-01", 300.0)),
            today = LocalDate.of(2026, 9, 2)
        )

        assertNull(result.spentChangePercent)
        assertNull(result.previousLeftOver)
        assertNull(result.pace.previousAtSamePoint)
        assertEquals(31, result.pace.previous.size) // last month's line is still drawn
    }

    @Test
    fun noCyclesMeansNoCyclePeriod() {
        assertNull(StatsPeriod.cycleOn(LocalDate.of(2026, 9, 1), emptyList(), LocalDate.of(2026, 9, 1)))
    }

    // ---- category detail -------------------------------------------------------------------------

    @Test
    fun categoryDetailOnlyCountsThoseCategories() {
        val records = listOf(
            expense("2026-09-02", 300.0, "Food"), expense("2026-09-04", 200.0, "food "),
            expense("2026-09-03", 900.0, "Rent"), expense("2026-08-10", 400.0, "Food")
        )
        val detail = StatsReport.categoryDetail(
            StatsInput(records, accounts, roles, emptyList(), september, afterSeptember), "Food", setOf("Food")
        )

        assertEquals(500.0, detail.spent, 0.0)
        assertEquals(400.0 / 3, detail.usual!!, 0.001) // Jun, Jul, Aug: only August had Food
        assertEquals(listOf(200.0, 300.0), detail.transactions.map { it.amount })
        assertEquals(8, detail.trend.size)
    }

    // ---- budget line -----------------------------------------------------------------------------

    @Test
    fun eachDayGetsItsOwnCyclesShare() {
        val cycles = listOf(
            cycle("2026-08-25", "2026-09-24", 3100.0, closedAt = "2026-09-25"), // 31 days, 100 a day
            cycle("2026-09-25", "2026-10-24", 6000.0)                            // 30 days, 200 a day
        )
        val timeline = build(emptyList(), cycles = cycles, today = LocalDate.of(2026, 9, 26)).timeline

        assertEquals(100.0, timeline[23].budget!!, 0.001) // 24 Sep
        assertEquals(200.0, timeline[24].budget!!, 0.001) // 25 Sep
    }

    @Test
    fun aYearPointAddsUpItsDaysAndDaysWithNoCycleAddNothing() {
        val cycles = listOf(cycle("2026-03-10", "2026-04-08", 3000.0, closedAt = "2026-04-09")) // 30 days, 100 a day
        val result = build(emptyList(), period = StatsPeriod.Year(2026), cycles = cycles, today = LocalDate.of(2027, 1, 1))

        assertNull(result.timeline[1].budget)                       // February
        assertEquals(2200.0, result.timeline[2].budget!!, 0.001)   // 10-31 March
        assertEquals(800.0, result.timeline[3].budget!!, 0.001)    // 1-8 April
        assertTrue(result.hasBudget)
    }

    @Test
    fun aRunningCycleStaysOpenPastItsEndDate() {
        val cycles = listOf(cycle("2026-08-25", "2026-09-24", 3100.0))
        val timeline = build(emptyList(), cycles = cycles, today = LocalDate.of(2026, 9, 27)).timeline

        assertEquals(100.0, timeline[26].budget!!, 0.001) // 27 Sep, still inside the running cycle
        assertNull(timeline[27].budget)                  // 28 Sep is after today
    }

    @Test
    fun theRunningBudgetStartsWhereTheFirstCycleDoes() {
        val cycles = listOf(cycle("2026-09-25", "2026-10-24", 3000.0)) // 100 a day
        val budget = build(emptyList(), cycles = cycles, today = LocalDate.of(2026, 9, 30)).pace.budget

        assertNull(budget[23])                  // 24 Sep, before the cycle
        assertEquals(100.0, budget[24]!!, 0.001) // 25 Sep
        assertEquals(600.0, budget[29]!!, 0.001) // 30 Sep
    }

    @Test
    fun noCyclesMeansNoBudgetLine() {
        assertFalse(build(listOf(expense("2026-09-01", 10.0))).hasBudget)
    }

    // ---- periods ---------------------------------------------------------------------------------

    @Test
    fun periodsStepByTheirOwnLength() {
        assertEquals(StatsPeriod.Month(YearMonth.of(2026, 8)), september.previous())
        assertEquals(StatsPeriod.Week(LocalDate.of(2026, 9, 14)), StatsPeriod.of(StatsScope.WEEKLY, LocalDate.of(2026, 9, 25)).previous())
        assertEquals(
            StatsPeriod.Custom(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 20)),
            StatsPeriod.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10)).next()
        )
    }

    @Test
    fun onlyTheSelectedPeriodIsCounted() {
        val result = build(listOf(expense("2026-08-31", 999.0), expense("2026-09-01", 10.0), expense("2026-10-01", 999.0)))

        assertEquals(10.0, result.totals.spent, 0.0)
        assertTrue(result.hasTransactions)
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private fun expense(date: String, amount: Double, category: String = "Food", account: Long? = 4L) =
        ExpenseRecord(date = date, type = "Expense", category = category, description = "", amount = amount, accountId = account, remarks = "")

    private fun income(date: String, amount: Double, category: String) =
        ExpenseRecord(date = date, type = "Income", category = category, description = "", amount = amount, accountId = 4L, remarks = "")

    private fun transfer(date: String, amount: Double, from: Long, to: Long) =
        ExpenseRecord(date = date, type = "Transfer", category = "", description = "", amount = amount, remarks = "", fromAccountId = from, toAccountId = to)

    private fun account(id: Long, group: String, name: String) =
        AccountRecord(id = id, groupName = group, accountName = name, initialBalance = 0.0, initialBalanceDate = "2026-01-01", isHidden = false, displayOrder = 0)

    private fun option(type: String, name: String, role: String) = DropdownOption(optionType = type, name = name, displayOrder = 0, role = role)

    private fun cycle(start: String, end: String, spendable: Double, closedAt: String? = null) =
        BudgetCycle(startDate = start, endDate = end, spendableAmount = spendable, closedAt = closedAt)
}
