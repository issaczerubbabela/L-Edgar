package com.issaczerubbabel.ledgar.ledger

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.util.formatCalendarMoney
import com.issaczerubbabel.ledgar.util.formatListMoney
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Income, Expenses and Net of the rows on screen. */
data class LedgerSummary(
    val income: String = formatListMoney(0.0),
    val expenses: String = formatListMoney(0.0),
    val net: String = formatListMoney(0.0)
)

/** One Transaction as a row. */
data class LedgerRow(
    val record: ExpenseRecord,
    val type: String,
    /** The Category, or "Balance adjustment"; blank for a Transfer. */
    val category: String,
    /** The description, falling back to the Category. */
    val description: String,
    /** The Account, or "From → To" for a Transfer. */
    val accountLabel: String,
    /** In full: "₹1,25,000", or "−₹340" for a Balance adjustment, which carries its sign. */
    val amount: String
) {
    val id: Long get() = record.id
    val isAdjustment: Boolean get() = type == TransactionType.ADJUSTMENT
}

/** What the transaction sheet shows. */
data class TransactionDetails(
    /** The raw type, for colour: [TransactionType]'s values. */
    val type: String,
    /** "Expense", "Balance adjustment". */
    val typeLabel: String,
    /** "₹1,212.50", signed for a Balance adjustment. */
    val amount: String,
    val description: String,
    /** "Fri, 9 Oct 2026". */
    val date: String,
    /** "9 Oct 2026", for "Copy for 9 Oct 2026". */
    val shortDate: String,
    /** The Account, or "From → To" for a Transfer. */
    val accounts: String,
    /** Null for a Transfer or Balance adjustment, which never have one. */
    val category: String?,
    val note: String?,
    val isBookmarked: Boolean
) {
    val isAdjustment: Boolean get() = type == TransactionType.ADJUSTMENT
    val isTransfer: Boolean get() = type == TransactionType.TRANSFER
}

/** One day on Daily: its rows newest logged first, and only the sides that moved. */
data class LedgerDay(
    val date: LocalDate,
    /** "9". */
    val dayNumber: String,
    /** "Fri". */
    val weekday: String,
    /** "Fri, 9 Oct": the day sheet's title. */
    val title: String,
    /** "9 Oct", for "Add on 9 Oct". */
    val shortDate: String,
    /** Null when nothing came in that day. */
    val income: String?,
    /** Null when nothing went out that day. */
    val expense: String?,
    val rows: List<LedgerRow>
)

/** One day on the Calendar. A day outside the month is there to fill the week and carries no figures. */
data class LedgerCalendarCell(
    val date: LocalDate,
    val isInMonth: Boolean,
    val isToday: Boolean,
    val income: Double,
    val expense: Double,
    /** Income − Expenses; negative on a day that spent more than it earned. */
    val net: Double,
    /** "1.2k"; null when nothing came in. */
    val incomeLabel: String?,
    /** Null when nothing went out. */
    val expenseLabel: String?,
    /** "−1.3k"; null when neither side moved. */
    val netLabel: String?,
    /** The day's distinct Categories, for its dots. */
    val categories: List<String>,
    val transactionCount: Int
)

/** One month of the Ledger: everything the tab draws for it. */
data class LedgerMonth(
    val month: YearMonth,
    /** "Oct 2026". */
    val monthLabel: String,
    val summary: LedgerSummary,
    /** Newest day first. */
    val days: List<LedgerDay>,
    /** Sunday first, 5 or 6 whole weeks. */
    val calendar: List<LedgerCalendarCell>
)

private val monthLabelFormat = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
private val weekdayFormat = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val dayTitleFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
internal val dayMonthFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
internal val sheetDateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
internal val shortDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/**
 * The Ledger tab as pure functions: given the Transactions and the month on screen, what the tab
 * shows. Screens and ViewModels read their figures from here and never recount them.
 */
object Ledger {

