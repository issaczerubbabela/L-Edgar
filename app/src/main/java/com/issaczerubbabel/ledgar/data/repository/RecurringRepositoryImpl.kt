package com.issaczerubbabel.ledgar.data.repository

import androidx.room.withTransaction
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.dao.AccountDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.dao.RecurringRuleDao
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import com.issaczerubbabel.ledgar.util.RecurrenceCalculator
import com.issaczerubbabel.ledgar.util.RepeatSchedule
import com.issaczerubbabel.ledgar.util.TransactionType
import kotlinx.coroutines.flow.Flow
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

    override fun observeRules(): Flow<List<RecurringRule>> = dao.observeAll()

    override suspend fun updateRule(rule: RecurringRule) = dao.update(rule)

    override suspend fun restoreRule(rule: RecurringRule) = dao.restore(rule)

    override suspend fun setPaused(ruleId: Long, paused: Boolean, today: LocalDate) {
        db.withTransaction {
            val rule = dao.getById(ruleId) ?: return@withTransaction
            dao.update(
                if (paused) rule.copy(isPaused = true)
                else rule.copy(isPaused = false, nextDate = RepeatSchedule.resumeDate(rule, today).toString())
            )
        }
    }

    override suspend fun addDueOccurrence(ruleId: Long, today: LocalDate): Long? =
        db.withTransaction { createNextIfDue(ruleId, today, onlyAutomatic = false) }

    override suspend fun skipDueOccurrence(ruleId: Long, today: LocalDate) {
        db.withTransaction {
            val rule = dao.getById(ruleId) ?: return@withTransaction
            val due = RepeatSchedule.dueDates(rule, today).firstOrNull() ?: return@withTransaction
            dao.update(advancedPast(rule, due))
        }
    }

    override suspend fun saveEditedOccurrence(ruleId: Long, occurrenceDate: LocalDate, record: ExpenseRecord): Long =
        db.withTransaction {
            val id = expenseDao.insert(record.copy(recurringRuleId = ruleId))
            val rule = dao.getById(ruleId)
            if (rule != null && rule.nextDate == occurrenceDate.toString()) dao.update(advancedPast(rule, occurrenceDate))
            id
        }

    override suspend fun materializeDue(today: LocalDate): Int {
        var created = 0
        for (ruleId in dao.getDueAutoRuleIds(today.toString())) {
            var guard = 0
            while (guard < MAX_OCCURRENCES_PER_RUN) {
                val createdId = db.withTransaction { createNextIfDue(ruleId, today, onlyAutomatic = true) }
                if (createdId == null) break
                created++
                guard++
            }
        }
        return created
    }

    /**
     * Creates the rule's next due occurrence, if there is one, advances it past that date and returns
     * the new Transaction's id. Runs inside [db]'s transaction, which SQLite serializes against any
     * other writer, so a second call racing this one always sees the advanced `nextDate` and can't
     * recreate the same occurrence. [onlyAutomatic] leaves Ask me first and paused rules alone.
     */
    private suspend fun createNextIfDue(ruleId: Long, today: LocalDate, onlyAutomatic: Boolean): Long? {
        val rule = dao.getById(ruleId) ?: return null
        if (rule.isPaused || (onlyAutomatic && !rule.autoAdd)) return null

        val occurrence = RepeatSchedule.dueDates(rule, today).firstOrNull() ?: return null
        val record = buildRecord(rule, occurrence) ?: return null
        val id = expenseDao.insert(record)
        dao.update(advancedPast(rule, occurrence))
        return id
    }

    private fun advancedPast(rule: RecurringRule, occurrence: LocalDate): RecurringRule = rule.copy(
        nextDate = RecurrenceCalculator.nextAfter(occurrence, rule.frequency, rule.interval, rule.anchorDay).toString(),
        remainingCount = rule.remainingCount?.minus(1)
    )

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
