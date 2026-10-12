package com.issaczerubbabel.ledgar.account

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AccountMathTest {

    private val salary = account(1, "SBI Salary", initial = 10_000.0, asOf = "2026-09-30")

    private fun account(
        id: Long,
        name: String,
        group: String = "Accounts",
        initial: Double = 0.0,
        asOf: String = "2026-01-01",
        liability: Boolean = false,
        included: Boolean = true
    ) = AccountSnapshot(id, name, group, liability, initial, LocalDate.parse(asOf), included)

    private fun txn(
        id: Long,
        type: String,
        date: String,
        amount: Double,
        accountId: Long? = null,
        fromAccountId: Long? = null,
        toAccountId: Long? = null
    ) = TransactionSnapshot(id, type, LocalDate.parse(date), amount, accountId, fromAccountId, toAccountId)

    @Test
    fun aTransactionOnTheAsOfDateIsAlreadyInTheInitialBalanceButTheNextDaysCounts() {
        val balances = AccountMath.balances(
            listOf(salary),
            listOf(
                txn(1, "Expense", "2026-09-30", 500.0, accountId = 1),
                txn(2, "Expense", "2026-10-01", 200.0, accountId = 1)
            )
        )

        assertEquals(9_800.0, balances.getValue(1), 0.0)
    }

    @Test
    fun eachTypeMovesTheAccountsItNamesWithFromAndToTakingPrecedence() {
        val card = account(2, "HDFC Millennia", group = "Credit card", liability = true)
        val wallet = account(3, "Wallet")
        val balances = AccountMath.balances(
            listOf(salary, card, wallet),
            listOf(
                txn(1, "Income", "2026-10-01", 50_000.0, accountId = 3, toAccountId = 1),
                txn(2, "Expense", "2026-10-02", 1_200.0, accountId = 3, fromAccountId = 2),
                txn(3, "Expense", "2026-10-02", 80.0, accountId = 3),
                txn(4, "Transfer", "2026-10-03", 1_000.0, fromAccountId = 1, toAccountId = 2),
                txn(5, "Adjustment", "2026-10-04", -45.5, accountId = 1),
                txn(6, "Refund?", "2026-10-04", 999.0, accountId = 1)
            )
        )

        assertEquals(mapOf(1L to 58_954.5, 2L to -200.0, 3L to -80.0), balances)
    }

    @Test
    fun aTransferToAnAccountItDoesntKnowStillLeavesTheSource() {
        val balances = AccountMath.balances(
            listOf(salary),
            listOf(txn(1, "Transfer", "2026-10-03", 300.0, fromAccountId = 1, toAccountId = null))
        )

        assertEquals(9_700.0, balances.getValue(1), 0.0)
    }

    @Test
    fun sumsAreExactToThePaiseAndAnExactCancelIsPlainZero() {
        val wallet = account(3, "Wallet", initial = 0.1)
        val balances = AccountMath.balances(
            listOf(wallet, account(4, "Cancelled", initial = 0.3)),
            listOf(
                txn(1, "Income", "2026-10-01", 0.2, accountId = 3),
                txn(2, "Expense", "2026-10-01", 0.3, accountId = 4)
            )
        )

        assertEquals("0.3", balances.getValue(3).toString())
        assertEquals("0.0", balances.getValue(4).toString())
    }

    @Test
    fun liabilitiesAreWhatIsOwedSoAnOverpaidCardLowersThem() {
        val card = account(2, "HDFC Millennia", group = "Credit card", liability = true, initial = -12_000.0)
        val overpaid = account(3, "ICICI Amazon", group = "Credit card", liability = true, initial = 500.0)
        val savings = account(4, "PPF", group = "Investments", initial = 1_00_000.0)

        val totals = AccountMath.totals(listOf(salary, card, overpaid, savings), listOf())

        assertEquals(1_10_000.0, totals.assets, 0.0)
        assertEquals(11_500.0, totals.liabilities, 0.0)
        assertEquals(98_500.0, totals.netWorth, 0.0)
    }

    @Test
    fun onlyAccountsIncludedInTotalsCountTowardsThem() {
        val mumsWallet = account(5, "Mum's wallet", initial = 5_000.0, included = false)
        val mumsCard = account(6, "Mum's card", group = "Credit card", liability = true, initial = -2_000.0, included = false)

        val totals = AccountMath.totals(listOf(salary, mumsWallet, mumsCard), listOf())

        assertEquals(Totals(assets = 10_000.0, liabilities = 0.0, netWorth = 10_000.0), totals)
    }

    @Test
    fun everyStatementMonthAddsUpAcrossTransfersAndAdjustments() {
        val months = AccountMath.statement(
            salary,
            listOf(
                txn(1, "Expense", "2026-09-30", 500.0, accountId = 1), // already in the Initial balance
                txn(2, "Income", "2026-10-01", 50_000.0, accountId = 1),
                txn(3, "Transfer", "2026-10-05", 12_000.0, fromAccountId = 1, toAccountId = 2),
                txn(4, "Expense", "2026-10-05", 1_250.5, accountId = 1),
                txn(5, "Adjustment", "2026-10-20", -30.0, accountId = 1),
                txn(6, "Transfer", "2026-11-02", 2_000.0, fromAccountId = 2, toAccountId = 1),
                txn(7, "Adjustment", "2026-11-03", 10.0, accountId = 1),
                txn(8, "Expense", "2026-11-03", 99.0, accountId = 2) // not this Account
            )
        )

        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10), YearMonth.of(2026, 9)), months.map { it.month })
        val (november, october, september) = months

        assertEquals(10_000.0, september.opening, 0.0)
        assertEquals(10_000.0, september.closing, 0.0)
        assertEquals(false, september.rows.single().counts)

        assertEquals(10_000.0, october.opening, 0.0)
        assertEquals(50_000.0, october.income, 0.0)
        assertEquals(12_000.0, october.transfersOut, 0.0)
        assertEquals(1_250.5, october.expenses, 0.0)
        assertEquals(30.0, october.adjustmentsOut, 0.0)
        assertEquals(50_000.0, october.moneyIn, 0.0)
        assertEquals(13_280.5, october.moneyOut, 0.0)
        assertEquals(46_719.5, october.closing, 0.0)

        assertEquals(46_719.5, november.opening, 0.0)
        assertEquals(2_000.0, november.transfersIn, 0.0)
        assertEquals(10.0, november.adjustmentsIn, 0.0)
        assertEquals(48_729.5, november.closing, 0.0)
        months.forEach { assertEquals(it.closing, it.opening + it.moneyIn - it.moneyOut, 0.001) }
    }

    @Test
    fun statementRowsAreNewestFirstWithTheirSignedAmountBalanceAfterAndOtherAccount() {
        val card = account(2, "HDFC Millennia", group = "Credit card", liability = true)
        val rows = AccountMath.statement(
            salary,
            listOf(
                txn(3, "Expense", "2026-10-05", 250.0, accountId = 1),
                txn(2, "Transfer", "2026-10-05", 1_000.0, fromAccountId = 1, toAccountId = 2),
                txn(1, "Income", "2026-10-01", 500.0, accountId = 1)
            )
        ).single().rows

        assertEquals(listOf(3L, 2L, 1L), rows.map { it.transactionId })
        assertEquals(listOf(-250.0, -1_000.0, 500.0), rows.map { it.amount })
        assertEquals(listOf(9_250.0, 9_500.0, 10_500.0), rows.map { it.balanceAfter })
        assertEquals(listOf(StatementRowKind.EXPENSE, StatementRowKind.TRANSFER_OUT, StatementRowKind.INCOME), rows.map { it.kind })
        assertEquals(card.id, rows[1].otherAccountId)
    }

    @Test
    fun aNegativeExpenseStillAddsUpAsMoneyIn() {
        val month = AccountMath.statement(
            salary,
            listOf(
                txn(1, "Expense", "2026-10-02", -400.0, accountId = 1),
                txn(2, "Income", "2026-10-03", -100.0, accountId = 1)
            )
        ).single()

        assertEquals(400.0, month.income, 0.0)
        assertEquals(100.0, month.expenses, 0.0)
        assertEquals(10_300.0, month.closing, 0.0)
    }

    private val card = account(2, "HDFC Millennia", group = "Credit card", liability = true, initial = -5_000.0, asOf = "2026-09-30")
    private val mumsWallet = account(5, "Mum's wallet", initial = 2_000.0, asOf = "2026-09-30", included = false)
    private val family = listOf(salary, card, mumsWallet)
    private val autumn = listOf(
        txn(1, "Income", "2026-10-01", 50_000.0, accountId = 1),
        txn(2, "Transfer", "2026-10-05", 5_000.0, fromAccountId = 1, toAccountId = 2), // between included Accounts
        txn(3, "Expense", "2026-10-09", 3_000.0, accountId = 2),
        txn(4, "Transfer", "2026-10-12", 1_000.0, fromAccountId = 1, toAccountId = 5), // leaves the totals
        txn(5, "Adjustment", "2026-10-20", -20.0, accountId = 1),
        txn(6, "Transfer", "2026-11-01", 500.0, fromAccountId = 5, toAccountId = 1), // comes back in
        txn(7, "Expense", "2026-11-02", 100.0, accountId = 5) // outside the totals entirely
    )

    @Test
    fun netWorthHistoryIsEachMonthsClosingFigure() {
        val history = AccountMath.netWorthHistory(family, autumn, listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)))

        assertEquals(listOf(5_000.0, 50_980.0, 51_480.0), history.map { it.netWorth })
    }

    @Test
    fun cashFlowLeavesOutTransfersBetweenIncludedAccountsButCountsOnesThatCrossTheBoundary() {
        val (october, november) = AccountMath.cashFlow(family, autumn, listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11)))

        assertEquals(CashFlowMonth(YearMonth.of(2026, 10), moneyIn = 50_000.0, moneyOut = 4_000.0, adjustments = -20.0), october)
        assertEquals(CashFlowMonth(YearMonth.of(2026, 11), moneyIn = 500.0, moneyOut = 0.0, adjustments = 0.0), november)
    }

    @Test
    fun moversAddUpToTheChangeInNetWorth() {
        val movers = AccountMath.movers(family, autumn, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))

        assertEquals(listOf(Mover(1, 43_980.0), Mover(2, 2_000.0)), movers)
        assertEquals(50_980.0 - 5_000.0, movers.sumOf { it.change }, 0.0)
    }

    @Test
    fun theReconcileDifferenceIsTheBankFigureMinusTheAppsToThePaise() {
        assertEquals(-0.3, AccountMath.reconcileDifference(bankBalance = 1_000.1, appBalance = 1_000.4), 0.0)
        assertEquals("0.0", AccountMath.reconcileDifference(bankBalance = 0.3, appBalance = 0.1 + 0.2).toString())
    }
}
