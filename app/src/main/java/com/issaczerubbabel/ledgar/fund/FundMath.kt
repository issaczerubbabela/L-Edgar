package com.issaczerubbabel.ledgar.fund

import com.issaczerubbabel.ledgar.account.AccountMath
import com.issaczerubbabel.ledgar.account.AccountSnapshot
import com.issaczerubbabel.ledgar.account.TransactionSnapshot
import com.issaczerubbabel.ledgar.data.bucket.CycleCalendar
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.Fund
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.FundPlan
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import java.time.LocalDate

/** Every Fund, Holding and Planned contribution, read together. */
data class FundsSnapshot(
    val funds: List<Fund> = emptyList(),
    val holdings: List<FundHolding> = emptyList(),
    val plans: List<FundPlan> = emptyList()
) {
    /** Funds not archived. An archived Fund keeps its history but holds nothing and plans nothing. */
    val activeFunds: List<Fund> get() = funds.filterNot { it.isArchived }

    /** The holdings of [activeFunds]: the ones every current figure uses. */
    val activeHoldings: List<FundHolding>
        get() {
            val active = activeFunds.mapTo(HashSet()) { it.id }
            return holdings.filter { it.fundId in active }
        }

    /** [cycleId]'s Planned contributions to [activeFunds]. */
    fun plansFor(cycleId: Long): List<FundPlan> {
        val active = activeFunds.mapTo(HashSet()) { it.id }
        return plans.filter { it.cycleId == cycleId && it.fundId in active }
    }

    val isEmpty: Boolean get() = funds.isEmpty()

    companion object {
        val EMPTY = FundsSnapshot()
    }
}

/** Money moved into (Set aside) or out of (drawn) Funds, per Fund id. */
data class FundFlow(val byFund: Map<Long, Double>) {
    val total: Double get() = AccountMath.rupees(byFund.values.sumOf { AccountMath.paise(it) })

    companion object {
        val NONE = FundFlow(emptyMap())
    }
}

/** Your money (CONTEXT.md), and the holdings it can't see because their Account isn't Included in totals. */
data class YourMoney(val total: Double, val excludedHoldings: List<FundHolding>)

/**
 * The only place Fund figures are worked out (Fund, Holding, Set aside, Free and Your money in
 * CONTEXT.md; ADR-0011). Pure: no Room, no Android, and the date comes in as `today`. Callers pass
 * the holdings that count, normally [FundsSnapshot.activeHoldings], and Account balances from
 * [AccountMath.balances]. Sums are whole paise, like AccountMath.
 */
object FundMath {

    /**
     * Which Fund holds each Account whole, by Account id. The database keeps an Account the whole
     * holding of one Fund at most; if a hand-edited backup ever breaks that, the lowest holding id keeps it.
     */
    fun wholeAccounts(holdings: List<FundHolding>): Map<Long, Long> =
        wholeHoldings(holdings).mapValues { it.value.fundId }

    /** A WHOLE holding's value is its Account's balance; a FIXED one's is its amount. */
    fun holdingValue(holding: FundHolding, balances: Map<Long, Double>): Double = when (holding.holdingKind) {
        HoldingKind.WHOLE -> balances[holding.accountId] ?: 0.0
        HoldingKind.FIXED -> holding.amount
    }

    /** Each Fund's balance, by Fund id: the sum of its holdings' values. */
    fun fundBalances(holdings: List<FundHolding>, balances: Map<Long, Double>): Map<Long, Double> {
        val counted = countedHoldings(holdings)
        val totals = LinkedHashMap<Long, Long>()
        holdings.forEach { holding ->
            val value = if (holding.id in counted) AccountMath.paise(holdingValue(holding, balances)) else 0L
            totals[holding.fundId] = (totals[holding.fundId] ?: 0L) + value
        }
        return totals.mapValues { AccountMath.rupees(it.value) }
    }

    fun fundBalance(fundId: Long, holdings: List<FundHolding>, balances: Map<Long, Double>): Double =
        fundBalances(holdings.filter { it.fundId == fundId }, balances)[fundId] ?: 0.0

    /** Every Fund's balance together: the "Funds" part of Your money. */
    fun fundsTotal(holdings: List<FundHolding>, balances: Map<Long, Double>): Double =
        AccountMath.rupees(fundBalances(holdings, balances).values.sumOf { AccountMath.paise(it) })

    /**
     * [cycle]'s Set aside, by Fund: Transfers in the cycle's window (see [CycleCalendar.countsUntil])
     * from an Account no Fund holds whole into an Account a Fund does. A Transfer between two Fund
     * Accounts is a move, and Income, Adjustments and interest are never Set aside. A holding only
     * counts from its start date.
     */
    fun setAside(cycle: BudgetCycle, transactions: List<TransactionSnapshot>, holdings: List<FundHolding>, today: LocalDate): FundFlow =
        flow(cycle, transactions, today) { setAsideInto(it, holdings) }

    /** The Fund a Transaction is Set aside into, or null when it isn't Set aside. */
    fun setAsideInto(txn: TransactionSnapshot, holdings: List<FundHolding>): Long? {
        if (!txn.type.trim().equals("transfer", ignoreCase = true)) return null
        val from = txn.fromAccountId ?: return null
        val to = txn.toAccountId ?: return null
        val whole = wholeHoldingsOn(holdings, txn.date)
        if (from in whole) return null
        return whole[to]?.fundId
    }

