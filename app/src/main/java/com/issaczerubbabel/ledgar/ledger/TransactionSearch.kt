package com.issaczerubbabel.ledgar.ledger

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.util.formatRupees
import com.issaczerubbabel.ledgar.util.parseAmountInput
import com.issaczerubbabel.ledgar.viewmodel.StatsRoles
import com.issaczerubbabel.ledgar.viewmodel.StatsReport
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * What Search is narrowed to, besides the text in its box. [picks] is the Ledger's own Category and
 * Account filter, so the two screens narrow alike; the rest combine with it (AND).
 */
data class SearchFilter(
    /** Expense, Income or Transfer; null for any. */
    val type: String? = null,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val picks: LedgerFilter = LedgerFilter(),
    val minAmount: Double? = null,
    val maxAmount: Double? = null
) {
    val hasDate: Boolean get() = start != null && end != null
    val hasAmount: Boolean get() = minAmount != null || maxAmount != null
    val isEmpty: Boolean get() = type == null && !hasDate && picks.isEmpty && !hasAmount

    /** How many chips are on, for "Clear all". */
    val activeCount: Int get() = listOf(type != null, hasDate, picks.hasCategories, picks.hasAccounts, hasAmount).count { it }

    /** A Type, keeping only the Categories that can match it: a Transfer has none. */
    fun withType(type: String?): SearchFilter = copy(
        type = type,
        picks = when (type) {
            null -> picks
            TransactionType.EXPENSE -> picks.copy(incomeCategories = emptySet())
            TransactionType.INCOME -> picks.copy(expenseCategories = emptySet())
            else -> picks.copy(expenseCategories = emptySet(), incomeCategories = emptySet())
        }
    )

    fun without(kind: SearchFilterKind): SearchFilter = when (kind) {
        SearchFilterKind.TYPE -> copy(type = null)
        SearchFilterKind.DATE -> copy(start = null, end = null)
        SearchFilterKind.CATEGORY -> copy(picks = picks.without(FilterSection.CATEGORIES))
        SearchFilterKind.ACCOUNT -> copy(picks = picks.without(FilterSection.ACCOUNTS))
        SearchFilterKind.AMOUNT -> copy(minAmount = null, maxAmount = null)
    }
}

/** Search's chips, in the order they're shown. */
enum class SearchFilterKind(val label: String) {
    TYPE("Type"), DATE("Date"), CATEGORY("Category"), ACCOUNT("Account"), AMOUNT("Amount")
}

/** One chip: [value] is what it holds ("Groceries"), or null while it's off and shows its [kind]'s name. */
data class SearchChip(val kind: SearchFilterKind, val value: String?) {
    val isOn: Boolean get() = value != null
}

/** "N transactions · Spent ₹X · Earned ₹Y": Spent and Earned as Stats counts them. */
data class SearchTotals(val count: Int, val spent: Double, val earned: Double)

/** A Category suggested on the empty screen, with which list it's from. */
data class RecentCategory(val name: String, val isIncome: Boolean)

/** A one-tap date range on the Date chip's picker. */
data class DatePreset(val label: String, val start: LocalDate, val end: LocalDate)

/** Search's matching and its chips, kept free of Android classes like the rest of the Ledger. */
object TransactionSearch {

    /** [query] read as an amount ("1499", "1,499", "1499.50"), or null when it's not just a number. */
    fun amountQuery(query: String): Double? {
        val text = query.trim()
        if (text.isEmpty() || text.any { !it.isDigit() && it != ',' && it != '.' && it != ' ' }) return null
        return parseAmountInput(text)
    }

    fun isActive(query: String, filter: SearchFilter): Boolean = query.isNotBlank() || !filter.isEmpty

    /**
     * The Transactions matching [query] and [filter], in the order given; deleted ones never. The box
     * matches the description, remarks, Category, any Account's name, and an amount: a whole number
     * matches whole rupees ("812" finds ₹812.40), one with paise matches exactly.
     */
    fun search(records: List<ExpenseRecord>, query: String, filter: SearchFilter, accounts: List<AccountRecord>): List<ExpenseRecord> {
        if (!isActive(query, filter)) return emptyList()
        val names = accounts.associate { it.id to it.accountName }
        val text = query.trim()
        val amount = amountQuery(text)
        return records.filter { record ->
            record.syncAction != "DELETE" &&
                (text.isEmpty() || matchesText(record, text, amount, names)) &&
                matchesFilter(record, filter)
        }
    }

