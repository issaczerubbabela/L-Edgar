package com.issaczerubbabel.ledgar.ledger

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.util.formatListMoney
import java.time.YearMonth

/**
 * What the Ledger is narrowed to. Choices within a section combine with OR, sections with AND:
 * "Food or Transport, paid from HDFC Credit card". An empty section matches everything.
 */
data class LedgerFilter(
    val expenseCategories: Set<String> = emptySet(),
    val incomeCategories: Set<String> = emptySet(),
    val accountIds: Set<Long> = emptySet()
) {
    val hasCategories: Boolean get() = expenseCategories.isNotEmpty() || incomeCategories.isNotEmpty()
    val hasAccounts: Boolean get() = accountIds.isNotEmpty()
    val isEmpty: Boolean get() = !hasCategories && !hasAccounts
    val activeCount: Int get() = expenseCategories.size + incomeCategories.size + accountIds.size

    /**
     * Categories are one section that knows its types: an expense category only matches Expenses and an
     * income one only Incomes, so a Transfer or Balance adjustment never matches a category. Accounts
     * match the Account, the from-Account or the to-Account.
     */
    fun matches(transaction: ExpenseRecord): Boolean {
        val categoriesMatch = !hasCategories || when (transaction.type) {
            TransactionType.EXPENSE -> transaction.category in expenseCategories
            TransactionType.INCOME -> transaction.category in incomeCategories
            else -> false
        }
        val accountsMatch = !hasAccounts || transaction.accountIds().any { it in accountIds }
        return categoriesMatch && accountsMatch
    }

    fun without(section: FilterSection): LedgerFilter = when (section) {
        FilterSection.CATEGORIES -> copy(expenseCategories = emptySet(), incomeCategories = emptySet())
        FilterSection.ACCOUNTS -> copy(accountIds = emptySet())
    }
}

/** The filter's sections, each with its own chip. */
enum class FilterSection { CATEGORIES, ACCOUNTS }

/** A chip under the tabs for one active section: "Food or Transport". */
data class FilterChipUi(val section: FilterSection, val label: String)

/** One choice in the filter sheet. */
data class FilterOptionUi(
    /** The Category name, or the Account's id. */
    val key: String,
    val label: String,
    /** This month's amount: "₹1,400". */
    val amount: String,
    /** Found on this month's Transactions but no longer in the Category list. */
    val isLeftover: Boolean,
    val isSelected: Boolean
)

/** One list in the sheet: what moved this month, biggest first, then what didn't, folded away. */
data class FilterOptionsSection(val used: List<FilterOptionUi>, val unused: List<FilterOptionUi>)

/** Everything the filter sheet shows for the month on screen. */
data class FilterOptions(
    val expense: FilterOptionsSection,
    val income: FilterOptionsSection,
    val accounts: FilterOptionsSection,
    /** How many of the month's Transactions [draft] would show: "Show 14 transactions". */
    val matchCount: Int
)

/**
 * The filter sheet's choices for [month], with [draft] (what's ticked so far) applied to the count and
 * the ticks. [expenseCategories] and [incomeCategories] are the Category lists, in their own order.
 */
fun Ledger.filterOptions(
    transactions: List<ExpenseRecord>,
    accounts: List<AccountRecord>,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
    month: YearMonth,
    draft: LedgerFilter,
    pendingDeleteIds: Set<Long> = emptySet()
): FilterOptions {
    val inMonth = monthTransactions(transactions, month, pendingDeleteIds)

    fun categorySection(type: String, listed: List<String>, ticked: Set<String>): FilterOptionsSection {
        val totals = inMonth.filter { it.type == type && it.category.isNotBlank() }
            .groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
        val names = (listed + totals.keys).distinct()
        return section(names.map { name ->
            Choice(name, name, totals[name], isLeftover = name !in listed, isSelected = name in ticked)
        })
    }

    val accountTotals = accounts.associate { account ->
        account.id to inMonth.filter { account.id in it.accountIds() }.takeIf { it.isNotEmpty() }?.sumOf { kotlin.math.abs(it.amount) }
    }
    return FilterOptions(
        expense = categorySection(TransactionType.EXPENSE, expenseCategories, draft.expenseCategories),
        income = categorySection(TransactionType.INCOME, incomeCategories, draft.incomeCategories),
        accounts = section(accounts.map { account ->
            Choice(account.id.toString(), account.accountName, accountTotals[account.id], isLeftover = false,
                isSelected = account.id in draft.accountIds)
        }),
        matchCount = inMonth.count(draft::matches)
    )
}

/** The chips for an active [filter], one per section: "Food or Transport", "HDFC Credit card". */
fun Ledger.filterChips(filter: LedgerFilter, accounts: List<AccountRecord>): List<FilterChipUi> {
    val names = accounts.associate { it.id to it.accountName }
    return listOfNotNull(
        (filter.expenseCategories + filter.incomeCategories).takeIf { it.isNotEmpty() }
            ?.let { FilterChipUi(FilterSection.CATEGORIES, it.joinToString(" or ")) },
        filter.accountIds.takeIf { it.isNotEmpty() }
            ?.let { ids -> FilterChipUi(FilterSection.ACCOUNTS, ids.joinToString(" or ") { names[it] ?: "Account" }) }
    )
}

/** Every Account a Transaction touches: its own, or a Transfer's from and to. */
private fun ExpenseRecord.accountIds(): List<Long> = listOfNotNull(accountId, fromAccountId, toAccountId)

/** A sheet choice before sorting: [total] is null when nothing moved this month. */
private data class Choice(val key: String, val label: String, val total: Double?, val isLeftover: Boolean, val isSelected: Boolean)

private fun section(choices: List<Choice>): FilterOptionsSection {
    fun Choice.ui() = FilterOptionUi(key, label, formatListMoney(total ?: 0.0), isLeftover, isSelected)
    val (used, unused) = choices.partition { it.total != null }
    return FilterOptionsSection(
        used = used.sortedWith(compareByDescending<Choice> { it.total }.thenBy { it.label }).map { it.ui() },
        unused = unused.map { it.ui() }
    )
}
