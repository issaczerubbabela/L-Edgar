package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.account.AccountMath
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.Fund
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.FundPlan
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import com.issaczerubbabel.ledgar.data.repository.AccountBook
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.fund.FundMath
import com.issaczerubbabel.ledgar.fund.FundsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** How Funds change a cycle's figures (ADR-0011), and that without Funds nothing changes. */
class CycleSummaryFundsTest {

    private val today = LocalDate.of(2026, 10, 10)
    private val cycle = BudgetCycle(id = 1, startDate = "2026-09-26", endDate = "2026-10-25", spendableAmount = 115_842.0)

    private val buckets = listOf(
        BudgetBucket(id = 1, cycleId = 1, name = "Essentials", colorIndex = 0, allocatedAmount = 30_000.0, sortOrder = 0),
        BudgetBucket(id = 2, cycleId = 1, name = "Giving", colorIndex = 1, allocatedAmount = 5_000.0, sortOrder = 1)
    )
    private val assignments = listOf(
        BucketCategory(cycleId = 1, bucketId = 1, category = "Rent"),
        BucketCategory(cycleId = 1, bucketId = 2, category = "Gifts")
    )

    private val accountRecords = listOf(
        AccountRecord(id = 1, groupName = "Accounts", accountName = "HDFC", initialBalance = 150_000.0, initialBalanceDate = "2026-08-31"),
        AccountRecord(id = 2, groupName = "Savings", accountName = "Emergency Fund RD", initialBalance = 30_000.0, initialBalanceDate = "2026-08-31"),
        AccountRecord(id = 3, groupName = "Accounts", accountName = "Ministry Fund", initialBalance = 10_000.0, initialBalanceDate = "2026-08-31")
    )

    private val emergency = Fund(id = 10, name = "Emergency", createdDate = "2026-09-01")
    private val ministry = Fund(id = 11, name = "Ministry", createdDate = "2026-09-01")
    private val funds = FundsSnapshot(
        funds = listOf(emergency, ministry),
        holdings = listOf(
            FundHolding(id = 1, fundId = 10, accountId = 2, kind = HoldingKind.WHOLE.name, startDate = "2026-09-01"),
            FundHolding(id = 2, fundId = 10, accountId = 1, kind = HoldingKind.FIXED.name, amount = 70_000.0, openingAmount = 70_000.0, startDate = "2026-09-01"),
            FundHolding(id = 3, fundId = 11, accountId = 3, kind = HoldingKind.WHOLE.name, startDate = "2026-09-01")
        ),
        plans = listOf(
            FundPlan(fundId = 10, cycleId = 1, plannedAmount = 15_000.0),
            FundPlan(fundId = 11, cycleId = 1, plannedAmount = 5_000.0),
            FundPlan(fundId = 10, cycleId = 99, plannedAmount = 1.0) // another cycle's plan
        )
    )

    private fun expense(date: String, category: String, amount: Double, account: Long = 1) =
        ExpenseRecord(date = date, type = "Expense", category = category, description = "", amount = amount, accountId = account, remarks = "")

    private fun transfer(date: String, amount: Double, from: Long, to: Long) =
        ExpenseRecord(date = date, type = "Transfer", category = "Transfer", description = "", amount = amount, fromAccountId = from, toAccountId = to, remarks = "")

    private val records = listOf(
        expense("2026-09-27", "Rent", 20_000.0),
        expense("2026-09-28", "Gifts", 1_500.0, account = 3), // giving from the Ministry Fund Account: drawn
        expense("2026-09-29", "Food", 2_000.0),
        transfer("2026-09-27", 15_000.0, from = 1, to = 2) // HDFC -> Emergency Fund RD: Set aside
    )

    private fun build(records: List<ExpenseRecord> = this.records, funds: FundsSnapshot = this.funds) =
        CycleSummaryBuilder.build(cycle, buckets, assignments, records, today, funds)