    private fun matchesText(record: ExpenseRecord, text: String, amount: Double?, names: Map<Long, String>): Boolean {
        val accountNames = listOfNotNull(record.accountId, record.fromAccountId, record.toAccountId).mapNotNull(names::get) +
            listOfNotNull(record.accountName, record.fromAccountName, record.toAccountName)
        val fields = listOf(record.description, record.remarks, record.category) + accountNames
        if (fields.any { it.contains(text, ignoreCase = true) }) return true
        if (amount == null) return false
        val value = abs(record.amount)
        return if (amount % 1.0 == 0.0) value.roundToLong() == amount.roundToLong() else abs(value - amount) < 0.005
    }

    private fun matchesFilter(record: ExpenseRecord, filter: SearchFilter): Boolean {
        if (filter.type != null && record.type != filter.type) return false
        if (filter.hasDate) {
            val date = runCatching { LocalDate.parse(record.date) }.getOrNull() ?: return false
            if (date.isBefore(filter.start) || date.isAfter(filter.end)) return false
        }
        if (!filter.picks.matches(record)) return false
        val value = abs(record.amount)
        if (filter.minAmount != null && value < filter.minAmount) return false
        if (filter.maxAmount != null && value > filter.maxAmount) return false
        return true
    }

    fun totals(results: List<ExpenseRecord>, accounts: List<AccountRecord>, roles: StatsRoles): SearchTotals {
        val money = StatsReport.moneyTotals(results, accounts, roles)
        return SearchTotals(count = results.size, spent = money.spent, earned = money.earned)
    }

    /** All five chips, each saying what it holds when it's on. */
    fun chips(filter: SearchFilter, accounts: List<AccountRecord>): List<SearchChip> {
        val names = accounts.associate { it.id to it.accountName }
        return SearchFilterKind.entries.map { kind ->
            SearchChip(
                kind,
                when (kind) {
                    SearchFilterKind.TYPE -> filter.type?.let(TransactionType::label)
                    SearchFilterKind.DATE -> if (filter.hasDate) Ledger.dateRangeLabel(filter.start, filter.end) else null
                    SearchFilterKind.CATEGORY -> (filter.picks.expenseCategories + filter.picks.incomeCategories)
                        .takeIf { it.isNotEmpty() }?.joinToString(" or ")
                    SearchFilterKind.ACCOUNT -> filter.picks.accountIds.takeIf { it.isNotEmpty() }
                        ?.joinToString(" or ") { names[it] ?: "Account" }
                    SearchFilterKind.AMOUNT -> if (filter.hasAmount) amountLabel(filter.minAmount, filter.maxAmount) else null
                }
            )
        }
    }

    /** "₹500 – ₹2,000", "₹500 or more", "Up to ₹2,000". */
    fun amountLabel(min: Double?, max: Double?): String = when {
        min != null && max != null -> "${formatRupees(min)} – ${formatRupees(max)}"
        min != null -> "${formatRupees(min)} or more"
        max != null -> "Up to ${formatRupees(max)}"
        else -> "Any amount"
    }

    /** The Date chip's shortcuts: the running Salary cycle when there is one, then calendar months. */
    fun datePresets(today: LocalDate, runningCycle: BudgetCycle?): List<DatePreset> {
        val month = YearMonth.from(today)
        val cycle = runningCycle?.let { c ->
            val start = runCatching { LocalDate.parse(c.startDate) }.getOrNull()
            val end = runCatching { LocalDate.parse(c.endDate) }.getOrNull()
            if (start != null && end != null) DatePreset("This cycle", start, end) else null
        }
        return listOfNotNull(
            cycle,
            DatePreset("This month", month.atDay(1), month.atEndOfMonth()),
            DatePreset("Last month", month.minusMonths(1).atDay(1), month.minusMonths(1).atEndOfMonth()),
            DatePreset("Last 3 months", month.minusMonths(2).atDay(1), today)
        )
    }

    /** The latest distinct Expense and Income Categories, for the empty screen's suggestions. */
    fun recentCategories(records: List<ExpenseRecord>, count: Int): List<RecentCategory> =
        records.asSequence()
            .filter { it.syncAction != "DELETE" && it.category.isNotBlank() }
            .filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.INCOME }
            .sortedWith(compareByDescending<ExpenseRecord> { it.date }.thenByDescending { it.id })
            .map { RecentCategory(it.category, it.type == TransactionType.INCOME) }
            .distinct()
            .take(count)
            .toList()
}
