package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.account.AccountMath
import com.issaczerubbabel.ledgar.account.StatementMonth
import com.issaczerubbabel.ledgar.account.StatementRow
import com.issaczerubbabel.ledgar.account.StatementRowKind
import com.issaczerubbabel.ledgar.data.repository.AccountBook
import com.issaczerubbabel.ledgar.util.formatMoney
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One row of an Account's statement, ready to show. */
data class StatementRowUi(
    val transactionId: Long,
    val kind: StatementRowKind,
    /** "Rent", "→ HDFC Millennia", "Balance adjustment". */
    val title: String,
    /** "2 Oct · October rent", or why the row doesn't count. */
    val subtitle: String,
    /** Signed, e.g. "−₹22,000.00". */
    val amount: String,
    val isMoneyIn: Boolean,
    /** "Bal ₹97,110.00"; blank when the row doesn't count. */
    val balanceAfter: String,
    val counts: Boolean
)

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
    /** Newest month first. */
    val months: List<StatementMonthUi> = emptyList(),
    val isLoaded: Boolean = false
)

private val monthLabel = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val dayLabel = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val longDateLabel = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** The account page for [accountId], from [AccountMath]'s statement. Null when the Account no longer exists. */
internal fun accountPage(book: AccountBook, accountId: Long): AccountPageUiState? {
    val account = book.accounts.firstOrNull { it.id == accountId } ?: return null
    val record = book.records.firstOrNull { it.id == accountId }
    val namesById = book.accounts.associate { it.id to it.name }
    val recordsById = book.transactionRecords.associateBy { it.id }
    val balance = AccountMath.balances(book.accounts, book.transactions)[accountId] ?: account.initialBalance

    fun rowUi(row: StatementRow): StatementRowUi {
        val txn = recordsById[row.transactionId]
        val other = row.otherAccountId?.let(namesById::get) ?: txn?.toAccountName?.takeIf { it.isNotBlank() }
        val title = when (row.kind) {
            StatementRowKind.TRANSFER_IN -> "← ${other ?: "Another account"}"
            StatementRowKind.TRANSFER_OUT -> "→ ${other ?: "Another account"}"
            StatementRowKind.ADJUSTMENT -> "Balance adjustment"
            StatementRowKind.INCOME, StatementRowKind.EXPENSE ->
                txn?.category?.takeIf { it.isNotBlank() } ?: if (row.kind == StatementRowKind.INCOME) "Income" else "Expense"
        }
        val detail = if (row.counts) txn?.description?.takeIf { it.isNotBlank() } else "Before the As-of date, not counted"
        return StatementRowUi(
            transactionId = row.transactionId,
            kind = row.kind,
            title = title,
            subtitle = listOfNotNull(row.date.format(dayLabel), detail).joinToString(" · "),
            amount = formatMoney(row.amount, signed = true),
            isMoneyIn = row.amount > 0,
            balanceAfter = if (row.counts) "Bal ${formatMoney(row.balanceAfter)}" else "",
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
        rows = month.rows.map(::rowUi)
    )

    return AccountPageUiState(
        accountId = accountId,
        accountName = account.name,
        groupName = account.group,
        balanceToday = formatMoney(balance),
        isBalanceNegative = AccountMath.paise(balance) < 0,
        initialBalanceNote = "Balance at end of ${account.asOfDate.format(longDateLabel)} was ${formatMoney(account.initialBalance)}"
            .takeIf { record != null }.orEmpty(),
        months = AccountMath.statement(account, book.transactions).map(::monthUi),
        isLoaded = true
    )
}
