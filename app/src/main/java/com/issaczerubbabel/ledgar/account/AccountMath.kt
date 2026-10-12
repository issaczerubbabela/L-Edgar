package com.issaczerubbabel.ledgar.account

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

/** What [AccountMath] needs to know about an Account. */
data class AccountSnapshot(
    val id: Long,
    val name: String,
    val group: String,
    /** Whether the Account's group has the Liability role. */
    val isLiability: Boolean,
    val initialBalance: Double,
    /** The As-of date: the Initial balance is the balance at the end of this day. */
    val asOfDate: LocalDate,
    val includedInTotals: Boolean
)

/** What [AccountMath] needs to know about a Transaction. */
data class TransactionSnapshot(
    val id: Long,
    val type: String,
    val date: LocalDate,
    /** As stored; only an Adjustment's amount is signed. */
    val amount: Double,
    val accountId: Long?,
    val fromAccountId: Long?,
    val toAccountId: Long?
)

/** What a statement row is, as far as its own Account is concerned. */
enum class StatementRowKind { INCOME, EXPENSE, TRANSFER_IN, TRANSFER_OUT, ADJUSTMENT }

/** One Transaction on an Account's statement. */
data class StatementRow(
    val transactionId: Long,
    val date: LocalDate,
    val kind: StatementRowKind,
    /** The signed change to this Account. */
    val amount: Double,
    /** The balance once this row is applied; unchanged by a row that doesn't count. */
    val balanceAfter: Double,
    /** False when dated on or before the As-of date, so it's already in the Initial balance. */
    val counts: Boolean,
    /** For a Transfer, the Account on the other side, when known. */
    val otherAccountId: Long?
)

/** One month of an Account's statement, where [opening] + [moneyIn] − [moneyOut] = [closing]. */
data class StatementMonth(
    val month: YearMonth,
    val opening: Double,
    val income: Double,
    val transfersIn: Double,
    val adjustmentsIn: Double,
    val expenses: Double,
    val transfersOut: Double,
    val adjustmentsOut: Double,
    val closing: Double,
    /** Newest first. */
    val rows: List<StatementRow>
) {
    val moneyIn: Double get() = income + transfersIn + adjustmentsIn
    val moneyOut: Double get() = expenses + transfersOut + adjustmentsOut
}

/** Net worth at the end of [month]. */
data class NetWorthPoint(val month: YearMonth, val netWorth: Double)

/**
 * Money into and out of the Accounts Included in totals in [month], with Balance adjustments
 * apart. [moneyIn] − [moneyOut] + [adjustments] is the month's change in Net worth.
 */
data class CashFlowMonth(val month: YearMonth, val moneyIn: Double, val moneyOut: Double, val adjustments: Double)

/** How much one Account Included in totals changed over a period. */
data class Mover(val accountId: Long, val change: Double)

/** The Accounts tab's header figures, in rupees. */
data class Totals(val assets: Double, val liabilities: Double, val netWorth: Double)

/**
 * The only place Account balances, totals and statements are worked out (see Account, Initial
 * balance, Net worth and Cash flow in CONTEXT.md). Pure: no Room, no Android. Sums are whole paise
 * so they never drift, and converted back to rupees only on the way out.
 */
object AccountMath {

    /** Each Account's balance today: its Initial balance plus every Transaction dated after its As-of date. */
    fun balances(accounts: List<AccountSnapshot>, transactions: List<TransactionSnapshot>): Map<Long, Double> =
        balancesPaise(accounts, transactions).mapValues { rupees(it.value) }

    /**
     * Assets, Liabilities and Net worth over the Accounts Included in totals. Liabilities are minus
     * the sum of the Liability Accounts' balances, so Assets − Liabilities = Net worth always holds.
     */
    fun totals(accounts: List<AccountSnapshot>, transactions: List<TransactionSnapshot>): Totals =
        totalsPaise(accounts, balancesPaise(accounts, transactions)).toRupees()

    private fun totalsPaise(accounts: List<AccountSnapshot>, balances: Map<Long, Long>): TotalsPaise {
        val included = accounts.filter { it.includedInTotals }
        val assets = included.filterNot { it.isLiability }.sumOf { balances.getValue(it.id) }
        val liabilities = -included.filter { it.isLiability }.sumOf { balances.getValue(it.id) }
        return TotalsPaise(assets, liabilities)
    }

    private data class TotalsPaise(val assets: Long, val liabilities: Long) {
        val netWorth: Long get() = assets - liabilities
        fun toRupees() = Totals(rupees(assets), rupees(liabilities), rupees(netWorth))
    }

