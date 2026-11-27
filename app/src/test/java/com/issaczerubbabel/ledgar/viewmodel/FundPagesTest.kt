package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
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

class FundPagesTest {

    private val today = LocalDate.of(2026, 10, 10)

    private val hdfc = AccountRecord(id = 1, groupName = "Bank", accountName = "HDFC", initialBalance = 100_000.0, initialBalanceDate = "2026-08-31")
    private val rd = AccountRecord(id = 2, groupName = "Deposits", accountName = "Emergency Fund RD", initialBalance = 15_000.0, initialBalanceDate = "2026-08-31")
    private val cash = AccountRecord(id = 3, groupName = "Cash", accountName = "Cash", initialBalance = 2_000.0, initialBalanceDate = "2026-08-31")
    private val insurance = AccountRecord(id = 4, groupName = "Deposits", accountName = "Insurance RD", initialBalance = 9_000.0,
        initialBalanceDate = "2026-08-31", includeInTotals = false)

    private val emergency = Fund(id = 10, name = "Emergency", emoji = "🛟", colorIndex = 2, targetAmount = 200_000.0,
        dueDate = "2027-03-31", createdDate = "2026-09-01", sortOrder = 0)
    private val trip = Fund(id = 11, name = "Goa trip", createdDate = "2026-09-01", sortOrder = 1)
    private val old = Fund(id = 12, name = "Old RD", createdDate = "2026-09-01", archivedAt = "2026-10-01", sortOrder = 2)
    private val cover = Fund(id = 13, name = "Insurance", createdDate = "2026-09-01", sortOrder = 3)

    private val emergencyWhole = FundHolding(1, 10, 2, HoldingKind.WHOLE.name, openingAmount = 15_000.0, startDate = "2026-09-01")
    private val emergencyFixed = FundHolding(2, 10, 1, HoldingKind.FIXED.name, amount = 70_000.0, openingAmount = 70_000.0, startDate = "2026-09-01")
    private val tripFixed = FundHolding(3, 11, 3, HoldingKind.FIXED.name, amount = 5_000.0, startDate = "2026-09-01")
    private val coverWhole = FundHolding(4, 13, 4, HoldingKind.WHOLE.name, startDate = "2026-09-01")

    private val cycle = BudgetCycle(id = 1, startDate = "2026-09-26", endDate = "2026-10-25", spendableAmount = 100_000.0)

    private var nextId = 1L
    private fun txn(date: String, type: String, amount: Double, accountId: Long? = null, from: Long? = null, to: Long? = null) =
        ExpenseRecord(id = nextId++, date = date, type = type, category = if (type == "Transfer") "Transfer" else "Giving",
            description = "", amount = amount, accountId = accountId, remarks = "", fromAccountId = from, toAccountId = to)

    // A ₹15,000 Set aside into the RD this cycle, and ₹1,000 drawn from it.
    private val transactions = listOf(
        txn("2026-10-01", "Transfer", 15_000.0, from = 1, to = 2),
        txn("2026-10-05", "Expense", 1_000.0, accountId = 2, from = 2),
        txn("2026-10-06", "Expense", 500.0, accountId = 1, from = 1)
    )

    private val book = AccountBook.of(listOf(hdfc, rd, cash, insurance), emptyList(), transactions)
    private val snapshot = FundsSnapshot(
        funds = listOf(emergency, trip, old, cover),
        holdings = listOf(emergencyWhole, emergencyFixed, tripFixed, coverWhole),
        plans = listOf(FundPlan(fundId = 10, cycleId = 1, plannedAmount = 20_000.0))
    )

    @Test
    fun theListShowsEachFundsBalanceAndTarget() {
        val list = fundsList(snapshot, book)
        assertEquals(listOf("Emergency", "Goa trip", "Insurance"), list.active.map { it.name })
        assertEquals(listOf("Old RD"), list.archived.map { it.name })
        val first = list.active.first()
        // RD 15,000 + 15,000 − 1,000 = 29,000, plus 70,000 fixed in HDFC.
        assertEquals("₹99,000", first.balance)
        assertEquals("₹99,000 of ₹2,00,000", first.progress)
        assertEquals(0.495f, first.progressFraction!!, 0.001f)
        assertNull(first.shortBy)
        assertNull(list.active[1].progress)
    }

    @Test
    fun aFixedHoldingAboveItsAccountsBalanceShowsShortInWords() {
        val spent = AccountBook.of(listOf(hdfc, rd, cash, insurance), emptyList(), transactions + txn("2026-10-07", "Expense", 4_000.0, accountId = 3, from = 3))
        val tripRow = fundsList(snapshot, spent).active.single { it.name == "Goa trip" }
        // Cash is 2,000 − 4,000 = −2,000 against 5,000 fixed: 7,000 short.
        assertEquals("₹7,000 short", tripRow.shortBy)
        assertTrue(tripRow.description.contains("₹7,000 short"))
    }

