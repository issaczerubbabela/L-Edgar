package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.Fund
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.FundPlan
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import com.issaczerubbabel.ledgar.fund.FundsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * #156: a cycle planned before Funds, with spendable entered net of its RD instalment. The issue's
 * example: salary ₹60,000, ₹10,000 a month into an RD, spendable ₹50,000, ₹15,000 spent.
 */
class SpendableTopUpTest {

    private val today = LocalDate.of(2026, 11, 28)
    private val cycle = BudgetCycle(id = 7, startDate = "2026-11-01", endDate = "2026-11-30", spendableAmount = 50_000.0)

    /** The day the phone first had a Fund holding a whole Account: mid-cycle. */
    private val fundsSince = LocalDate.of(2026, 11, 28)

    private val rd = Fund(id = 10, name = "Emergency", createdDate = "2026-11-28", sortOrder = 0)
    private val sip = Fund(id = 11, name = "Retirement", createdDate = "2026-11-28", sortOrder = 1)
    private val rdHolding = FundHolding(id = 1, fundId = 10, accountId = 2, kind = HoldingKind.WHOLE.name, startDate = "2026-11-01")
    private val sipHolding = FundHolding(id = 2, fundId = 11, accountId = 3, kind = HoldingKind.WHOLE.name, startDate = "2026-11-01")

    private val funds = FundsSnapshot(funds = listOf(rd, sip), holdings = listOf(rdHolding, sipHolding))

    private val records = listOf(
        ExpenseRecord(date = "2026-11-05", type = "Expense", category = "Rent", description = "", amount = 15_000.0, accountId = 1, remarks = ""),
        // Salary Account -> RD: Set aside once a Fund holds the RD whole.
        ExpenseRecord(date = "2026-11-02", type = "Transfer", category = "Transfer", description = "", amount = 10_000.0, fromAccountId = 1, toAccountId = 2, remarks = "")
    )

    private fun summary(cycle: BudgetCycle = this.cycle, funds: FundsSnapshot = this.funds, records: List<ExpenseRecord> = this.records) =
        CycleSummaryBuilder.build(cycle, emptyList(), emptyList(), records, today, funds)

    @Test
    fun aCyclePlannedBeforeFundsIsOfferedItsSetAsideBack() {
        val summary = summary()
        assertEquals(25_000.0, summary.leftToSpend, 0.0) // the ₹10,000 counted twice

        assertEquals(10_000.0, SpendableTopUp.suggest(summary, fundsSince, emptySet())!!, 0.0)
    }

    @Test
    fun raisingByTheSuggestionLiftsLeftToSpendAndDailyPaceByTheSameAmount() {
        val before = summary()
        val topUp = SpendableTopUp.suggest(before, fundsSince, emptySet())!!
        val after = summary(cycle = cycle.copy(spendableAmount = cycle.spendableAmount + topUp))

        assertEquals(35_000.0, after.leftToSpend, 0.0) // what v1.7.0 showed
        assertEquals(before.leftToSpend + topUp, after.leftToSpend, 0.0)
        assertEquals(before.dailyPace + topUp / before.daysLeft, after.dailyPace, 0.001)
    }

    @Test
    fun plannedContributionsStillToTransferAreAddedToWhatIsSetAside() {
        val withPlans = funds.copy(
            plans = listOf(
                FundPlan(fundId = 10, cycleId = 7, plannedAmount = 8_000.0), // already over: ₹10,000 Set aside
                FundPlan(fundId = 11, cycleId = 7, plannedAmount = 5_000.0), // nothing transferred yet
                FundPlan(fundId = 11, cycleId = 99, plannedAmount = 1_000.0) // another cycle's plan
            )
        )

        assertEquals(15_000.0, SpendableTopUp.suggest(summary(funds = withPlans), fundsSince, emptySet())!!, 0.0)
    }

