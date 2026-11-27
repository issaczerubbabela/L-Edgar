package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.account.AccountMath
import com.issaczerubbabel.ledgar.data.bucket.CycleCalendar
import com.issaczerubbabel.ledgar.data.bucket.CycleSummary
import com.issaczerubbabel.ledgar.data.bucket.CycleSummaryBuilder
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import com.issaczerubbabel.ledgar.data.repository.AccountBook
import com.issaczerubbabel.ledgar.fund.FundMath
import com.issaczerubbabel.ledgar.fund.FundsSnapshot
import com.issaczerubbabel.ledgar.util.formatRupees
import java.time.LocalDate

data class BudgetUiState(
    val isLoading: Boolean = true,
    val summary: CycleSummary? = null,
    /** 0 is the newest cycle; higher numbers step back through history. */
    val cycleIndex: Int = 0,
    val cycleCount: Int = 0,
    /** Whether any Fund is active. Without one the Budget tab looks exactly as it did before Funds. */
    val hasFunds: Boolean = false,
    /** The Your money card; only for the running cycle, and only with Funds. */
    val yourMoney: YourMoneySplit? = null,
    /** Transfers this cycle into a Savings-group Account no Fund holds: shown as "Set aside, no Fund". */
    val setAsideNoFund: Double = 0.0
) {
    val hasCycles: Boolean get() = cycleCount > 0
    val canGoOlder: Boolean get() = cycleIndex < cycleCount - 1
    val canGoNewer: Boolean get() = cycleIndex > 0

    /** A closed cycle is history: frozen numbers, no pace tick, no actions. */
    val isRunning: Boolean get() = summary?.cycle?.closedAt == null
}

/** One Fund's part of an Account. */
data class FundPart(val fundName: String, val amount: Double)

/** One Account Included in totals on the Your money card's per-Account split. */
data class AccountSplit(
    val accountId: Long,
    val name: String,
    val balance: Double,
    /** What each Fund holds here: the whole balance, or a fixed amount. */
    val funds: List<FundPart>,
    /** The balance no Fund holds; negative when fixed holdings are larger than the balance. */
    val rest: Double,
    /** How far the fixed holdings here are above the balance; 0 when they fit. */
    val shortBy: Double
)

/**
 * Your money (CONTEXT.md) = [funds] + [thisCycle] + [free]. [thisCycle] is what the running cycle
 * still has left, never below zero, so an overspent cycle never makes Free look larger.
 */
data class YourMoneySplit(
    val total: Double,
    val funds: Double,
    val thisCycle: Double,
    val free: Double,
    val accounts: List<AccountSplit>,
    /** Fund holdings in Accounts not Included in totals: left out of Your money, [funds] and [free]. */
    val holdingsOutsideTotals: Int
) {
    val isFreeNegative: Boolean get() = free < 0

    val overPlannedText: String get() = "Your plan uses ${formatRupees(-free)} more than you have"

    /** What TalkBack reads for the whole card. */
    val spokenSummary: String
        get() = buildString {
            append("Your money ").append(formatRupees(total))
            append(": Funds ").append(formatRupees(funds))
            append(", This cycle ").append(formatRupees(thisCycle)).append(" left")
            append(" and Free ").append(formatRupees(free))
            if (isFreeNegative) append(". ").append(overPlannedText)
        }
}

/**
 * The Budget tab for [cycle], worked out the same way everywhere a cycle is shown (ADR-0011). Pure:
 * no Room, no Android. With no active Fund it's the state the tab had before Funds.
 */
fun budgetHome(
    cycle: BudgetCycle,
    cycleIndex: Int,
    cycleCount: Int,
    buckets: List<BudgetBucket>,
    assignments: List<BucketCategory>,
    book: AccountBook,
    funds: FundsSnapshot,
    roles: StatsRoles,
    today: LocalDate
): BudgetUiState {
    val summary = CycleSummaryBuilder.build(cycle, buckets, assignments, book.transactionRecords, today, funds)
    val base = BudgetUiState(isLoading = false, summary = summary, cycleIndex = cycleIndex, cycleCount = cycleCount)
    if (funds.activeFunds.isEmpty()) return base

    val holdings = funds.activeHoldings
    return base.copy(
        hasFunds = true,
        yourMoney = if (cycle.closedAt == null) yourMoney(book, funds, summary.leftToSpend) else null,
        setAsideNoFund = setAsideNoFund(cycle, book, holdings, roles, today)
    )
}

private fun yourMoney(book: AccountBook, funds: FundsSnapshot, leftToSpend: Double): YourMoneySplit {
    val holdings = funds.activeHoldings
    val balances = AccountMath.balances(book.accounts, book.transactions)
    val money = FundMath.yourMoney(book.accounts, book.transactions, holdings)
    val thisCycle = leftToSpend.coerceAtLeast(0.0)

    val fundNames = funds.activeFunds.associate { it.id to it.name }
    val wholeBy = FundMath.wholeAccounts(holdings)
    val shortfalls = FundMath.shortfalls(holdings, balances)
    val included = book.records.filter { record ->
        record.includeInTotals && !(record.isHidden && (balances[record.id] ?: 0.0) == 0.0)
    }
    val free = FundMath.accountFree(included.map { it.id }, holdings, balances)
    val accounts = included.map { record ->
        val balance = balances[record.id] ?: 0.0
        val parts = wholeBy[record.id]?.let { fundId -> listOf(FundPart(fundNames[fundId].orEmpty(), balance)) }
            ?: holdings.filter { it.accountId == record.id && it.holdingKind == HoldingKind.FIXED }
                .map { FundPart(fundNames[it.fundId].orEmpty(), it.amount) }
        AccountSplit(
            accountId = record.id,
            name = record.accountName,
            balance = balance,
            funds = parts,
            rest = free[record.id] ?: balance,
            shortBy = shortfalls[record.id] ?: 0.0
        )
    }

    return YourMoneySplit(
        total = money.total,
        funds = money.funds,
        thisCycle = thisCycle,
        free = FundMath.free(money.total, money.funds, thisCycle),
        accounts = accounts,
        holdingsOutsideTotals = money.excludedHoldings.size
    )
}

/**
 * Transfers in [cycle]'s window into a Savings-group Account (ADR-0004) from one outside that
 * group, when no Fund holds either side whole. They aren't Set aside, so Left to spend keeps them.
 */
internal fun setAsideNoFund(
    cycle: BudgetCycle,
    book: AccountBook,
    holdings: List<FundHolding>,
    roles: StatsRoles,
    today: LocalDate
): Double {
    val start = LocalDate.parse(cycle.startDate)
    val until = CycleCalendar.countsUntil(LocalDate.parse(cycle.endDate), today, cycle.closedAt != null)
    val groupOf = book.records.associate { it.id to it.groupName }
    var total = 0L
    book.transactions.forEach { txn ->
        if (!txn.type.trim().equals("transfer", ignoreCase = true)) return@forEach
        if (txn.date.isBefore(start) || txn.date.isAfter(until)) return@forEach
        val from = txn.fromAccountId ?: return@forEach
        val to = txn.toAccountId ?: return@forEach
        if (!roles.isSavingsGroup(groupOf[to]) || roles.isSavingsGroup(groupOf[from])) return@forEach
        if (FundMath.heldWholeOn(to, txn.date, holdings) != null || FundMath.heldWholeOn(from, txn.date, holdings) != null) return@forEach
        total += AccountMath.paise(txn.amount)
    }
    return AccountMath.rupees(total)
}
