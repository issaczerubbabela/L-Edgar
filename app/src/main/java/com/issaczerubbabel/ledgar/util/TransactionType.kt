package com.issaczerubbabel.ledgar.util

/** The values `ExpenseRecord.type` takes, and which dropdown list feeds each one's categories. */
object TransactionType {
    const val EXPENSE = "Expense"
    const val INCOME = "Income"
    const val TRANSFER = "Transfer"

    /** A Balance adjustment (ADR-0009): only Reconcile creates one, and only Account balances count it. */
    const val ADJUSTMENT = "Adjustment"

    const val EXPENSE_CATEGORY_OPTION = "EXPENSE_CATEGORY"
    const val INCOME_CATEGORY_OPTION = "INCOME_CATEGORY"

    /** How a type is named on screen. */
    fun label(type: String): String = if (type == ADJUSTMENT) "Balance adjustment" else type

    fun categoryOptionType(type: String): String =
        if (type == INCOME) INCOME_CATEGORY_OPTION else EXPENSE_CATEGORY_OPTION
}
