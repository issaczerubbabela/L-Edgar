package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.account.AccountMath
import com.issaczerubbabel.ledgar.data.repository.AccountBook
import com.issaczerubbabel.ledgar.util.formatMoney
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How far back the Net worth screen looks. */
enum class NetWorthPeriod(val label: String, val months: Int) {
    SIX_MONTHS("6M", 6),
    ONE_YEAR("1Y", 12),
    ALL("All", 240)
}

data class NetWorthMonthUi(
    val month: YearMonth,
    /** "Oct". */
    val shortLabel: String,
    /** "Oct 2026". */
    val label: String,
    val netWorth: Double,
    val netWorthText: String,
    /** "+₹6,420.00 vs Sep"; null for the first month shown. */
    val change: String?
)

data class CashFlowMonthUi(
    val shortLabel: String,
    val moneyIn: Double,
    val moneyOut: Double,
    val moneyInText: String,
    val moneyOutText: String,
    /** "adj +₹340.00"; null when the month had no Balance adjustment. */
    val adjustments: String?,
    /** In − Out + adjustments: the month's change in Net worth. */
    val net: String,
    val isNetNegative: Boolean
)

/** One Account group's part of Assets or Liabilities. */
data class ShareUi(val name: String, val amount: String, val fraction: Float, val percent: String)

data class MoverUi(val name: String, val change: String, val isNegative: Boolean)

data class NetWorthUiState(
    val netWorth: String = formatMoney(0.0),
    val monthChange: String = "",
    val isMonthChangeNegative: Boolean = false,
    val period: NetWorthPeriod = NetWorthPeriod.SIX_MONTHS,
    /** Oldest first, never before the first month with data. */
    val history: List<NetWorthMonthUi> = emptyList(),
    /** Fewer than 4 months is shown as figures, not a line. */
    val showHistoryChart: Boolean = false,
    val historySummary: String = "",
    /** Oldest first. */
    val cashFlow: List<CashFlowMonthUi> = emptyList(),
    val cashFlowSummary: String = "",
    val assetsTotal: String = formatMoney(0.0),
    val liabilitiesTotal: String = formatMoney(0.0),
    val assetShares: List<ShareUi> = emptyList(),
    val liabilityShares: List<ShareUi> = emptyList(),
    /** Biggest change first, at most [MAX_MOVERS]. */
    val movers: List<MoverUi> = emptyList(),
    /** "Change since 30 Sep. Together: +₹6,420.00". */
    val moversNote: String = "",
    val isLoaded: Boolean = false
) {
    companion object {
        const val MAX_MOVERS = 5
        const val MIN_CHART_MONTHS = 4
    }
}

private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val longMonth = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** Ignored when looking for the first month with data: the As-of date an Account gets when none was set. */
private val NO_AS_OF_DATE = LocalDate.of(1971, 1, 1)