    /**
     * The Ledger for [month], narrowed to [filter]. Transactions in [pendingDeleteIds] are waiting on
     * Undo: they are left out of every figure, so the screen updates before the delete is written.
     */
    fun build(
        transactions: List<ExpenseRecord>,
        accounts: List<AccountRecord>,
        month: YearMonth,
        today: LocalDate,
        pendingDeleteIds: Set<Long> = emptySet(),
        filter: LedgerFilter = LedgerFilter()
    ): LedgerMonth {
        val inMonth = datedInMonth(transactions, month, pendingDeleteIds).filter { filter.matches(it.transaction) }
        val totals = Totals.of(inMonth.map { it.transaction })
        val byDate = inMonth.groupBy({ it.date }, { it.transaction })
        val accountNames = accounts.associate { it.id to it.accountName }
        val days = byDate.toSortedMap(reverseOrder()).map { (date, onDay) ->
            val dayTotals = Totals.of(onDay)
            dayOf(
                date,
                income = dayTotals.income.takeIf { dayTotals.hasIncome }?.let(::formatListMoney),
                expense = dayTotals.expense.takeIf { dayTotals.hasExpense }?.let(::formatListMoney),
                rows = rowsNewestFirst(onDay, accountNames)
            )
        }
        return LedgerMonth(
            month = month,
            monthLabel = month.format(monthLabelFormat),
            summary = LedgerSummary(
                income = formatListMoney(totals.income),
                expenses = formatListMoney(totals.expense),
                net = formatListMoney(totals.net)
            ),
            days = days,
            calendar = calendar(month, today, byDate)
        )
    }

    private fun calendar(
        month: YearMonth,
        today: LocalDate,
        byDate: Map<LocalDate, List<ExpenseRecord>>
    ): List<LedgerCalendarCell> {
        val first = month.atDay(1)
        // DayOfWeek counts Monday as 1 and Sunday as 7; the grid starts on Sunday.
        val start = first.minusDays((first.dayOfWeek.value % 7).toLong())
        val used = (first.dayOfWeek.value % 7) + month.lengthOfMonth()
        val cellCount = if (used <= 35) 35 else 42
        return (0 until cellCount).map { offset ->
            val date = start.plusDays(offset.toLong())
            val isInMonth = YearMonth.from(date) == month
            val onDay = if (isInMonth) byDate[date].orEmpty() else emptyList()
            val totals = Totals.of(onDay)
            LedgerCalendarCell(
                date = date,
                isInMonth = isInMonth,
                isToday = date == today,
                income = totals.income,
                expense = totals.expense,
                net = totals.net,
                incomeLabel = totals.income.takeIf { totals.hasIncome }?.let(::formatCalendarMoney),
                expenseLabel = totals.expense.takeIf { totals.hasExpense }?.let(::formatCalendarMoney),
                netLabel = if (totals.hasIncome || totals.hasExpense) formatCalendarMoney(totals.net) else null,
                categories = onDay.map { it.category }.filter { it.isNotBlank() }.distinct(),
                transactionCount = onDay.size
            )
        }
    }

    /** [date]'s day on [ledger], for the Calendar's day sheet: an empty day when nothing was logged. */
    fun day(ledger: LedgerMonth, date: LocalDate): LedgerDay =
        ledger.days.firstOrNull { it.date == date } ?: dayOf(date, income = null, expense = null, rows = emptyList())

    private fun dayOf(date: LocalDate, income: String?, expense: String?, rows: List<LedgerRow>) = LedgerDay(
        date = date,
        dayNumber = date.dayOfMonth.toString(),
        weekday = date.format(weekdayFormat),
        title = date.format(dayTitleFormat),
        shortDate = date.format(dayMonthFormat),
        income = income,
        expense = expense,
        rows = rows
    )

    /** The newest month with a Transaction in it, or null when there are none. */
    fun latestMonth(transactions: List<ExpenseRecord>): YearMonth? =
        transactions.mapNotNull(::dateOf).maxOrNull()?.let(YearMonth::from)

    /** Income − Expenses of [transactions], as the Ledger counts it: Transfers and Balance adjustments add nothing. */
    fun net(transactions: List<ExpenseRecord>): Double = Totals.of(transactions).net

    /** "Delete 6 transactions (−₹4,250)?": what a batch delete asks before it happens. */
    fun deleteConfirm(selected: List<ExpenseRecord>): String {
        val noun = if (selected.size == 1) "transaction" else "transactions"
        return "Delete ${selected.size} $noun (${formatListMoney(net(selected))})?"
    }

    /** "Deleted “Uber to office” · ₹212": the Undo snackbar's text. */
    fun deletedMessage(transaction: ExpenseRecord, accounts: List<AccountRecord>): String {
        val row = row(transaction, accounts)
        return "Deleted “${row.description}” · ${row.amount}"
    }