    /** Net worth at the end of each of [months]. */
    fun netWorthHistory(
        accounts: List<AccountSnapshot>,
        transactions: List<TransactionSnapshot>,
        months: List<YearMonth>
    ): List<NetWorthPoint> = months.map { month ->
        val upToMonthEnd = transactions.filter { !it.date.isAfter(month.atEndOfMonth()) }
        NetWorthPoint(month, rupees(totalsPaise(accounts, balancesPaise(accounts, upToMonthEnd)).netWorth))
    }

    /**
     * Cash flow for each of [months]. Each Transaction's counted effects on the Accounts Included in
     * totals are netted, so a Transfer between two of them is nothing, and one that crosses the
     * boundary is money in or out.
     */
    fun cashFlow(
        accounts: List<AccountSnapshot>,
        transactions: List<TransactionSnapshot>,
        months: List<YearMonth>
    ): List<CashFlowMonth> {
        val byId = accounts.associateBy { it.id }
        val byMonth = transactions.groupBy { YearMonth.from(it.date) }
        return months.map { month ->
            var moneyIn = 0L
            var moneyOut = 0L
            var adjustments = 0L
            byMonth[month].orEmpty().forEach { txn ->
                val net = countedEffects(txn, byId).filterKeys { byId.getValue(it).includedInTotals }.values.sum()
                when {
                    kindOf(txn.type) == Kind.ADJUSTMENT -> adjustments += net
                    net > 0 -> moneyIn += net
                    else -> moneyOut -= net
                }
            }
            CashFlowMonth(month, rupees(moneyIn), rupees(moneyOut), rupees(adjustments))
        }
    }

    /**
     * Each Account Included in totals that changed between [from] and [to] (both inclusive),
     * biggest change first. Together they add up to the change in Net worth.
     */
    fun movers(
        accounts: List<AccountSnapshot>,
        transactions: List<TransactionSnapshot>,
        from: LocalDate,
        to: LocalDate
    ): List<Mover> {
        val byId = accounts.associateBy { it.id }
        val changes = mutableMapOf<Long, Long>()
        transactions.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.forEach { txn ->
            countedEffects(txn, byId).forEach { (id, delta) ->
                if (byId.getValue(id).includedInTotals) changes[id] = (changes[id] ?: 0L) + delta
            }
        }
        return changes.filterValues { it != 0L }
            .entries.sortedByDescending { kotlin.math.abs(it.value) }
            .map { Mover(it.key, rupees(it.value)) }
    }

    /** What a Balance adjustment must add to bring [appBalance] to [bankBalance]. */
    fun reconcileDifference(bankBalance: Double, appBalance: Double): Double = rupees(paise(bankBalance) - paise(appBalance))

    /**
     * [account]'s statement, one section per month that has a Transaction on it, newest month and
     * row first. Transactions dated on or before the As-of date are listed but don't count.
     */
    fun statement(account: AccountSnapshot, transactions: List<TransactionSnapshot>): List<StatementMonth> {
        var balance = paise(account.initialBalance)
        val rows = transactions
            .mapNotNull { txn -> effects(txn)[account.id]?.let { txn to it } }
            .sortedWith(compareBy({ it.first.date }, { it.first.id }))
            .map { (txn, delta) ->
                val counts = counts(txn, account)
                if (counts) balance += delta
                RowPaise(txn, kindFor(txn, account.id, delta), delta, balance, counts)
            }
        var opening = paise(account.initialBalance)
        return rows.groupBy { YearMonth.from(it.txn.date) }.map { (month, monthRows) ->
            val counted = monthRows.filter { it.counts }
            fun sumOf(sign: Int, vararg kinds: StatementRowKind) = counted
                .filter { it.delta.sign() == sign && it.kind in kinds }
                .sumOf { kotlin.math.abs(it.delta) }
            val section = StatementMonth(
                month = month,
                opening = rupees(opening),
                // Grouped by sign first, so a negative Expense is money in and every month adds up.
                income = rupees(sumOf(1, StatementRowKind.INCOME, StatementRowKind.EXPENSE)),
                transfersIn = rupees(sumOf(1, StatementRowKind.TRANSFER_IN, StatementRowKind.TRANSFER_OUT)),
                adjustmentsIn = rupees(sumOf(1, StatementRowKind.ADJUSTMENT)),
                expenses = rupees(sumOf(-1, StatementRowKind.INCOME, StatementRowKind.EXPENSE)),
                transfersOut = rupees(sumOf(-1, StatementRowKind.TRANSFER_IN, StatementRowKind.TRANSFER_OUT)),
                adjustmentsOut = rupees(sumOf(-1, StatementRowKind.ADJUSTMENT)),
                closing = rupees(monthRows.last().balanceAfter),
                rows = monthRows.reversed().map { it.toRow() }
            )
            opening = monthRows.last().balanceAfter
            section
        }.reversed()
    }