    @Test
    fun aPlannedContributionAloneIsEnoughForTheCard() {
        val planOnly = funds.copy(plans = listOf(FundPlan(fundId = 11, cycleId = 7, plannedAmount = 5_000.0)))
        val summary = summary(funds = planOnly, records = records.filter { it.type == "Expense" })

        assertEquals(5_000.0, SpendableTopUp.suggest(summary, fundsSince, emptySet())!!, 0.0)
    }

    @Test
    fun nothingSetAsideAndNoPlannedContributionsShowsNoCard() {
        val summary = summary(records = records.filter { it.type == "Expense" })

        assertNull(SpendableTopUp.suggest(summary, fundsSince, emptySet()))
    }

    @Test
    fun aCycleStartedOnOrAfterTheFirstFundNeverShowsTheCard() {
        val startedThatDay = cycle.copy(startDate = "2026-11-28", endDate = "2026-12-27")
        val startedAfter = cycle.copy(startDate = "2026-11-29", endDate = "2026-12-28")
        val fundsBefore = LocalDate.of(2026, 10, 15)

        assertNull(SpendableTopUp.suggest(summary(), fundsBefore, emptySet()))
        assertNull(SpendableTopUp.suggest(summary(cycle = startedThatDay), fundsSince, emptySet()))
        assertNull(SpendableTopUp.suggest(summary(cycle = startedAfter), fundsSince, emptySet()))
    }

    @Test
    fun noWholeAccountFundYetShowsNoCard() {
        assertNull(SpendableTopUp.suggest(summary(), fundsSince = null, answeredCycleIds = emptySet()))
    }

    @Test
    fun raiseOrKeepHidesTheCardForThatCycleOnly() {
        assertNull(SpendableTopUp.suggest(summary(), fundsSince, setOf(7L)))
        assertEquals(10_000.0, SpendableTopUp.suggest(summary(), fundsSince, setOf(6L))!!, 0.0)
    }

    @Test
    fun aClosedCycleNeverShowsTheCard() {
        val closed = cycle.copy(closedAt = "2026-11-28")

        assertNull(SpendableTopUp.suggest(summary(cycle = closed), fundsSince, emptySet()))
    }

    @Test
    fun fundsSinceIsWhenTheFirstWholeHoldingCouldHaveExisted() {
        // Backdated to the cycle start so the earlier instalment counts, but the Fund was made today.
        assertEquals(LocalDate.of(2026, 11, 28), SpendableTopUp.fundsSince(funds, today))

        // A whole holding added later to an older Fund counts from its own start.
        val older = FundsSnapshot(
            funds = listOf(rd.copy(createdDate = "2026-09-01"), sip.copy(createdDate = "2026-11-20")),
            holdings = listOf(rdHolding.copy(startDate = "2026-11-10"), sipHolding.copy(startDate = "2026-11-01"))
        )
        assertEquals(LocalDate.of(2026, 11, 10), SpendableTopUp.fundsSince(older, today))
    }

    @Test
    fun fundsSinceIgnoresFixedHoldingsAndArchivedFunds() {
        val fixedOnly = FundsSnapshot(
            funds = listOf(rd),
            holdings = listOf(FundHolding(id = 3, fundId = 10, accountId = 1, kind = HoldingKind.FIXED.name, amount = 5_000.0, startDate = "2026-11-01"))
        )
        val archived = funds.copy(funds = listOf(rd.copy(archivedAt = "2026-11-28"), sip.copy(archivedAt = "2026-11-28")))

        assertNull(SpendableTopUp.fundsSince(fixedOnly, today))
        assertNull(SpendableTopUp.fundsSince(archived, today))
        assertNull(SpendableTopUp.fundsSince(FundsSnapshot.EMPTY, today))
    }

    @Test
    fun fundsSinceIsNeverAfterToday() {
        val future = funds.copy(holdings = listOf(rdHolding.copy(startDate = "2026-12-15")), funds = listOf(rd))

        assertEquals(today, SpendableTopUp.fundsSince(future, today))
    }
}
