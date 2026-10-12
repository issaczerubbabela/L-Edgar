package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.account.AccountMath
import com.issaczerubbabel.ledgar.account.Reconcile
import com.issaczerubbabel.ledgar.account.StatementMonth
import com.issaczerubbabel.ledgar.account.StatementRow
import com.issaczerubbabel.ledgar.account.StatementRowKind
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountBook
import com.issaczerubbabel.ledgar.ledger.Ledger
import com.issaczerubbabel.ledgar.ledger.LedgerRow
import com.issaczerubbabel.ledgar.util.formatListMoney
import com.issaczerubbabel.ledgar.util.formatMoney
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One row of an Account's statement: the shared Transaction row, plus its date and the balance after it. */
data class StatementRowUi(
    val transaction: ExpenseRecord,
    val kind: StatementRowKind,
    val row: LedgerRow,
    /** "2 Oct · Bal ₹97,110", or "30 Sep · Not counted" before the As-of date. */
    val trailingLine: String,
    val counts: Boolean
) {
    val transactionId: Long get() = transaction.id
}

/** One month of the statement, where Opening + In − Out = Closing. */
data class StatementMonthUi(
    val key: String,
    /** "October 2026". */
    val label: String,
    val opening: String,
    val moneyIn: String,
    val moneyOut: String,
    val closing: String,
    /** "Income ₹3,500.00 · Transfers ₹40,000.00 · Adjustments ₹340.00", only the parts that aren't zero. */
    val inSplit: String,
    val outSplit: String,
    val rows: List<StatementRowUi>
)

data class AccountPageUiState(
    val accountId: Long = -1,
    val accountName: String = "",
    val groupName: String = "",
    val balanceToday: String = "",
    val isBalanceNegative: Boolean = false,
    /** "Balance at end of 31 Aug 2026 was ₹12,000.00". */
    val initialBalanceNote: String = "",
    /** "Reconciled 12 days ago", or "Counted…" for Cash. */
    val lastChecked: String = "",
    /** Newest month first. */
    val months: List<StatementMonthUi> = emptyList(),
    val isLoaded: Boolean = false
)

private val monthLabel = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val dayLabel = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val longDateLabel = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** The account page for [accountId], from [AccountMath]'s statement. Null when the Account no longer exists. */
internal fun accountPage(book: AccountBook, accountId: Long, today: LocalDate = LocalDate.now()): AccountPageUiState? {
    val account = book.accounts.firstOrNull { it.id == accountId } ?: return null
    val record = book.records.firstOrNull { it.id == accountId }
    val recordsById = book.transactionRecords.associateBy { it.id }
    val balance = AccountMath.balances(book.accounts, book.transactions)[accountId] ?: account.initialBalance

    fun rowUi(row: StatementRow): StatementRowUi? {
        // Every statement row comes from one of the book's Transactions.
        val txn = recordsById[row.transactionId] ?: return null
        val balance = if (row.counts) "Bal ${formatListMoney(row.balanceAfter)}" else "Not counted"
        return StatementRowUi(
            transaction = txn,
            kind = row.kind,
            row = Ledger.row(txn, book.records),
            trailingLine = "${row.date.format(dayLabel)} · $balance",
            counts = row.counts
        )
    }

    fun split(vararg parts: Pair<String, Double>): String =
        parts.filter { (_, amount) -> amount != 0.0 }
            .joinToString(" · ") { (label, amount) -> "$label ${formatMoney(amount)}" }

    fun monthUi(month: StatementMonth) = StatementMonthUi(
        key = month.month.toString(),
        label = month.month.format(monthLabel),
        opening = formatMoney(month.opening),
        moneyIn = formatMoney(month.moneyIn),
        moneyOut = formatMoney(month.moneyOut),
        closing = formatMoney(month.closing),
        inSplit = split("Income" to month.income, "Transfers" to month.transfersIn, "Adjustments" to month.adjustmentsIn),
        outSplit = split("Expenses" to month.expenses, "Transfers" to month.transfersOut, "Adjustments" to month.adjustmentsOut),
        rows = month.rows.mapNotNull(::rowUi)
    )

    return AccountPageUiState(
        accountId = accountId,
        accountName = account.name,
        groupName = account.group,
        balanceToday = formatMoney(balance),
        isBalanceNegative = AccountMath.paise(balance) < 0,
        initialBalanceNote = "Balance at end of ${account.asOfDate.format(longDateLabel)} was ${formatMoney(account.initialBalance)}"
            .takeIf { record != null }.orEmpty(),
        lastChecked = record?.let { Reconcile.lastCheckedLabel(it.reconciledAt?.let(::parseFlexibleDate), account.group, today) }.orEmpty(),
        months = AccountMath.statement(account, book.transactions).map(::monthUi),
        isLoaded = true
    )
}