    @Test
    fun issue40AcceptanceEmergencyHoldsTheRdWholeAndSeventyThousandOfHdfc() {
        val rdAt30k = rd.copy(initialBalance = 30_000.0)
        val before = AccountBook.of(listOf(hdfc, rdAt30k), emptyList(), emptyList())
        val funds = FundsSnapshot(listOf(emergency), listOf(emergencyWhole, emergencyFixed))
        assertEquals("₹1,00,000", fundsList(funds, before).active.single().balance)
        assertEquals("Free ₹30,000.00", accountFund(1, funds, before)!!.label)

        // A ₹15,000 Transfer from HDFC to the RD raises the Fund by ₹15,000.
        val after = AccountBook.of(listOf(hdfc, rdAt30k), emptyList(), listOf(txn("2026-10-01", "Transfer", 15_000.0, from = 1, to = 2)))
        assertEquals("₹1,15,000", fundsList(funds, after).active.single().balance)
        assertEquals("Free ₹15,000.00", accountFund(1, funds, after)!!.label)
    }

    @Test
    fun eachHoldingRowReadsAsOneSentence() {
        val detail = fundDetail(10, snapshot, book, cycle, today)!!
        assertEquals(listOf("Emergency Fund RD, whole account, ₹29,000", "₹70,000 fixed in HDFC"), detail.holdings.map { it.sentence })
        assertEquals("Emergency Fund RD ₹29,000 · HDFC ₹70,000 fixed", detail.holdingSummary)
        assertEquals("Opening ₹15,000 on 1 Sep 2026", detail.holdings.first().openingNote)
    }

    @Test
    fun aHoldingInAnAccountLeftOutOfTotalsSaysSo() {
        val row = fundDetail(13, snapshot, book, cycle, today)!!.holdings.single()
        assertTrue(row.notInTotals)
        assertEquals("Insurance RD, whole account, ₹9,000, not counted in Your money", row.sentence)
    }

    @Test
    fun theDetailShowsThisCyclesPlannedAgainstTransferred() {
        val plan = fundDetail(10, snapshot, book, cycle, today)!!.cycle!!
        assertEquals("₹20,000", plan.planned)
        assertEquals("₹15,000", plan.transferred)
        assertEquals("₹5,000 still to transfer", plan.status)
        assertFalse(plan.isMet)
    }

    @Test
    fun noCycleMeansNoCycleCard() {
        assertNull(fundDetail(10, snapshot, book, cycle = null, today = today)!!.cycle)
    }

    @Test
    fun aFundWithNothingPlannedOrTransferredThisCycleHasNoCycleCard() {
        assertNull(fundDetail(11, snapshot, book, cycle, today)!!.cycle)
    }

    @Test
    fun theHistoryListsSetAsideInAndDrawsNewestFirst() {
        val history = fundDetail(10, snapshot, book, cycle, today)!!.history
        assertEquals(listOf(FundHistoryKind.DRAWN, FundHistoryKind.SET_ASIDE), history.map { it.kind })
        assertEquals("5 Oct · Drawn", history.first().trailingLine)
        assertEquals("1 Oct · Set aside", history.last().trailingLine)
    }

    @Test
    fun theDetailOfAMissingFundIsNull() {
        assertNull(fundDetail(99, snapshot, book, cycle, today))
    }

    @Test
    fun anAccountWithFixedHoldingsShowsFreeAsBalanceLessThem() {
        val free = accountFund(1, snapshot, book)!!
        // HDFC: 100,000 − 15,000 − 500 = 84,500, less 70,000 fixed.
        assertEquals("Free ₹14,500.00", free.label)
        assertEquals("₹70,000.00 held for Emergency", free.detail)
        assertFalse(free.isShort)
    }

    @Test
    fun anAccountAFundHoldsWholeNamesTheFund() {
        val free = accountFund(2, snapshot, book)!!
        assertEquals("Fund: Emergency", free.label)
    }

    @Test
    fun anAccountNoFundHoldsIsAllFree() {
        val other = AccountRecord(id = 5, groupName = "Bank", accountName = "SBI", initialBalance = 300.0, initialBalanceDate = "2026-08-31")
        val withOther = AccountBook.of(listOf(hdfc, rd, cash, insurance, other), emptyList(), transactions)
        assertEquals("Free ₹300.00", accountFund(5, snapshot, withOther)!!.label)
    }

    @Test
    fun withNoFundsTheAccountPageShowsNoFreeLine() {
        assertNull(accountFund(1, FundsSnapshot.EMPTY, book))
    }

    @Test
    fun aShortAccountSaysSoInWords() {
        val spent = AccountBook.of(listOf(hdfc, rd, cash, insurance), emptyList(), transactions + txn("2026-10-07", "Expense", 4_000.0, accountId = 3, from = 3))
        val free = accountFund(3, snapshot, spent)!!
        assertEquals("Free −₹7,000.00", free.label)
        assertTrue(free.isShort)
        assertEquals("₹5,000.00 held for Goa trip · ₹7,000.00 short", free.detail)
    }
}
