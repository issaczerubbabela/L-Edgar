package com.issaczerubbabel.ledgar.ledger

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import java.time.LocalDate

/** A change to every selected Transaction it fits. */
sealed interface BatchAction {
    data class ChangeDate(val date: String) : BatchAction

    /** [type] is the Category's own type: [TransactionType.EXPENSE] or [TransactionType.INCOME]. */
    data class ChangeCategory(val type: String, val category: String) : BatchAction
    data class ChangeAccount(val accountId: Long) : BatchAction
    data class ChangeDescription(val description: String) : BatchAction
}

/** A selected Transaction a batch change leaves alone, and why. */
data class BatchSkip(val id: Long, val reason: String)

/** Which selected rows a batch change writes, which it skips, and what to tell the user afterwards. */
data class BatchPlan(
    val changeIds: List<Long>,
    val skipped: List<BatchSkip>,
    /** "Changed 4 Expenses · skipped 1 Transfer". */
    val summary: String
)

/**
 * What a batch change does to a selection. Only the rows it fits change: a Category only ever goes to
 * Transactions of its own type, and Transfers keep their Accounts.
 */
fun Ledger.batchPlan(selected: List<ExpenseRecord>, action: BatchAction): BatchPlan {
    val skipReason: (ExpenseRecord) -> String? = when (action) {
        is BatchAction.ChangeDate, is BatchAction.ChangeDescription -> { _ -> null }
        is BatchAction.ChangeCategory -> { txn -> categorySkipReason(txn.type, action.type) }
        is BatchAction.ChangeAccount -> { txn ->
            "A Transfer keeps its from and to Accounts".takeIf { txn.type == TransactionType.TRANSFER }
        }
    }
    val (skipped, changed) = selected.partition { skipReason(it) != null }
    val summary = buildString {
        append(if (changed.isEmpty()) "Nothing changed" else "Changed ${countOf(changed)}")
        if (skipped.isNotEmpty()) append(" · skipped ${byType(skipped)}")
    }
    return BatchPlan(
        changeIds = changed.map { it.id },
        skipped = skipped.map { BatchSkip(it.id, skipReason(it)!!) },
        summary = summary
    )
}

/**
 * What the category picker says before anything is picked: "An expense category changes the 2
 * Expenses, an income category the 1 Income. The 1 Transfer has no category and stays as it is."
 */
fun Ledger.categoryPickerNote(selected: List<ExpenseRecord>): String {
    val expenses = selected.count { it.type == TransactionType.EXPENSE }
    val incomes = selected.count { it.type == TransactionType.INCOME }
    val changes = listOfNotNull(
        "an expense category changes the ${noun(TransactionType.EXPENSE, expenses)}".takeIf { expenses > 0 },
        (if (expenses > 0) "an income category the " else "an income category changes the ")
            .plus(noun(TransactionType.INCOME, incomes)).takeIf { incomes > 0 }
    ).joinToString(", ").replaceFirstChar { it.uppercase() }
    val without = selected.filter { it.type != TransactionType.EXPENSE && it.type != TransactionType.INCOME }
    val withoutNote = when {
        without.isEmpty() -> ""
        without.size == 1 -> "The ${byType(without, joiner = " and ")} has no category and stays as it is."
        else -> "The ${byType(without, joiner = " and ")} have no category and stay as they are."
    }
    return listOf(if (changes.isEmpty()) "" else "$changes.", withoutNote).filter { it.isNotEmpty() }.joinToString(" ")
}

/** What the account picker says: "Changes 4 transactions. The 1 Transfer keeps its Accounts." */
fun Ledger.accountPickerNote(selected: List<ExpenseRecord>): String {
    val (transfers, others) = selected.partition { it.type == TransactionType.TRANSFER }
    val keep = when (transfers.size) {
        0 -> ""
        1 -> " The 1 Transfer keeps its Accounts."
        else -> " The ${transfers.size} Transfers keep their Accounts."
    }
    return "Changes ${countOf(others)}.$keep"
}

/** The date every selected Transaction shares, or null when they differ: where Change date starts. */
fun Ledger.sharedDate(selected: List<ExpenseRecord>): LocalDate? =
    selected.map { parseFlexibleDate(it.date) }.distinct().singleOrNull()

private fun categorySkipReason(transactionType: String, categoryType: String): String? = when (transactionType) {
    TransactionType.TRANSFER -> "A Transfer has no Category"
    TransactionType.ADJUSTMENT -> "A Balance adjustment has no Category"
    categoryType -> null
    TransactionType.INCOME -> "An Income keeps an income Category"
    else -> "An Expense keeps an expense Category"
}

private val typeOrder = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER, TransactionType.ADJUSTMENT)

/** "2 Expenses" when they're all one type, otherwise "5 transactions". */
private fun countOf(transactions: List<ExpenseRecord>): String =
    transactions.map { it.type }.distinct().singleOrNull()?.let { noun(it, transactions.size) }
        ?: "${transactions.size} ${if (transactions.size == 1) "transaction" else "transactions"}"

/** "1 Income, 1 Transfer, 1 Balance adjustment", in type order. */
private fun byType(transactions: List<ExpenseRecord>, joiner: String = ", "): String =
    transactions.groupingBy { it.type }.eachCount()
        .entries.sortedBy { typeOrder.indexOf(it.key).let { i -> if (i < 0) typeOrder.size else i } }
        .joinToString(joiner) { (type, count) -> noun(type, count) }

/** "1 Transfer", "2 Balance adjustments". */
private fun noun(type: String, count: Int): String =
    "$count ${TransactionType.label(type)}${if (count == 1) "" else "s"}"
