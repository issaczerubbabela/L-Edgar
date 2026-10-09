package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.util.parseAmountInput

/** What's wrong with the Add/Edit Account form, shown next to each field. */
data class AccountFormErrors(
    val group: String? = null,
    val name: String? = null,
    val amount: String? = null
) {
    val isEmpty: Boolean get() = group == null && name == null && amount == null
}

/** The Add/Edit Account form's rules, kept free of Android so they can be tested. */
object AccountForm {

    /**
     * Checks the form. The amount is only checked when adding: once an Account exists its Initial
     * balance is read-only and corrected by Reconcile (ADR-0009).
     */
    fun validate(
        group: String,
        name: String,
        amountInput: String,
        otherAccountNames: List<String>,
        isAdding: Boolean
    ): AccountFormErrors {
        val trimmed = name.trim()
        return AccountFormErrors(
            group = "Pick a group".takeIf { group.isBlank() },
            name = when {
                trimmed.isEmpty() -> "Enter a name"
                otherAccountNames.any { it.trim().equals(trimmed, ignoreCase = true) } ->
                    "You already have an account called $trimmed"
                else -> null
            },
            amount = "Enter an amount, like 1250.50".takeIf { isAdding && parseAmountInput(amountInput) == null }
        )
    }

    /** The Initial balance to store: a Liability's "Amount owed" is typed positive and stored negative. */
    fun initialBalance(amount: Double, isLiability: Boolean): Double =
        if (isLiability && amount != 0.0) -amount else amount

    /** The amount the form shows for a stored Initial balance: for a Liability, what's owed. */
    fun amountShown(initialBalance: Double, isLiability: Boolean): Double =
        if (isLiability && initialBalance != 0.0) -initialBalance else initialBalance

    /**
     * Whether Delete… may go ahead. With no Transactions it can; otherwise they must be moved to
     * another Account, or the Account's name typed to delete them too.
     */
    fun canConfirmDelete(
        transactionCount: Int,
        deleteTransactions: Boolean,
        moveToAccountId: Long?,
        accountId: Long,
        typedName: String,
        accountName: String
    ): Boolean = when {
        transactionCount == 0 -> true
        deleteTransactions -> typedName.trim() == accountName.trim()
        else -> moveToAccountId != null && moveToAccountId != accountId
    }
}
