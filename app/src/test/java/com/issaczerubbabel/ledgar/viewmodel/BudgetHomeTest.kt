package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.bucket.CycleSummaryBuilder
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.DropdownRole
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.Fund
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.FundPlan
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import com.issaczerubbabel.ledgar.data.repository.AccountBook
import com.issaczerubbabel.ledgar.fund.FundsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The Budget tab with Funds (ADR-0011, #41): the whole salary accounted for, and nothing changed without Funds. */
class BudgetHomeTest {

    private val today = LocalDate.of(2026, 10, 10)
    private val cycle = BudgetCycle(id = 1, startDate = "2026-09-26", endDate = "2026-10-25", spendableAmount = 115_842.0)

    private val buckets = listOf(
        BudgetBucket(id = 1, cycleId = 1, name = "Essentials", colorIndex = 0, allocatedAmount = 40_000.0, sortOrder = 0),
        BudgetBucket(id = 2, cycleId = 1, name = "Family", colorIndex = 1, allocatedAmount = 25_000.0, sortOrder = 1),
        BudgetBucket(id = 3, cycleId = 1, name = "Fun", colorIndex = 2, allocatedAmount = 5_842.0, sortOrder = 2)
    )
    private val assignments = listOf(
        BucketCategory(cycleId = 1, bucketId = 1, category = "Rent"),
        BucketCategory(cycleId = 1, bucketId = 2, category = "Gifts"),
        BucketCategory(cycleId = 1, bucketId = 3, category = "Movies")
    )

    private val asOf = "2026-08-31"
    private val accounts = listOf(
        AccountRecord(id = 1, groupName = "Accounts", accountName = "HDFC", initialBalance = 200_000.0, initialBalanceDate = asOf, displayOrder = 0),
        AccountRecord(id = 2, groupName = "Savings", accountName = "Emergency Fund RD", initialBalance = 30_000.0, initialBalanceDate = asOf, displayOrder = 1),
        AccountRecord(id = 3, groupName = "Savings", accountName = "Insurance RD", initialBalance = 12_000.0, initialBalanceDate = asOf, displayOrder = 2),
        AccountRecord(id = 4, groupName = "Investments", accountName = "SIP", initialBalance = 50_000.0, initialBalanceDate = asOf, displayOrder = 3),
        AccountRecord(id = 5, groupName = "Accounts", accountName = "Ministry Fund", initialBalance = 10_000.0, initialBalanceDate = asOf, displayOrder = 4),
        AccountRecord(id = 6, groupName = "Savings", accountName = "PPF", initialBalance = 80_000.0, initialBalanceDate = asOf, displayOrder = 5),
        AccountRecord(id = 7, groupName = "Accounts", accountName = "Mom's wallet", initialBalance = 3_000.0, initialBalanceDate = asOf, includeInTotals = false, displayOrder = 6)
    )
    private val options = listOf(
        DropdownOption(optionType = "ACCOUNT_GROUP", name = "Savings", displayOrder = 0, role = DropdownRole.SAVINGS)
    )
    private val roles = StatsRoles.from(options)

    private val emergency = Fund(id = 10, name = "Emergency", createdDate = "2026-09-01", sortOrder = 0)
    private val insurance = Fund(id = 11, name = "Insurance", createdDate = "2026-09-01", sortOrder = 1)
    private val sip = Fund(id = 12, name = "SIP", createdDate = "2026-09-01", sortOrder = 2)
    private val ministry = Fund(id = 13, name = "Ministry", createdDate = "2026-09-01", sortOrder = 3)

    private fun whole(id: Long, fund: Long, account: Long) =
        FundHolding(id = id, fundId = fund, accountId = account, kind = HoldingKind.WHOLE.name, startDate = "2026-09-01")

    private val funds = FundsSnapshot(
        funds = listOf(emergency, insurance, sip, ministry),
        holdings = listOf(
            whole(1, 10, 2),
            FundHolding(id = 2, fundId = 10, accountId = 1, kind = HoldingKind.FIXED.name, amount = 70_000.0, openingAmount = 70_000.0, startDate = "2026-09-01"),
            whole(3, 11, 3),
            whole(4, 12, 4),
            whole(5, 13, 5)
        ),
        plans = listOf(
            FundPlan(fundId = 10, cycleId = 1, plannedAmount = 15_000.0),
            FundPlan(fundId = 11, cycleId = 1, plannedAmount = 4_000.0),
            FundPlan(fundId = 12, cycleId = 1, plannedAmount = 10_000.0),
            FundPlan(fundId = 13, cycleId = 1, plannedAmount = 6_000.0)
        )
    )

    private var nextId = 100L
    private fun expense(date: String, category: String, amount: Double, account: Long = 1) =
        ExpenseRecord(id = nextId++, date = date, type = "Expense", category = category, description = "", amount = amount, accountId = account, remarks = "")

    private fun transfer(date: String, amount: Double, from: Long, to: Long) =
        ExpenseRecord(id = nextId++, date = date, type = "Transfer", category = "Transfer", description = "", amount = amount, fromAccountId = from, toAccountId = to, remarks = "")

    private val salary = ExpenseRecord(id = 1, date = "2026-09-26", type = "Income", category = "Salary", description = "", amount = 115_842.0, accountId = 1, remarks = "")

    private val records = listOf(
        salary,
        expense("2026-09-27", "Rent", 20_000.0),
        expense("2026-09-29", "Food", 2_000.0), // in no Bucket
        transfer("2026-09-28", 10_000.0, from = 1, to = 4) // HDFC -> SIP: Set aside
    )

    private fun state(
        records: List<ExpenseRecord> = this.records,
        funds: FundsSnapshot = this.funds,
        cycle: BudgetCycle = this.cycle,
        accounts: List<AccountRecord> = this.accounts,
        fundsSince: LocalDate? = null,
        answeredTopUps: Set<Long> = emptySet()
    ) =
        budgetHome(
            cycle = cycle,
            cycleIndex = 0,
            cycleCount = 1,
            buckets = buckets,
            assignments = assignments,
            book = AccountBook.of(accounts, options, records),
            funds = funds,
            roles = roles,
            today = today,
            fundsSince = fundsSince,
            answeredTopUps = answeredTopUps
        )

    @Test
    fun theBudgetTabAccountsForTheWholeSalary() {
        val summary = state().summary!!

        assertEquals(70_842.0, summary.allocated, 0.001)
        assertEquals(35_000.0, summary.plannedContributions, 0.001)
        assertEquals(10_000.0, summary.unallocated, 0.001)
        assertEquals(cycle.spendableAmount, summary.allocated + summary.plannedContributions + summary.unallocated, 0.001)
    }

    @Test
    fun leftToSpendIsSpendableLessExpensesLessSetAside() {
        val summary = state().summary!!

        assertEquals(22_000.0, summary.totalSpent, 0.001)
        assertEquals(10_000.0, summary.setAside, 0.001)
        assertEquals(115_842.0 - 22_000.0 - 10_000.0, summary.leftToSpend, 0.001)
    }

    @Test
    fun theYourMoneyCardSplitsIntoFundsThisCycleAndFree() {
        val money = state().yourMoney!!

        // Included Accounts: HDFC 2,00,000 + 1,15,842 − 22,000 − 10,000; RD 30,000; Insurance 12,000;
        // SIP 50,000 + 10,000; Ministry 10,000; PPF 80,000. Mom's wallet isn't Included.
        assertEquals(475_842.0, money.total, 0.001)
        // Emergency 30,000 + 70,000, Insurance 12,000, SIP 60,000, Ministry 10,000.
        assertEquals(182_000.0, money.funds, 0.001)
        assertEquals(83_842.0, money.thisCycle, 0.001)
        assertEquals(475_842.0 - 182_000.0 - 83_842.0, money.free, 0.001)
        assertEquals(money.total, money.funds + money.thisCycle + money.free, 0.001)
        assertFalse(money.isFreeNegative)
    }

    @Test
    fun aTransferToAFundTicksOffItsContributionAndLeavesFreeUnchanged() {
        val before = state()
        val after = state(records + transfer("2026-10-01", 15_000.0, from = 1, to = 2))

        val emergencyBefore = before.summary!!.fundContributions.single { it.fund.id == 10L }
        val emergencyAfter = after.summary!!.fundContributions.single { it.fund.id == 10L }
        assertFalse(emergencyBefore.isMet)
        assertEquals(15_000.0, emergencyBefore.stillToTransfer, 0.001)
        assertTrue(emergencyAfter.isMet)
        assertEquals(0.0, emergencyAfter.stillToTransfer, 0.001)

        assertEquals(before.yourMoney!!.free, after.yourMoney!!.free, 0.001)
        assertEquals(before.yourMoney!!.thisCycle - 15_000.0, after.yourMoney!!.thisCycle, 0.001)
    }

    @Test
    fun givingFromTheMinistryFundDoesNotLowerThisCycle() {
        val before = state()
        val after = state(records + expense("2026-10-02", "Gifts", 2_500.0, account = 5))

        assertEquals(before.summary!!.leftToSpend, after.summary!!.leftToSpend, 0.001)
        assertEquals(before.yourMoney!!.thisCycle, after.yourMoney!!.thisCycle, 0.001)
        assertEquals(2_500.0, after.summary!!.drawnFromFunds, 0.001)
        // The money leaves the Ministry Fund, so the Funds part shrinks and Free doesn't move.
        assertEquals(before.yourMoney!!.funds - 2_500.0, after.yourMoney!!.funds, 0.001)
        assertEquals(before.yourMoney!!.free, after.yourMoney!!.free, 0.001)
    }

    @Test
    fun withoutFundsTheBudgetStateIsExactlyAsBefore() {
        val state = state(funds = FundsSnapshot.EMPTY)

        val before = BudgetUiState(
            isLoading = false,
            summary = CycleSummaryBuilder.build(cycle, buckets, assignments, records, today),
            cycleIndex = 0,
            cycleCount = 1
        )
        assertEquals(before, state)
        assertNull(state.yourMoney)
        assertFalse(state.hasFunds)
        assertEquals(0.0, state.setAsideNoFund, 0.0)
    }

    @Test
    fun onlyArchivedFundsCountAsNoFunds() {
        val archived = FundsSnapshot(funds = listOf(emergency.copy(archivedAt = "2026-09-20")), holdings = listOf(whole(1, 10, 2)))

        assertFalse(state(funds = archived).hasFunds)
        assertNull(state(funds = archived).yourMoney)
    }

    @Test
    fun aTransferIntoASavingsAccountNoFundHoldsIsSetAsideNoFund() {
        val withPpf = state(records + transfer("2026-10-03", 5_000.0, from = 1, to = 6) + transfer("2026-08-03", 9_000.0, from = 1, to = 6))

        assertEquals(5_000.0, withPpf.setAsideNoFund, 0.001)
        // It's not Set aside, so it doesn't come off Left to spend.
        assertEquals(state().summary!!.leftToSpend, withPpf.summary!!.leftToSpend, 0.001)
    }

    @Test
    fun aTransferOutOfAFundIntoSavingsIsNotSetAsideNoFund() {
        assertEquals(0.0, state(records + transfer("2026-10-03", 5_000.0, from = 2, to = 6)).setAsideNoFund, 0.0)
    }

    @Test
    fun negativeFreeIsExplained() {
        // A cycle started before the salary lands: the plan counts money that isn't in an Account yet.
        val emptyHdfc = accounts.map { if (it.id == 1L) it.copy(initialBalance = 0.0) else it }
        val money = state(records - salary, accounts = emptyHdfc).yourMoney!!

        assertTrue(money.isFreeNegative)
        assertEquals(money.total - money.funds - money.thisCycle, money.free, 0.001)
        assertEquals("Your plan uses ${com.issaczerubbabel.ledgar.util.formatRupees(-money.free)} more than you have", money.overPlannedText)
    }

    @Test
    fun theCardReadsAsOneSentence() {
        val money = state().yourMoney!!

        assertEquals(
            "Your money ₹4,75,842: Funds ₹1,82,000, This cycle ₹83,842 left and Free ₹2,10,000",
            money.spokenSummary
        )
    }

    @Test
    fun anOverspentCycleShowsNothingLeftAndFreeDoesNotGrow() {
        val overspent = state(records + expense("2026-10-04", "Rent", 100_000.0))
        val money = overspent.yourMoney!!

        assertTrue(overspent.summary!!.leftToSpend < 0)
        assertEquals(0.0, money.thisCycle, 0.0)
        assertEquals(money.total - money.funds, money.free, 0.001)
    }

    @Test
    fun eachAccountShowsWhatItsFundsHoldAndTheRest() {
        val split = state().yourMoney!!.accounts

        assertEquals(listOf("HDFC", "Emergency Fund RD", "Insurance RD", "SIP", "Ministry Fund", "PPF"), split.map { it.name })
        val hdfc = split.first { it.name == "HDFC" }
        assertEquals(283_842.0, hdfc.balance, 0.001)
        assertEquals(listOf(FundPart("Emergency", 70_000.0)), hdfc.funds)
        assertEquals(213_842.0, hdfc.rest, 0.001)
        val rd = split.first { it.name == "Emergency Fund RD" }
        assertEquals(listOf(FundPart("Emergency", 30_000.0)), rd.funds)
        assertEquals(0.0, rd.rest, 0.0)
        assertEquals(80_000.0, split.first { it.name == "PPF" }.rest, 0.001)
    }

    @Test
    fun aFixedHoldingLargerThanItsAccountIsShort() {
        val big = funds.copy(holdings = funds.holdings.map { if (it.id == 2L) it.copy(amount = 300_000.0) else it })
        val hdfc = state(funds = big).yourMoney!!.accounts.first { it.name == "HDFC" }

        assertEquals(16_158.0, hdfc.shortBy, 0.001)
        assertEquals(0.0, state().yourMoney!!.accounts.first { it.name == "HDFC" }.shortBy, 0.0)
    }

    @Test
    fun aFundInAnAccountNotIncludedInTotalsIsFlagged() {
        val withWallet = funds.copy(holdings = funds.holdings + whole(9, 13, 7))

        assertEquals(1, state(funds = withWallet).yourMoney!!.holdingsOutsideTotals)
        assertEquals(0, state().yourMoney!!.holdingsOutsideTotals)
    }

    @Test
    fun aFundInAnAccountNotIncludedInTotalsDoesNotLowerFree() {
        val withWallet = state(funds = funds.copy(holdings = funds.holdings + whole(9, 13, 7))).yourMoney!!
        val money = state().yourMoney!!

        assertEquals(money.funds, withWallet.funds, 0.001)
        assertEquals(money.free, withWallet.free, 0.001)
        assertEquals(withWallet.total, withWallet.funds + withWallet.thisCycle + withWallet.free, 0.001)
    }

    @Test
    fun aClosedCycleHasNoYourMoneyCard() {
        val closed = state(cycle = cycle.copy(closedAt = "2026-10-26"))

        assertNull(closed.yourMoney)
        assertTrue(closed.hasFunds)
    }

    @Test
    fun aCyclePlannedBeforeFundsOffersToRaiseSpendable() {
        val midCycle = LocalDate.of(2026, 10, 1)

        // ₹10,000 Set aside into the SIP, plus ₹25,000 of Planned contributions still to transfer.
        assertEquals(35_000.0, state(fundsSince = midCycle).spendableTopUp!!, 0.001)
        assertNull(state(fundsSince = midCycle, answeredTopUps = setOf(cycle.id)).spendableTopUp)
        assertNull(state(fundsSince = LocalDate.of(2026, 9, 1)).spendableTopUp)
        assertNull(state(fundsSince = midCycle, cycle = cycle.copy(closedAt = "2026-10-26")).spendableTopUp)
        assertNull(state(fundsSince = midCycle, funds = FundsSnapshot.EMPTY).spendableTopUp)
    }
}
