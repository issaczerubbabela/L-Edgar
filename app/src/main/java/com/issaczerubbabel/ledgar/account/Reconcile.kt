package com.issaczerubbabel.ledgar.account

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.TransactionType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What Reconcile or Start fresh writes: the Account, and for Reconcile a Balance adjustment when the figures differ. */
data class ReconcileOutcome(val account: AccountRecord, val adjustment: ExpenseRecord?)

/**
 * Reconcile and Start fresh (see Reconcile and Balance adjustment in CONTEXT.md, and ADR-0009).
 * Pure: the repository writes what these return in one Room transaction.
 */
object Reconcile {

    /** An Account not reconciled for longer than this many days gets a dot on the Accounts tab. */
    const val STALE_AFTER_DAYS = 30L

    /**
     * Matches [account] to [bankBalance] on [today]: a Balance adjustment for any difference, dated
     * today, and the Account marked as reconciled today either way. The Initial balance stays.
     */
    fun reconcile(account: AccountRecord, appBalance: Double, bankBalance: Double, today: LocalDate): ReconcileOutcome {
        val difference = AccountMath.reconcileDifference(bankBalance = bankBalance, appBalance = appBalance)
        val adjustment = if (AccountMath.paise(difference) == 0L) null else ExpenseRecord(
            date = today.toString(),
            type = TransactionType.ADJUSTMENT,
            category = "",
            description = "Balance adjustment",
            amount = difference,
            accountId = account.id,
            accountName = account.accountName,
            remarks = "Reconciled with bank",
            isSynced = false,
            syncAction = "INSERT"
        )
        return ReconcileOutcome(account.copy(reconciledAt = today.toString()), adjustment)
    }

    /**
     * Starts [account] afresh: [bankBalance] becomes its Initial balance at the end of [today], so
     * every earlier Transaction stops counting. Adds no Transaction.
     */
    fun startFresh(account: AccountRecord, bankBalance: Double, today: LocalDate): ReconcileOutcome =
        ReconcileOutcome(
            account.copy(
                initialBalance = AccountMath.rupees(AccountMath.paise(bankBalance)),
                initialBalanceDate = today.toString(),
                reconciledAt = today.toString()
            ),
            adjustment = null
        )

    /** "Reconciled 12 days ago", or "Counted…" for a Cash group, which has no bank to check against. */
    fun lastCheckedLabel(reconciledAt: LocalDate?, groupName: String, today: LocalDate): String {
        val verb = if (isCash(groupName)) "Counted" else "Reconciled"
        if (reconciledAt == null) return if (isCash(groupName)) "Not counted yet" else "Not reconciled yet"
        return when (val days = ChronoUnit.DAYS.between(reconciledAt, today)) {
            0L -> "$verb today"
            1L -> "$verb yesterday"
            else -> "$verb $days days ago"
        }
    }

    /** True once [reconciledAt] is more than [STALE_AFTER_DAYS] days before [today]. Never reconciled isn't flagged. */
    fun isStale(reconciledAt: LocalDate?, today: LocalDate): Boolean =
        reconciledAt != null && ChronoUnit.DAYS.between(reconciledAt, today) > STALE_AFTER_DAYS

    fun isCash(groupName: String): Boolean = groupName.trim().lowercase().contains("cash")
}