    @Test
    fun withoutFundsEveryCycleFigureIsUnchanged() {
        val before = CycleSummaryBuilder.build(cycle, buckets, assignments, records, today)
        val withEmpty = CycleSummaryBuilder.build(cycle, buckets, assignments, records, today, FundsSnapshot.EMPTY)

        assertEquals(before, withEmpty)
        assertEquals(23_500.0, before.totalSpent, 0.0)
        assertEquals(115_842.0 - 23_500.0, before.leftToSpend, 0.0)
        assertEquals(115_842.0 - 35_000.0, before.unallocated, 0.0)
        assertEquals(1_500.0, before.buckets[1].spent, 0.0)
        assertEquals(0.0, before.setAside, 0.0)
        assertEquals(0.0, before.drawnFromFunds, 0.0)
        assertEquals(0.0, before.plannedContributions, 0.0)
        assertTrue(before.fundContributions.isEmpty())
    }

    @Test
    fun thisCycleIsSpendableLessExpensesNotDrawnFromAFundLessSetAside() {
        val summary = build()

        assertEquals(22_000.0, summary.totalSpent, 0.0)
        assertEquals(1_500.0, summary.drawnFromFunds, 0.0)
        assertEquals(15_000.0, summary.setAside, 0.0)
        assertEquals(115_842.0 - 22_000.0 - 15_000.0, summary.leftToSpend, 0.0)
        assertEquals("the drawn gift isn't in its Bucket", 0.0, summary.buckets[1].spent, 0.0)
        assertEquals(summary.totalSpent, summary.buckets.sumOf { it.spent } + summary.unbucketedSpent, 0.0001)
    }

    @Test
    fun unallocatedTakesOffPlannedContributionsAndIgnoresSpending() {
        val summary = build()

        assertEquals(20_000.0, summary.plannedContributions, 0.0)
        assertEquals(115_842.0 - 35_000.0 - 20_000.0, summary.unallocated, 0.0)
        assertEquals(
            listOf(Triple(10L, 15_000.0, 15_000.0), Triple(11L, 5_000.0, 0.0)),
            summary.fundContributions.map { Triple(it.fund.id, it.planned, it.setAside) }
        )
        assertTrue(summary.fundContributions[0].isMet)
        assertEquals(5_000.0, summary.fundContributions[1].stillToTransfer, 0.0)
    }

    @Test
    fun anArchivedFundNeitherPlansNorHolds() {
        val archived = funds.copy(funds = listOf(emergency.copy(archivedAt = "2026-09-30"), ministry))
        val summary = build(funds = archived)

        assertEquals(0.0, summary.setAside, 0.0)
        assertEquals(5_000.0, summary.plannedContributions, 0.0)
    }

    @Test
    fun aTransferIntoAFundLeavesFreeUnchanged() {
        val beforeTransfer = records.dropLast(1)
        assertEquals(free(beforeTransfer), free(records), 0.0)
    }

    @Test
    fun overspendingTheCycleDoesNotHideFromFree() {
        val overspent = records + expense("2026-10-01", "Rent", 200_000.0)
        val summary = build(overspent)
        assertTrue(summary.leftToSpend < 0)

        val book = AccountBook.of(accountRecords, emptyList(), overspent)
        val balances = AccountMath.balances(book.accounts, book.transactions)
        val yourMoney = FundMath.yourMoney(book.accounts, book.transactions, funds.activeHoldings).total
        assertEquals(
            yourMoney - FundMath.fundsTotal(funds.activeHoldings, balances),
            free(overspent),
            0.0
        )
    }

    private fun free(records: List<ExpenseRecord>): Double {
        val book = AccountBook.of(accountRecords, emptyList(), records)
        val balances = AccountMath.balances(book.accounts, book.transactions)
        val yourMoney = FundMath.yourMoney(book.accounts, book.transactions, funds.activeHoldings)
        return FundMath.free(yourMoney.total, FundMath.fundsTotal(funds.activeHoldings, balances), build(records).leftToSpend)
    }
}
