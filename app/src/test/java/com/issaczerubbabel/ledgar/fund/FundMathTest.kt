package com.issaczerubbabel.ledgar.fund

import com.issaczerubbabel.ledgar.account.AccountMath
import com.issaczerubbabel.ledgar.account.AccountSnapshot
import com.issaczerubbabel.ledgar.account.TransactionSnapshot
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FundMathTest {

    private val today = LocalDate.of(2026, 10, 10)

    // Accounts: the salary Account, two whole-Account Funds, a credit card, and an RD left out of totals.
    private val hdfc = account(1, "HDFC", initial = 100_000.0, asOf = "2026-08-31")
    private val emergencyRd = account(2, "Emergency Fund RD", initial = 20_000.0, asOf = "2026-08-31")
    private val ministry = account(3, "Ministry Fund", initial = 5_000.0, asOf = "2026-08-31")
    private val card = account(4, "HDFC Millennia", liability = true, asOf = "2026-08-31")
    private val insuranceRd = account(5, "Insurance RD", initial = 12_000.0, asOf = "2026-08-31", included = false)
    private val accounts = listOf(hdfc, emergencyRd, ministry, card, insuranceRd)

    private val emergencyWhole = holding(1, fund = EMERGENCY, account = 2, kind = HoldingKind.WHOLE)
    private val emergencyFixed = holding(2, fund = EMERGENCY, account = 1, kind = HoldingKind.FIXED, amount = 70_000.0, opening = 70_000.0)
    private val ministryWhole = holding(3, fund = MINISTRY, account = 3, kind = HoldingKind.WHOLE)
    private val insuranceWhole = holding(4, fund = INSURANCE, account = 5, kind = HoldingKind.WHOLE)
    private val holdings = listOf(emergencyWhole, emergencyFixed, ministryWhole, insuranceWhole)

    private val cycle = BudgetCycle(id = 1, startDate = "2026-09-26", endDate = "2026-10-25", spendableAmount = 115_842.0)

    private fun account(
        id: Long,
        name: String,
        initial: Double = 0.0,
        asOf: String,
        liability: Boolean = false,
        included: Boolean = true
    ) = AccountSnapshot(id, name, "Accounts", liability, initial, LocalDate.parse(asOf), included)

    private fun holding(
        id: Long,
        fund: Long,
        account: Long,
        kind: HoldingKind,
        amount: Double = 0.0,
        opening: Double = 0.0,
        start: String = "2026-09-01"
    ) = FundHolding(id, fund, account, kind.name, amount, opening, start)

    private var nextId = 1L
    private fun txn(type: String, date: String, amount: Double, account: Long? = null, from: Long? = null, to: Long? = null) =
        TransactionSnapshot(nextId++, type, LocalDate.parse(date), amount, account, from, to)

    private fun transfer(date: String, amount: Double, from: Long, to: Long) = txn("Transfer", date, amount, from = from, to = to)

    private fun balances(transactions: List<TransactionSnapshot>) = AccountMath.balances(accounts, transactions)

    // ── Holding values ───────────────────────────────────────────────────────

    @Test
    fun aWholeHoldingIsItsAccountsBalanceAndMoneyBeforeTheAsOfDateIsTheInitialBalance() {
        val transactions = listOf(
            transfer("2026-08-20", 15_000.0, from = 1, to = 2), // before the As-of date: already in the Initial balance
            transfer("2026-09-27", 15_000.0, from = 1, to = 2)
        )

        assertEquals(35_000.0, FundMath.holdingValue(emergencyWhole, balances(transactions)), 0.0)
    }

    @Test
    fun aFixedHoldingIsItsAmountWhateverItsAccountDoesAndTheOpeningAmountIsNotAddedAgain() {
        val transactions = listOf(txn("Expense", "2026-09-28", 2_000.0, account = 1))

        assertEquals(70_000.0, FundMath.holdingValue(emergencyFixed, balances(transactions)), 0.0)
        assertEquals(
            "an opening amount on a WHOLE holding is history, not extra money",
            20_000.0,
            FundMath.holdingValue(emergencyWhole.copy(openingAmount = 20_000.0), balances(emptyList())),
            0.0
        )
    }

    @Test
    fun aFundsBalanceAddsUpItsHoldings() {
        val balances = balances(listOf(transfer("2026-09-27", 10_000.0, from = 1, to = 2)))

        assertEquals(100_000.0, FundMath.fundBalance(EMERGENCY, holdings, balances), 0.0)
        assertEquals(
            mapOf(EMERGENCY to 100_000.0, MINISTRY to 5_000.0, INSURANCE to 12_000.0),
            FundMath.fundBalances(holdings, balances)
        )
    }

    // ── Set aside ────────────────────────────────────────────────────────────

    @Test
    fun onlyATransferFromANonFundAccountIntoAFundsWholeAccountIsSetAside() {
        val transactions = listOf(
            transfer("2026-09-27", 15_000.0, from = 1, to = 2), // HDFC -> Emergency RD: Set aside
            transfer("2026-09-28", 3_000.0, from = 2, to = 3), // Fund -> Fund: a move
            transfer("2026-09-29", 8_000.0, from = 1, to = 4), // a card payment
            txn("Adjustment", "2026-09-30", 400.0, account = 2), // interest, as a Balance adjustment
            txn("Income", "2026-09-30", 250.0, account = 3) // Income into a Fund Account
        )

        val setAside = FundMath.setAside(cycle, transactions, holdings, today)

        assertEquals(15_000.0, setAside.total, 0.0)
        assertEquals(mapOf(EMERGENCY to 15_000.0), setAside.byFund)
    }

    @Test
    fun setAsideCountsInTheCyclesWindowAndFromTheHoldingsStartDate() {
        val transactions = listOf(
            transfer("2026-09-25", 1_000.0, from = 1, to = 2), // the day before the cycle
            transfer("2026-09-26", 2_000.0, from = 1, to = 2), // its first day
            transfer("2026-10-25", 4_000.0, from = 1, to = 2), // its last day
            transfer("2026-10-26", 8_000.0, from = 1, to = 2) // after both its end date and today
        )
        assertEquals(6_000.0, FundMath.setAside(cycle, transactions, holdings, today).total, 0.0)

        val lateStart = listOf(emergencyWhole.copy(startDate = "2026-10-01"))
        assertEquals(
            "a Transfer from before the holding started isn't Set aside",
            4_000.0,
            FundMath.setAside(cycle, transactions, lateStart, today).total,
            0.0
        )

        // An overdue running cycle keeps counting up to today, like its spend (#115).
        val overdueToday = LocalDate.of(2026, 10, 27)
        assertEquals(14_000.0, FundMath.setAside(cycle, transactions, holdings, overdueToday).total, 0.0)
    }

    // ── Draws ────────────────────────────────────────────────────────────────

    @Test
    fun anExpensePaidFromAFundsWholeAccountIsDrawnFromThatFund() {
        val giving = txn("Expense", "2026-09-28", 1_500.0, account = 3)
        val groceries = txn("Expense", "2026-09-28", 900.0, account = 1)
        val fromCard = txn("Expense", "2026-09-28", 600.0, account = 3, from = 4) // fromAccountId wins, as in AccountMath

        assertEquals(MINISTRY, FundMath.drawnFrom(giving, holdings))
        assertNull(FundMath.drawnFrom(groceries, holdings))
        assertNull(FundMath.drawnFrom(fromCard, holdings))
        assertNull("a Transfer out is a release, not a draw", FundMath.drawnFrom(transfer("2026-09-28", 1.0, from = 3, to = 1), holdings))

        val drawn = FundMath.drawn(cycle, listOf(giving, groceries, fromCard), holdings, today)
        assertEquals(1_500.0, drawn.total, 0.0)
        assertEquals(mapOf(MINISTRY to 1_500.0), drawn.byFund)
    }

    // ── Your money and Free ──────────────────────────────────────────────────

    @Test
    fun yourMoneyIsNetWorthOverIncludedAccountsAndAHoldingInAnExcludedAccountIsFlagged() {
        val yourMoney = FundMath.yourMoney(accounts, emptyList(), holdings)

        assertEquals(125_000.0, yourMoney.total, 0.0) // 1,00,000 + 20,000 + 5,000; the Insurance RD is left out
        assertEquals(listOf(insuranceWhole), yourMoney.excludedHoldings)
    }

    @Test
    fun freeIsYourMoneyLessFundsLessWhatIsLeftOfThisCycle() {
        assertEquals(20_000.0, FundMath.free(yourMoney = 150_000.0, funds = 100_000.0, thisCycle = 30_000.0), 0.0)
    }

    @Test
    fun overspendingTheCycleNeverMakesFreeLookLarger() {
        assertEquals(50_000.0, FundMath.free(yourMoney = 150_000.0, funds = 100_000.0, thisCycle = -8_000.0), 0.0)
        assertEquals("Free can go below zero", -10_000.0, FundMath.free(100_000.0, 100_000.0, 10_000.0), 0.0)
    }

    // ── Shortfall and an Account's Free ─────────────────────────────────────

    @Test
    fun aFixedHoldingAboveItsAccountsBalanceIsShortByTheDifference() {
        val spent = listOf(txn("Expense", "2026-09-28", 50_000.0, account = 1)) // HDFC down to 50,000

        assertEquals(mapOf(1L to 20_000.0), FundMath.shortfalls(holdings, balances(spent)))
        assertTrue(FundMath.shortfalls(holdings, balances(emptyList())).isEmpty())
    }

    @Test
    fun anAccountsFreeIsItsBalanceLessFixedHoldingsAndNothingForAWholeHolding() {
        val free = FundMath.accountFree(listOf(1L, 2L, 4L), holdings, balances(emptyList()))

        assertEquals(mapOf(1L to 30_000.0, 2L to 0.0, 4L to 0.0), free)
    }

    // ── One Account, one whole holding ──────────────────────────────────────

    @Test
    fun ifTwoFundsClaimOneAccountWholeTheFirstKeepsIt() {
        val clash = holding(9, fund = MINISTRY, account = 2, kind = HoldingKind.WHOLE)

        assertEquals(EMERGENCY, FundMath.wholeAccounts(listOf(emergencyWhole, clash))[2L])
        assertEquals(
            "the Account's money is never counted twice",
            mapOf(EMERGENCY to 20_000.0, MINISTRY to 0.0),
            FundMath.fundBalances(listOf(emergencyWhole, clash), balances(emptyList()))
        )
    }

    private companion object {
        const val EMERGENCY = 10L
        const val MINISTRY = 11L
        const val INSURANCE = 12L
    }
}
