package com.issaczerubbabel.ledgar.data.repository

import androidx.room.withTransaction
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.dao.AccountDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.dao.RecurringRuleDao
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import com.issaczerubbabel.ledgar.util.RecurrenceCalculator
import com.issaczerubbabel.ledgar.util.TransactionType
import java.time.LocalDate
import javax.inject.Inject

class RecurringRepositoryImpl @Inject constructor(
    private val db: SheetSyncDatabase,
    private val dao: RecurringRuleDao,
    private val expenseDao: ExpenseDao,
    private val accountDao: AccountDao
) : RecurringRepository {

    override suspend fun createRule(rule: RecurringRule): Long = dao.insert(rule)

    override suspend fun getById(id: Long): RecurringRule? = dao.getById(id)

    override suspend fun deleteRule(rule: RecurringRule) = dao.delete(rule)

    override suspend fun materializeDue(today: LocalDate): Int {
        var created = 0
        for (ruleId in dao.getDueAutoRuleIds(today.toString())) {
            var guard = 0
            while (guard < MAX_OCCURRENCES_PER_RUN) {
                val didCreate = db.withTransaction { materializeNextIfDue(ruleId, today) }
                if (!didCreate) break
                created++
                guard++
            }
        }
        return created
    }

    /**
     * Creates the rule's next due occurrence, if there is one, and advances it past that date.
     * Runs inside [db]'s transaction, which SQLite serializes against any other writer, so a second
     * call racing this one always sees the advanced `nextDate` and can't recreate the same occurrence.
     */
    private suspend fun materializeNextIfDue(ruleId: Long, today: LocalDate): Boolean {
        val rule = dao.getById(ruleId) ?: return false
        if (rule.isPaused || !rule.autoAdd) return false

        val nextDate = LocalDate.parse(rule.nextDate)
        val occurrence = RecurrenceCalculator.dueOccurrences(
            nextDate = nextDate,
            frequency = rule.frequency,
            interval = rule.interval,
            anchorDay = rule.anchorDay,
            today = today,
            endDate = rule.endDate?.let(LocalDate::parse),
            remainingCount = rule.remainingCount,
            maxOccurrences = 1
        ).firstOrNull() ?: return false

        val record = buildRecord(rule, occurrence) ?: return false
        expenseDao.insert(record)
        dao.update(
            rule.copy(
                nextDate = RecurrenceCalculator.nextAfter(occurrence, rule.frequency, rule.interval, rule.anchorDay).toString(),
                remainingCount = rule.remainingCount?.minus(1)
            )
        )
        return true
    }

    /** Null when the rule's required account(s) were deleted: skip rather than create a broken row. */
    private suspend fun buildRecord(rule: RecurringRule, date: LocalDate): ExpenseRecord? {
        val hasRequiredAccounts = if (rule.type == TransactionType.TRANSFER) {
            rule.fromAccountId != null && rule.toAccountId != null
        } else {
            rule.accountId != null
        }
        if (!hasRequiredAccounts) return null

        return ExpenseRecord(
            date = date.toString(),
            type = rule.type,
            category = rule.category,
            description = rule.description,
            amount = rule.amount,
            accountId = rule.accountId,
            remarks = rule.remarks,
            fromAccountId = rule.fromAccountId,
            toAccountId = rule.toAccountId,
            toAccountName = rule.toAccountId?.let { accountDao.getAccountById(it)?.accountName },
            isBookmarked = false,
            isSynced = false,
            syncAction = "INSERT",
            recurringRuleId = rule.id
        )
    }

    private companion object {
        /** Caps catch-up per rule per call, so a corrupted or long-untouched `nextDate` can't loop forever. */
        const val MAX_OCCURRENCES_PER_RUN = 24
    }
}
