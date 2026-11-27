package com.issaczerubbabel.ledgar.capture

import androidx.room.withTransaction
import com.issaczerubbabel.ledgar.capture.categorize.MerchantRuleTeacher
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.dao.CaptureDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.TransactionType
import javax.inject.Inject

/**
 * Ledger edits teach (#143): when a Transaction that came from a capture (a Confirm or a Link, by
 * `confirmedExpenseId`) has its type or Category changed in the Ledger, its merchant's rule learns
 * the new outcome exactly as a Confirm with that outcome would: an override of the rule's outcome,
 * or one more confirmation of it. For a Transfer, a new other Account is a new outcome too. Amount,
 * date, Account and Description edits teach nothing, and a Ledger edit never renames the merchant.
 *
 * ExpenseRepository wraps its edits in [around], so every edit path in the Ledger is covered. Each
 * rule's read-modify-write runs in its own Room transaction, so a Confirm teaching the same rule at
 * the same moment can't lose an update; the edit itself is not part of it.
 */
class LedgerEditLearner @Inject constructor(
    private val database: SheetSyncDatabase,
    private val captureDao: CaptureDao,
    private val expenseDao: ExpenseDao,
    private val teacher: MerchantRuleTeacher,
    private val reguesser: CaptureReguesser
) {

    /**
     * Runs [edit] on the Transactions [expenseIds], then teaches from those whose outcome it changed.
     * Learning never fails the edit: the edit is saved either way.
     */
    suspend fun <T> around(expenseIds: List<Long>, edit: suspend () -> T): T {
        // The edit is what matters; a lesson missed is only a guess not sharpened.
        val before = bestEffort { snapshot(expenseIds) }
        val result = edit()
        if (!before.isNullOrEmpty()) bestEffort { learn(before) }
        return result
    }

    /** The captures that taught these Transactions, with each Transaction as it was. */
    private suspend fun snapshot(expenseIds: List<Long>): List<Pair<CapturedTransaction, ExpenseRecord>> {
        if (expenseIds.isEmpty()) return emptyList()
        // Chunked: a batch edit can select more rows than SQLite takes in one IN list.
        val captures = expenseIds.distinct().chunked(500).flatMap { captureDao.getTeachingFor(it) }
        val records = captures.mapNotNull { it.confirmedExpenseId }.distinct()
            .mapNotNull { id -> expenseDao.getById(id)?.let { id to it } }.toMap()
        return captures.mapNotNull { capture -> records[capture.confirmedExpenseId]?.let { capture to it } }
    }

    private suspend fun learn(before: List<Pair<CapturedTransaction, ExpenseRecord>>) {
        var taught = false
        before.forEach { (capture, old) ->
            if (database.withTransaction { teachFrom(capture, old) }) taught = true
        }
        // A changed rule can sharpen the guesses on captures still waiting, as after a Confirm.
        if (taught) reguesser.reguessPending()
    }

    /** Teaches [capture]'s merchant what its Transaction says now, if that changed since [old]. */
    private suspend fun teachFrom(capture: CapturedTransaction, old: ExpenseRecord): Boolean {
        val new = expenseDao.getById(old.id)?.takeIf { it.syncAction != "DELETE" } ?: return false
        val outcome = outcomeOf(new, capture) ?: return false
        if (outcome == outcomeOf(old, capture)) return false
        teacher.teach(
            capture,
            type = outcome.type,
            category = outcome.category,
            alwaysUse = false,
            // Description edits never teach, so the merchant keeps the name it has.
            description = { existing -> existing?.description },
            toAccountId = outcome.otherAccountId
        )
        return true
    }

    private data class Outcome(val type: String, val category: String, val otherAccountId: Long?)

    /**
     * What a Transaction says about its capture's merchant: Expense or Income with a Category, or a
     * Transfer with the Account on the capture's other side. Null for anything a rule can't hold.
     */
    private fun outcomeOf(record: ExpenseRecord, capture: CapturedTransaction): Outcome? = when (record.type) {
        TransactionType.TRANSFER -> {
            val other = if (capture.direction == "CREDIT") record.fromAccountId else record.toAccountId
            other?.let { Outcome(TransactionType.TRANSFER, TransactionType.TRANSFER, it) }
        }
        TransactionType.EXPENSE, TransactionType.INCOME ->
            record.category.trim().takeIf { it.isNotEmpty() }?.let { Outcome(record.type, it, null) }
        else -> null
    }
}
