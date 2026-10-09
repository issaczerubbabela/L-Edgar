package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.account.AccountMath
import com.issaczerubbabel.ledgar.account.Reconcile
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountBook
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.util.formatMoney
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
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
    val isHidden: Boolean,
    /** "Reconciled 12 days ago", or "Counted…" for Cash. */
    val lastChecked: String = "",
    /** Not reconciled for more than 30 days: the row shows a dot. */
    val isStale: Boolean = false
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

/** A Transfer whose account was only ever saved by name, and no Account has that name: the user picks one. */
data class UnlinkedTransferUi(
    val transactionId: Long,
    /** "5 Oct · ₹2,000.00 → Axis Old". */
    val summary: String
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
    /** Newest first. Until linked, a Transfer doesn't count on its unlinked side. */
    val unlinkedTransfers: List<UnlinkedTransferUi> = emptyList(),
    val isLoaded: Boolean = false
) {
    /** The groups the list shows: a group whose Accounts are all Hidden only appears in Edit order. */
    val listedGroups: List<AccountGroupUi> get() = groups.filter { it.rows.isNotEmpty() }
}

private val sinceLabel = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val unlinkedDateLabel = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

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
        val reconciledAt = record.reconciledAt?.let(::parseFlexibleDate)
        rowsByGroup.getOrPut(record.groupName) { mutableListOf() } += AccountRowUi(
            id = record.id,
            name = record.accountName,
            groupName = record.groupName,
            balance = formatMoney(balance),
            isNegative = AccountMath.paise(balance) < 0,
            isIncludedInTotals = snapshot.includedInTotals,
            isHidden = record.isHidden,
            lastChecked = Reconcile.lastCheckedLabel(reconciledAt, record.groupName, today),
            isStale = Reconcile.isStale(reconciledAt, today)
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
        unlinkedTransfers = unlinkedTransfers(book),
        isLoaded = true
    )
}

/**
 * Transfers that name an account on a side with no Account linked, typically from an old Sheet row
 * whose name matched no Account when Room 24 linked Transfers by name.
 */
private fun unlinkedTransfers(book: AccountBook): List<UnlinkedTransferUi> =
    book.transactionRecords
        .filter { record ->
            record.type == TransactionType.TRANSFER &&
                ((record.toAccountId == null && !record.toAccountName.isNullOrBlank()) ||
                    (record.fromAccountId == null && !record.fromAccountName.isNullOrBlank()))
        }
        .sortedWith(compareByDescending<ExpenseRecord> { it.date }.thenByDescending { it.id })
        .map { record ->
            val date = parseFlexibleDate(record.date)?.format(unlinkedDateLabel) ?: record.date
            val from = record.fromAccountName?.takeIf { it.isNotBlank() } ?: "?"
            val to = record.toAccountName?.takeIf { it.isNotBlank() } ?: "?"
            UnlinkedTransferUi(record.id, "$date · ${formatMoney(record.amount)} · $from → $to")
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
