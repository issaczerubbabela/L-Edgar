package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.account.AccountMath
import com.issaczerubbabel.ledgar.account.Reconcile
import com.issaczerubbabel.ledgar.data.repository.AccountBook
import com.issaczerubbabel.ledgar.util.formatMoney
import com.issaczerubbabel.ledgar.util.parseAmountInput
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Reconcile sheet for one Account, as the user types the bank's figure. */
data class ReconcileSheetUi(
    val accountName: String,
    /** "Bank shows", or "Bank says you owe" for a Liability. */
    val bankLabel: String,
    /** L.Edgar's figure the same way round: for a Liability, what's owed. */
    val appFigure: String,
    val lastChecked: String,
    /** The bank's figure as a balance (a Liability's amount owed made negative), or null while the field isn't a number. */
    val bankBalance: Double?,
    /** "+₹340.00": what the Balance adjustment would add to the balance; null while the field isn't a number. */
    val difference: String?,
    val isDifferenceNegative: Boolean,
    val matches: Boolean,
    /** "Adds a Balance adjustment of +₹340.00 dated 8 Oct 2026." or why nothing is added. */
    val note: String
)

private val noteDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** The sheet for [accountId] with [input] typed, on [today]; null when the Account no longer exists. */
fun reconcileSheet(book: AccountBook, accountId: Long, input: String, today: LocalDate): ReconcileSheetUi? {
    val account = book.accounts.firstOrNull { it.id == accountId } ?: return null
    val record = book.records.firstOrNull { it.id == accountId } ?: return null
    val appBalance = AccountMath.balances(book.accounts, book.transactions)[accountId] ?: account.initialBalance
    val typed = parseAmountInput(input)
    val bankBalance = typed?.let { AccountForm.initialBalance(it, account.isLiability) }
    val difference = bankBalance?.let { AccountMath.reconcileDifference(bankBalance = it, appBalance = appBalance) }
    val matches = difference != null && AccountMath.paise(difference) == 0L
    return ReconcileSheetUi(
        accountName = account.name,
        bankLabel = if (account.isLiability) "Bank says you owe" else "Bank shows",
        appFigure = formatMoney(AccountForm.amountShown(appBalance, account.isLiability)),
        lastChecked = Reconcile.lastCheckedLabel(record.reconciledAt?.let(::parseFlexibleDate), account.group, today),
        bankBalance = bankBalance,
        difference = difference?.let { formatMoney(it, signed = true) },
        isDifferenceNegative = difference != null && AccountMath.paise(difference) < 0,
        matches = matches,
        note = when {
            difference == null -> "Type what your bank shows right now."
            matches -> "The balances match. Nothing is added; the account is marked as checked today."
            else -> "Adds a Balance adjustment of ${formatMoney(difference, signed = true)} dated ${today.format(noteDate)}. " +
                "It shows in the statement and never counts as income or spending."
        }
    )
}