/** The Net worth screen for [book] on [today], over [period]. Every figure comes from [AccountMath]. */
fun netWorthScreen(book: AccountBook, today: LocalDate, period: NetWorthPeriod): NetWorthUiState {
    val thisMonth = YearMonth.from(today)
    val firstDataMonth = (book.transactions.map { it.date } + book.accounts.map { it.asOfDate }.filter { it.isAfter(NO_AS_OF_DATE) })
        .minOrNull()?.let(YearMonth::from)?.coerceAtMost(thisMonth) ?: thisMonth
    val months = monthsBack(thisMonth, firstDataMonth, period.months)

    val totals = AccountMath.totals(book.accounts, book.transactions)
    val lastMonthEnd = AccountMath.netWorthHistory(book.accounts, book.transactions, listOf(thisMonth.minusMonths(1))).single().netWorth
    val change = totals.netWorth - lastMonthEnd

    val points = AccountMath.netWorthHistory(book.accounts, book.transactions, months)
    val history = points.mapIndexed { i, point ->
        NetWorthMonthUi(
            month = point.month,
            shortLabel = point.month.format(shortMonth),
            label = point.month.format(longMonth),
            netWorth = point.netWorth,
            netWorthText = formatMoney(point.netWorth),
            change = points.getOrNull(i - 1)?.let { previous ->
                "${formatMoney(point.netWorth - previous.netWorth, signed = true)} vs ${previous.month.format(shortMonth)}"
            }
        )
    }

    // Cash flow bars stay readable: at most a year of them.
    val flowMonths = months.takeLast(12)
    val flows = AccountMath.cashFlow(book.accounts, book.transactions, flowMonths).map { flow ->
        val net = flow.moneyIn - flow.moneyOut + flow.adjustments
        CashFlowMonthUi(
            shortLabel = flow.month.format(shortMonth),
            moneyIn = flow.moneyIn,
            moneyOut = flow.moneyOut,
            moneyInText = formatMoney(flow.moneyIn),
            moneyOutText = formatMoney(flow.moneyOut),
            adjustments = if (AccountMath.paise(flow.adjustments) == 0L) null else "adj ${formatMoney(flow.adjustments, signed = true)}",
            net = formatMoney(net, signed = true),
            isNetNegative = AccountMath.paise(net) < 0
        )
    }
    val latestFlow = AccountMath.cashFlow(book.accounts, book.transactions, listOf(thisMonth)).single()

    val balances = AccountMath.balances(book.accounts, book.transactions)
    val included = book.accounts.filter { it.includedInTotals }
    fun shares(isLiability: Boolean): List<ShareUi> {
        val byGroup = included.filter { it.isLiability == isLiability }
            .groupBy { it.group }
            .mapValues { (_, accounts) ->
                val sum = accounts.sumOf { AccountMath.paise(balances.getValue(it.id)) }
                if (isLiability) -sum else sum
            }
            .filterValues { it > 0L }
        val whole = byGroup.values.sum().takeIf { it > 0L } ?: return emptyList()
        return byGroup.entries.sortedByDescending { it.value }.map { (group, paise) ->
            val fraction = paise.toFloat() / whole
            ShareUi(group, formatMoney(AccountMath.rupees(paise)), fraction, String.format(Locale.ENGLISH, "%.1f%%", fraction * 100))
        }
    }

    val names = book.accounts.associate { it.id to it.name }
    val allMovers = AccountMath.movers(book.accounts, book.transactions, from = thisMonth.atDay(1), to = today)
    val moversTotal = AccountMath.rupees(allMovers.sumOf { AccountMath.paise(it.change) })

    return NetWorthUiState(
        netWorth = formatMoney(totals.netWorth),
        monthChange = "${formatMoney(change, signed = true)} since ${thisMonth.atDay(1).format(dayMonth)}",
        isMonthChangeNegative = AccountMath.paise(change) < 0,
        period = period,
        history = history,
        showHistoryChart = history.size >= NetWorthUiState.MIN_CHART_MONTHS,
        historySummary = history.firstOrNull()?.let { first ->
            val last = history.last()
            if (first === last) "Net worth is ${last.netWorthText} in ${last.label}."
            else "Net worth went from ${first.netWorthText} in ${first.label} to ${last.netWorthText} in ${last.label}."
        }.orEmpty(),
        cashFlow = flows,
        cashFlowSummary = "${thisMonth.format(longMonth)} so far: in ${formatMoney(latestFlow.moneyIn)}, out ${formatMoney(latestFlow.moneyOut)}" +
            (if (AccountMath.paise(latestFlow.adjustments) != 0L) ", adjustments ${formatMoney(latestFlow.adjustments, signed = true)}" else "") + ".",
        assetsTotal = formatMoney(totals.assets),
        liabilitiesTotal = formatMoney(totals.liabilities),
        assetShares = shares(isLiability = false),
        liabilityShares = shares(isLiability = true),
        movers = allMovers.take(NetWorthUiState.MAX_MOVERS).map { mover ->
            MoverUi(names[mover.accountId].orEmpty(), formatMoney(mover.change, signed = true), AccountMath.paise(mover.change) < 0)
        },
        moversNote = "Change since ${thisMonth.minusMonths(1).atEndOfMonth().format(dayMonth)}. Together: ${formatMoney(moversTotal, signed = true)}",
        isLoaded = true
    )
}

/** Up to [count] months ending with [last], never before [first]. */
private fun monthsBack(last: YearMonth, first: YearMonth, count: Int): List<YearMonth> {
    val months = ArrayList<YearMonth>()
    var month = last
    while (!month.isBefore(first) && months.size < count) {
        months += month
        month = month.minusMonths(1)
    }
    return months.reversed()
}