    /** [transaction] as a row anywhere else it's listed (Search, Bookmarks, an Account's page). */
    fun row(transaction: ExpenseRecord, accounts: List<AccountRecord>): LedgerRow =
        row(transaction, accounts.associate { it.id to it.accountName })

    /** What the transaction sheet shows for [transaction]. */
    fun details(transaction: ExpenseRecord, accounts: List<AccountRecord>): TransactionDetails {
        val row = row(transaction, accounts)
        val date = dateOf(transaction)
        return TransactionDetails(
            type = transaction.type,
            typeLabel = TransactionType.label(transaction.type),
            amount = row.amount,
            description = row.description,
            date = date?.format(sheetDateFormat) ?: transaction.date,
            shortDate = date?.format(shortDateFormat) ?: transaction.date,
            accounts = row.accountLabel,
            // The row already blanks a Transfer's Category; a Balance adjustment's names its type.
            category = row.category.takeIf { !row.isAdjustment && it.isNotBlank() },
            note = transaction.remarks.takeIf { it.isNotBlank() },
            isBookmarked = transaction.isBookmarked
        )
    }

    private fun row(record: ExpenseRecord, accountNames: Map<Long, String>): LedgerRow {
        val isAdjustment = record.type == TransactionType.ADJUSTMENT
        val category = when (record.type) {
            TransactionType.ADJUSTMENT -> TransactionType.label(record.type)
            TransactionType.TRANSFER -> ""
            else -> record.category
        }
        return LedgerRow(
            record = record,
            type = record.type,
            category = category,
            description = record.description.ifBlank { category.ifBlank { TransactionType.label(record.type) } },
            accountLabel = accountLabel(record, accountNames),
            amount = formatListMoney(record.amount, signed = isAdjustment)
        )
    }

    private fun accountLabel(record: ExpenseRecord, names: Map<Long, String>): String {
        fun name(id: Long?, fallback: String?) = id?.let(names::get) ?: fallback.orEmpty()
        return when (record.type) {
            TransactionType.INCOME -> name(record.toAccountId ?: record.accountId, record.toAccountName ?: record.accountName)
            TransactionType.EXPENSE -> name(record.fromAccountId ?: record.accountId, record.fromAccountName ?: record.accountName)
            TransactionType.TRANSFER -> {
                val from = name(record.fromAccountId, record.fromAccountName)
                val to = name(record.toAccountId, record.toAccountName)
                when {
                    from.isNotBlank() && to.isNotBlank() -> "$from → $to"
                    else -> from.ifBlank { to }
                }
            }
            else -> name(record.accountId, record.accountName)
        }
    }

    /** Newest logged first; ids only grow, so the order never reshuffles on a Sync. */
    internal fun rowsNewestFirst(transactions: List<ExpenseRecord>, accountNames: Map<Long, String>): List<LedgerRow> =
        transactions.sortedByDescending { it.id }.map { row(it, accountNames) }

    /** [month]'s Transactions, less those waiting on Undo. */
    internal fun monthTransactions(transactions: List<ExpenseRecord>, month: YearMonth, pendingDeleteIds: Set<Long>) =
        datedInMonth(transactions, month, pendingDeleteIds).map { it.transaction }

    // Each date is parsed once per build.
    private fun datedInMonth(transactions: List<ExpenseRecord>, month: YearMonth, pendingDeleteIds: Set<Long>): List<Dated> =
        transactions.mapNotNull { record ->
            if (record.id in pendingDeleteIds) return@mapNotNull null
            val date = dateOf(record) ?: return@mapNotNull null
            if (YearMonth.from(date) == month) Dated(date, record) else null
        }

    internal fun dateOf(record: ExpenseRecord): LocalDate? =
        parseFlexibleDate(record.date) ?: record.remoteTimestamp?.let(::parseFlexibleDate)
}

private data class Dated(val date: LocalDate, val transaction: ExpenseRecord)

/** Income and Expenses counted by type alone; Transfers and Balance adjustments never add up. */
internal data class Totals(val income: Double, val expense: Double) {
    val net: Double get() = income - expense
    /** More than rounding dust came in. */
    val hasIncome: Boolean get() = income > HALF_PAISA
    val hasExpense: Boolean get() = expense > HALF_PAISA

    companion object {
        private const val HALF_PAISA = 0.005

        fun of(transactions: List<ExpenseRecord>) = Totals(
            income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
            expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        )
    }
}
