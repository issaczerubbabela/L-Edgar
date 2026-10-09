package com.issaczerubbabel.ledgar.ui.components

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.TransactionType
import kotlin.math.abs

/** What Search and Bookmarks show for a Transaction, so a Balance adjustment reads as one. */
val ExpenseRecord.isAdjustment: Boolean get() = type == TransactionType.ADJUSTMENT

fun ExpenseRecord.listTitle(): String = description.ifBlank { category.ifBlank { TransactionType.label(type) } }

fun ExpenseRecord.listDetails(): String =
    listOf(date, TransactionType.label(type), category).filter { it.isNotBlank() }.joinToString(" • ")

/** A Balance adjustment is signed, since it can lower a balance as well as raise it. */
fun ExpenseRecord.listAmount(): String =
    if (isAdjustment) "${if (amount < 0) "−" else "+"}₹ %,.2f".format(abs(amount)) else "₹ %,.2f".format(amount)
