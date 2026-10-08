package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.account.AccountMath
import com.issaczerubbabel.ledgar.data.repository.AccountBook
import com.issaczerubbabel.ledgar.util.formatMoney
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One Account on the Accounts tab. */
data class AccountRowUi(
    val id: Long,
    val name: String,
    val groupName: String,
    /** "−₹18,640.00". */
    val balance: String,
    val isNegative: Boolean,
    val isIncludedInTotals: Boolean,
    val isHidden: Boolean
)

/** One Account group: its shown Accounts, and the subtotal of those Included in totals, hidden or not. */
data class AccountGroupUi(
    val name: String,
    val isLiability: Boolean,
    val subtotal: String,
    val isSubtotalNegative: Boolean,
    /** The group's Accounts that aren't Hidden, in display order. */
    val rows: List<AccountRowUi>,
    /** Every Account in the group, Hidden ones too, in display order: what Edit order moves. */
    val allRows: List<AccountRowUi>
)

data class AccountsTabUiState(
    val netWorth: String = formatMoney(0.0),
    val assets: String = formatMoney(0.0),
    val liabilities: String = formatMoney(0.0),
    /** "+₹6,420.00 since 1 Oct"; blank until loaded. */
    val monthChange: String = "",
    val isMonthChangeNegative: Boolean = false,
    /** Asset groups first, then Liability groups, each in display order. */
    val groups: List<AccountGroupUi> = emptyList(),
    val hiddenAccounts: List<AccountRowUi> = emptyList(),
    val isLoaded: Boolean = false
)

private val sinceLabel = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** The Accounts tab for [book] on [today]: every figure comes from [AccountMath]. */
fun accountsTab(book: AccountBook, today: LocalDate): AccountsTabUiState {
    val balances = AccountMath.balances(book.accounts, book.transactions)
    val totals = AccountMath.totals(book.accounts, book.transactions)
    val lastMonthEnd = AccountMath.netWorthHistory(book.accounts, book.transactions, listOf(YearMonth.from(today).minusMonths(1)))
        .single().netWorth
    val change = totals.netWorth - lastMonthEnd
    val snapshots = book.accounts.associateBy { it.id }

    // Records arrive in display order; a group sits where its first Account does.
    val rowsByGroup = LinkedHashMap<String, MutableList<AccountRowUi>>()
    book.records.forEach { record ->
        val snapshot = snapshots.getValue(record.id)
        val balance = balances.getValue(record.id)
        rowsByGroup.getOrPut(record.groupName) { mutableListOf() } += AccountRowUi(
            id = record.id,
            name = record.accountName,
            groupName = record.groupName,
            balance = formatMoney(balance),
            isNegative = AccountMath.paise(balance) < 0,
            isIncludedInTotals = snapshot.includedInTotals,
            isHidden = record.isHidden
        )
    }
    val groups = rowsByGroup.map { (name, rows) ->
        val subtotal = AccountMath.rupees(
            rows.filter { it.isIncludedInTotals }.sumOf { AccountMath.paise(balances.getValue(it.id)) }
        )
        AccountGroupUi(
            name = name,
            isLiability = rows.any { snapshots.getValue(it.id).isLiability },
            subtotal = formatMoney(subtotal),
            isSubtotalNegative = AccountMath.paise(subtotal) < 0,
            rows = rows.filterNot { it.isHidden },
            allRows = rows
        )
    }.sortedBy { it.isLiability }

    return AccountsTabUiState(
        netWorth = formatMoney(totals.netWorth),
        assets = formatMoney(totals.assets),
        liabilities = formatMoney(totals.liabilities),
        monthChange = "${formatMoney(change, signed = true)} since ${today.withDayOfMonth(1).format(sinceLabel)}",
        isMonthChangeNegative = AccountMath.paise(change) < 0,
        groups = groups,
        hiddenAccounts = groups.flatMap { group -> group.allRows.filter { it.isHidden } },
        isLoaded = true
    )
}

/** Every Account id in display order after moving one Account from [from] to [to] within [groupName]. */
fun orderAfterMovingAccount(groups: List<AccountGroupUi>, groupName: String, from: Int, to: Int): List<Long> =
    groups.flatMap { group ->
        val ids = group.allRows.map { it.id }
        if (group.name == groupName) ids.moved(from, to) else ids
    }

/** Every Account id in display order after moving the group at [from] to [to]. */
fun orderAfterMovingGroup(groups: List<AccountGroupUi>, from: Int, to: Int): List<Long> =
    groups.moved(from, to).flatMap { group -> group.allRows.map { it.id } }

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices) return this
    val target = to.coerceIn(indices)
    return toMutableList().apply { add(target, removeAt(from)) }
}
