package com.issaczerubbabel.ledgar.util

/** The values `ExpenseRecord.type` takes, and which dropdown list feeds each one's categories. */
object TransactionType {
    const val EXPENSE = "Expense"
    const val INCOME = "Income"
    const val TRANSFER = "Transfer"

    const val EXPENSE_CATEGORY_OPTION = "EXPENSE_CATEGORY"
    const val INCOME_CATEGORY_OPTION = "INCOME_CATEGORY"

    fun categoryOptionType(type: String): String =
        if (type == INCOME) INCOME_CATEGORY_OPTION else EXPENSE_CATEGORY_OPTION
}