    private class RowPaise(
        val txn: TransactionSnapshot,
        val kind: StatementRowKind,
        val delta: Long,
        val balanceAfter: Long,
        val counts: Boolean
    ) {
        fun toRow() = StatementRow(
            transactionId = txn.id,
            date = txn.date,
            kind = kind,
            amount = rupees(delta),
            balanceAfter = rupees(balanceAfter),
            counts = counts,
            otherAccountId = when (kind) {
                StatementRowKind.TRANSFER_IN -> txn.fromAccountId
                StatementRowKind.TRANSFER_OUT -> txn.toAccountId
                else -> null
            }
        )
    }

    private fun Long.sign(): Int = java.lang.Long.signum(this)

    private fun kindFor(txn: TransactionSnapshot, accountId: Long, delta: Long): StatementRowKind = when (kindOf(txn.type)) {
        Kind.INCOME -> StatementRowKind.INCOME
        Kind.EXPENSE -> StatementRowKind.EXPENSE
        Kind.ADJUSTMENT -> StatementRowKind.ADJUSTMENT
        else -> if (delta > 0 || (delta == 0L && txn.toAccountId == accountId)) StatementRowKind.TRANSFER_IN else StatementRowKind.TRANSFER_OUT
    }

    private fun balancesPaise(accounts: List<AccountSnapshot>, transactions: List<TransactionSnapshot>): Map<Long, Long> {
        val byId = accounts.associateBy { it.id }
        val totals = accounts.associate { it.id to paise(it.initialBalance) }.toMutableMap()
        transactions.forEach { txn ->
            countedEffects(txn, byId).forEach { (accountId, delta) -> totals[accountId] = totals.getValue(accountId) + delta }
        }
        return totals
    }

    /**
     * How much [txn] changes each Account it touches, in paise, before the As-of rule. Income goes
     * to `toAccountId ?: accountId`, an Expense comes from `fromAccountId ?: accountId`, a Transfer
     * moves money from `fromAccountId` to `toAccountId`, and an Adjustment adds its signed amount to
     * `accountId`. Any other type changes nothing.
     */
    fun effects(txn: TransactionSnapshot): Map<Long, Long> {
        val amount = paise(txn.amount)
        val effects = mutableMapOf<Long, Long>()
        fun add(accountId: Long?, delta: Long) {
            if (accountId != null) effects[accountId] = (effects[accountId] ?: 0L) + delta
        }
        when (kindOf(txn.type)) {
            Kind.INCOME -> add(txn.toAccountId ?: txn.accountId, amount)
            Kind.EXPENSE -> add(txn.fromAccountId ?: txn.accountId, -amount)
            Kind.TRANSFER -> {
                add(txn.fromAccountId, -amount)
                add(txn.toAccountId, amount)
            }
            Kind.ADJUSTMENT -> add(txn.accountId, amount)
            null -> Unit
        }
        return effects
    }

    /** [effects] limited to known Accounts whose As-of date [txn] is after. */
    private fun countedEffects(txn: TransactionSnapshot, accounts: Map<Long, AccountSnapshot>): Map<Long, Long> =
        effects(txn).filterKeys { id -> accounts[id]?.let { counts(txn, it) } == true }

    /** Whether [txn] moves [account]'s balance: only when dated after its As-of date (day precision). */
    fun counts(txn: TransactionSnapshot, account: AccountSnapshot): Boolean = txn.date.isAfter(account.asOfDate)

    private enum class Kind { INCOME, EXPENSE, TRANSFER, ADJUSTMENT }

    private fun kindOf(type: String): Kind? = when (type.trim().lowercase()) {
        "income" -> Kind.INCOME
        "expense" -> Kind.EXPENSE
        "transfer" -> Kind.TRANSFER
        "adjustment" -> Kind.ADJUSTMENT
        else -> null
    }

    internal fun paise(rupees: Double): Long = (rupees * 100).roundToLong()

    /** Never returns -0.0. */
    internal fun rupees(paise: Long): Double = paise / 100.0
}
