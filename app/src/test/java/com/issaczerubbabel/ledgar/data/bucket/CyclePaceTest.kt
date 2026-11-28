package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.Fund
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import com.issaczerubbabel.ledgar.fund.FundsSnapshot
import com.issaczerubbabel.ledgar.util.RecurrenceFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The one Daily pace: Left to spend less the Repeats still due, over the days left (#135). */
class CyclePaceTest {

    // The issue's example: a ₹60,000 Salary cycle from 1 to 30 Nov, looked at on 10 Nov.
    private val cycle = BudgetCycle(id = 1, startDate = "2026-11-01", endDate = "2026-11-30", spendableAmount = 60_000.0)
    private val today = LocalDate.of(2026, 11, 10)
    private val spent = listOf(expense("2026-11-03", "Food", 20_000.0))

    private fun rule(
        id: Long = 1,
        nextDate: String = "2026-11-25",
        amount: Double = 15_000.0,
        type: String = "Expense",
        frequency: String = RecurrenceFrequency.MONTHLY,
        anchorDay: Int = LocalDate.parse(nextDate).let { if (frequency == RecurrenceFrequency.WEEKLY) it.dayOfWeek.value else it.dayOfMonth },
        autoAdd: Boolean = true,
        isPaused: Boolean = false,
        endDate: String? = null,
        remainingCount: Int? = null,
        accountId: Long? = 1
    ) = RecurringRule(
        id = id, type = type, category = "Rent", description = "Rent", amount = amount, accountId = accountId,
        remarks = "", frequency = frequency, anchorDay = anchorDay, startDate = "2026-01-01", nextDate = nextDate,
        endDate = endDate, remainingCount = remainingCount, autoAdd = autoAdd, isPaused = isPaused,
        createdAt = "2026-01-01"
    )

    private fun expense(date: String, category: String, amount: Double, account: Long = 1) =
        ExpenseRecord(date = date, type = "Expense", category = category, description = "", amount = amount, accountId = account, remarks = "")

    private fun pace(
        rules: List<RecurringRule>,
        records: List<ExpenseRecord> = spent,
        cycle: BudgetCycle = this.cycle,
        today: LocalDate = this.today,
        funds: FundsSnapshot = FundsSnapshot.EMPTY
    ): CyclePace {
        val summary = CycleSummaryBuilder.build(cycle, emptyList(), emptyList(), records, today, funds)
        return CyclePace.of(summary, rules, today, funds)
    }

    @Test
    fun rentDueLaterInTheCycleComesOffThePaceButNotLeftToSpend() {
        val pace = pace(listOf(rule()))

        assertEquals(40_000.0, pace.leftToSpend, 0.0)
        assertEquals(15_000.0, pace.stillDueTotal, 0.0)
        assertEquals(listOf(LocalDate.of(2026, 11, 25)), pace.stillDue.map { it.date })
        // (₹40,000 − ₹15,000) over the 20 days left after the 10th.
        assertEquals(1_250.0, pace.dailyPace, 0.0001)
        assertTrue(pace.isAfterRepeats)
        assertFalse(pace.stillDueExceedsLeft)
    }

    @Test
    fun withNoRepeatRulesThePaceIsLeftToSpendOverTheDaysLeft() {
        val pace = pace(emptyList())

        assertTrue(pace.stillDue.isEmpty())
        assertFalse(pace.isAfterRepeats)
        assertEquals(2_000.0, pace.dailyPace, 0.0001)
    }

    @Test
    fun aWeeklyRepeatCountsEveryDateLeftInTheCycle() {
        // Fridays: 13, 20 and 27 Nov are still to come; 4 Dec is next cycle's.
        val weekly = rule(nextDate = "2026-11-13", amount = 500.0, frequency = RecurrenceFrequency.WEEKLY)

        val pace = pace(listOf(weekly))

        assertEquals(listOf(13, 20, 27), pace.stillDue.map { it.date.dayOfMonth })
        assertEquals(1_500.0, pace.stillDueTotal, 0.0)
        assertEquals((40_000.0 - 1_500.0) / 20, pace.dailyPace, 0.0001)
    }

    @Test
    fun pausedIncomeTransferAndLaterRepeatsAreLeftOut() {
        val rules = listOf(
            rule(id = 1, isPaused = true),
            rule(id = 2, type = "Income"),
            rule(id = 3, type = "Transfer", accountId = null),
            rule(id = 4, nextDate = "2026-12-01"),
            rule(id = 5, nextDate = "2026-11-20", amount = 999.0)
        )

        val pace = pace(rules)

        assertEquals(listOf(5L), pace.stillDue.map { it.rule.id })
        assertEquals(999.0, pace.stillDueTotal, 0.0)
    }

    @Test
    fun aRuleEndingMidCycleStopsAtItsEndDateOrCount() {
        val endsOnDate = rule(id = 1, nextDate = "2026-11-13", amount = 500.0, frequency = RecurrenceFrequency.WEEKLY, endDate = "2026-11-21")
        val twoLeft = rule(id = 2, nextDate = "2026-11-12", amount = 100.0, frequency = RecurrenceFrequency.WEEKLY, remainingCount = 2)
        val finished = rule(id = 3, nextDate = "2026-11-20", remainingCount = 0)

        val due = pace(listOf(endsOnDate, twoLeft, finished)).stillDue

        assertEquals(listOf(13, 20), due.filter { it.rule.id == 1L }.map { it.date.dayOfMonth })
        assertEquals(listOf(12, 19), due.filter { it.rule.id == 2L }.map { it.date.dayOfMonth })
        assertTrue(due.none { it.rule.id == 3L })
    }

    @Test
    fun onceAddedTheOccurrenceMovesFromStillDueIntoSpentWithoutCountingTwice() {
        val onThe25th = LocalDate.of(2026, 11, 25)
        val before = pace(listOf(rule()), today = onThe25th)
        // Adding it writes a Transaction on its date and moves the rule to next month.
        val after = pace(
            listOf(rule(nextDate = "2026-12-25")),
            records = spent + expense("2026-11-25", "Rent", 15_000.0),
            today = onThe25th
        )

        assertEquals(15_000.0, before.stillDueTotal, 0.0)
        assertTrue(after.stillDue.isEmpty())
        assertEquals(before.leftToSpend - 15_000.0, after.leftToSpend, 0.0)
        assertEquals(before.dailyPace, after.dailyPace, 0.0001)
    }

    @Test
    fun anAskMeFirstOccurrenceWaitingCountsAndIsTagged() {
        val waiting = rule(id = 1, nextDate = "2026-11-08", amount = 700.0, autoAdd = false)
        val later = rule(id = 2, nextDate = "2026-11-20", amount = 300.0, autoAdd = false)

        val due = pace(listOf(waiting, later)).stillDue

        assertEquals(listOf(1L, 2L), due.map { it.rule.id })
        assertTrue(due[0].isWaiting)
        assertTrue(due[0].isNextForRule)
        assertFalse(due[1].isWaiting)
        assertEquals(1_000.0, due.sumOf { it.amount }, 0.0)
    }

    @Test
    fun anAutomaticOccurrenceNotAddedYetCountsUntilTheWorkerAddsIt() {
        val dueToday = rule(nextDate = "2026-11-10", amount = 400.0)

        val due = pace(listOf(dueToday)).stillDue.single()

        assertEquals(today, due.date)
        assertFalse(due.isWaiting)
    }

    @Test
    fun anOccurrenceFromBeforeTheCycleBelongsToTheCycleBefore() {
        // Waiting since 28 Oct: adding it lands in October's cycle. 28 Nov is this one's.
        val waitingSinceOctober = rule(nextDate = "2026-10-28", amount = 800.0, autoAdd = false)

        val due = pace(listOf(waitingSinceOctober)).stillDue

        assertEquals(listOf(LocalDate.of(2026, 11, 28)), due.map { it.date })
        assertFalse(due.single().isWaiting)
        assertFalse(due.single().isNextForRule)
    }

    @Test
    fun aRuleFarBehindStillReachesTheCycle() {
        // A weekly rule two years behind, past the 24-at-a-time catch-up cap. 6 Nov isn't added yet either.
        val farBehind = rule(nextDate = "2024-11-15", amount = 100.0, frequency = RecurrenceFrequency.WEEKLY)

        assertEquals(listOf(6, 13, 20, 27), pace(listOf(farBehind)).stillDue.map { it.date.dayOfMonth })
    }

    @Test
    fun anOverdueCycleHasNothingStillDueAndNoPace() {
        val pace = pace(listOf(rule(nextDate = "2026-11-28", autoAdd = false)), today = LocalDate.of(2026, 12, 2))

        assertTrue(pace.stillDue.isEmpty())
        assertEquals(0.0, pace.dailyPace, 0.0)
    }

    @Test
    fun aClosedCycleHasNothingStillDue() {
        val closed = cycle.copy(closedAt = "2026-11-30")

        assertTrue(CyclePace.stillDue(closed, listOf(rule()), today).isEmpty())
    }

    @Test
    fun thePaceStopsAtZeroWhenRepeatsTakeAllThatIsLeft() {
        val pace = pace(listOf(rule(amount = 45_000.0)))

        assertEquals(0.0, pace.dailyPace, 0.0)
        assertTrue(pace.stillDueExceedsLeft)
    }

    @Test
    fun onTheLastDayWhatIsLeftAfterRepeatsIsTodaysPace() {
        val pace = pace(listOf(rule(nextDate = "2026-11-30", amount = 5_000.0)), today = LocalDate.of(2026, 11, 30))

        assertEquals(35_000.0, pace.dailyPace, 0.0001)
    }

    @Test
    fun aMonthEndRepeatLandsOnTheCyclesLastDay() {
        val december = BudgetCycle(id = 2, startDate = "2026-12-01", endDate = "2026-12-31", spendableAmount = 60_000.0)
        val february = BudgetCycle(id = 3, startDate = "2027-02-01", endDate = "2027-02-28", spendableAmount = 60_000.0)
        val monthEnd = rule(nextDate = "2026-12-31", anchorDay = 31)

        val inDecember = CyclePace.stillDue(december, listOf(monthEnd), LocalDate.of(2026, 12, 10))
        val inFebruary = CyclePace.stillDue(february, listOf(monthEnd.copy(nextDate = "2027-02-28")), LocalDate.of(2027, 2, 10))

        assertEquals(listOf(LocalDate.of(2026, 12, 31)), inDecember.map { it.date })
        assertEquals(listOf(LocalDate.of(2027, 2, 28)), inFebruary.map { it.date })
    }

    @Test
    fun anExpenseRepeatPaidFromAFundsAccountComesOutOfTheFund() {
        val giving = Fund(id = 10, name = "Ministry", createdDate = "2026-09-01")
        val funds = FundsSnapshot(
            funds = listOf(giving),
            holdings = listOf(FundHolding(id = 1, fundId = 10, accountId = 3, kind = HoldingKind.WHOLE.name, startDate = "2026-09-01"))
        )
        val fromFund = rule(id = 1, nextDate = "2026-11-20", amount = 2_000.0, accountId = 3)
        val fromSalary = rule(id = 2, nextDate = "2026-11-20", amount = 1_000.0, accountId = 1)

        val pace = pace(listOf(fromFund, fromSalary), funds = funds)

        assertEquals(listOf(2L), pace.stillDue.map { it.rule.id })
        assertEquals(1_000.0, pace.stillDueTotal, 0.0)
    }
}