    /** The Fund holding [accountId] whole on [date] (from that holding's start date), or null. */
    fun heldWholeOn(accountId: Long, date: LocalDate, holdings: List<FundHolding>): Long? =
        wholeHoldingsOn(holdings, date)[accountId]?.fundId

    /** [cycle]'s Expenses paid from an Account a Fund holds whole, by Fund. They don't count in the cycle. */
    fun drawn(cycle: BudgetCycle, transactions: List<TransactionSnapshot>, holdings: List<FundHolding>, today: LocalDate): FundFlow =
        flow(cycle, transactions, today) { drawnFrom(it, holdings) }

    /**
     * The Fund an Expense is drawn from: the one holding its Account (`fromAccountId ?: accountId`,
     * as in [AccountMath.effects]) whole, from that holding's start date. Null for anything else.
     */
    fun drawnFrom(txn: TransactionSnapshot, holdings: List<FundHolding>): Long? {
        if (!txn.type.trim().equals("expense", ignoreCase = true)) return null
        val account = txn.fromAccountId ?: txn.accountId ?: return null
        return wholeHoldingsOn(holdings, txn.date)[account]?.fundId
    }

    /** Net worth over the Accounts Included in totals, flagging holdings in Accounts that aren't. */
    fun yourMoney(accounts: List<AccountSnapshot>, transactions: List<TransactionSnapshot>, holdings: List<FundHolding>): YourMoney {
        val included = accounts.filter { it.includedInTotals }.mapTo(HashSet()) { it.id }
        return YourMoney(
            total = AccountMath.totals(accounts, transactions).netWorth,
            excludedHoldings = holdings.filter { it.accountId !in included }
        )
    }

    /**
     * Free = Your money − Funds − max(This cycle, 0). Moving money into a Fund leaves it unchanged,
     * and an overspent cycle never makes it look larger. It can go below zero.
     */
    fun free(yourMoney: Double, funds: Double, thisCycle: Double): Double =
        AccountMath.rupees(AccountMath.paise(yourMoney) - AccountMath.paise(funds) - AccountMath.paise(thisCycle).coerceAtLeast(0L))

    /** By Account id, how far the FIXED holdings in an Account are above its balance. Only short Accounts are listed. */
    fun shortfalls(holdings: List<FundHolding>, balances: Map<Long, Double>): Map<Long, Double> =
        fixedByAccount(holdings)
            .mapValues { (accountId, fixed) -> fixed - AccountMath.paise(balances[accountId] ?: 0.0) }
            .filterValues { it > 0L }
            .mapValues { AccountMath.rupees(it.value) }

    /**
     * Each of [accountIds]'s Free: its balance less the FIXED holdings in it, or 0 for an Account a
     * Fund holds whole. Negative when the holdings are short.
     */
    fun accountFree(accountIds: List<Long>, holdings: List<FundHolding>, balances: Map<Long, Double>): Map<Long, Double> {
        val whole = wholeAccounts(holdings)
        val fixed = fixedByAccount(holdings)
        return accountIds.associateWith { id ->
            if (id in whole) 0.0
            else AccountMath.rupees(AccountMath.paise(balances[id] ?: 0.0) - (fixed[id] ?: 0L))
        }
    }

    private fun flow(
        cycle: BudgetCycle,
        transactions: List<TransactionSnapshot>,
        today: LocalDate,
        fundOf: (TransactionSnapshot) -> Long?
    ): FundFlow {
        val start = LocalDate.parse(cycle.startDate)
        val until = CycleCalendar.countsUntil(LocalDate.parse(cycle.endDate), today, cycle.closedAt != null)
        val totals = LinkedHashMap<Long, Long>()
        transactions.forEach { txn ->
            if (txn.date.isBefore(start) || txn.date.isAfter(until)) return@forEach
            val fundId = fundOf(txn) ?: return@forEach
            totals[fundId] = (totals[fundId] ?: 0L) + AccountMath.paise(txn.amount)
        }
        return FundFlow(totals.mapValues { AccountMath.rupees(it.value) })
    }

    private fun wholeHoldings(holdings: List<FundHolding>): Map<Long, FundHolding> {
        val byAccount = LinkedHashMap<Long, FundHolding>()
        holdings.filter { it.holdingKind == HoldingKind.WHOLE }
            .sortedBy { it.id }
            .forEach { byAccount.putIfAbsent(it.accountId, it) }
        return byAccount
    }

    /** The WHOLE holdings that had started by [date], by Account id. */
    private fun wholeHoldingsOn(holdings: List<FundHolding>, date: LocalDate): Map<Long, FundHolding> =
        wholeHoldings(holdings).filterValues { holding -> startOf(holding)?.isAfter(date) != true }

    /** Holdings whose value counts: every FIXED one, and the WHOLE one that keeps each Account. */
    private fun countedHoldings(holdings: List<FundHolding>): Set<Long> {
        val keptWhole = wholeHoldings(holdings).values.mapTo(HashSet()) { it.id }
        return holdings.filter { it.holdingKind == HoldingKind.FIXED || it.id in keptWhole }.mapTo(HashSet()) { it.id }
    }

    private fun fixedByAccount(holdings: List<FundHolding>): Map<Long, Long> =
        holdings.filter { it.holdingKind == HoldingKind.FIXED }
            .groupBy { it.accountId }
            .mapValues { (_, fixed) -> fixed.sumOf { AccountMath.paise(it.amount) } }

    /** An unreadable start date counts as always started. */
    private fun startOf(holding: FundHolding): LocalDate? = runCatching { LocalDate.parse(holding.startDate) }.getOrNull()
}
