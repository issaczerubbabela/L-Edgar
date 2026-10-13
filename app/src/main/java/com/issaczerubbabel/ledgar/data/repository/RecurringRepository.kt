package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface RecurringRepository {

    suspend fun createRule(rule: RecurringRule): Long

    suspend fun getById(id: Long): RecurringRule?

    fun observeRules(): Flow<List<RecurringRule>>

    /** Saves changes to a rule's template or cadence; only Transactions it adds from now on change. */
    suspend fun updateRule(rule: RecurringRule)

    /** Undo for [deleteRule]: the rule comes back as it was, under the same id. */
    suspend fun restoreRule(rule: RecurringRule)

    /**
     * Pauses or resumes a rule. Resuming moves its next date to the first occurrence on or after
     * [today], so the paused stretch is never back-filled.
     */
    suspend fun setPaused(ruleId: Long, paused: Boolean, today: LocalDate = LocalDate.now())

    /** Adds the rule's due occurrence as it stands ("Add" on a waiting one). Returns the new Transaction's id. */
    suspend fun addDueOccurrence(ruleId: Long, today: LocalDate = LocalDate.now()): Long?

    /** Moves past the rule's due occurrence without adding it. */
    suspend fun skipDueOccurrence(ruleId: Long, today: LocalDate = LocalDate.now())

    /**
     * Saves [record], the user's own version of the rule's occurrence on [occurrenceDate] ("Edit &
     * add"), and moves the rule past that date. If the rule has already moved on, only [record] is saved.
     */
    suspend fun saveEditedOccurrence(ruleId: Long, occurrenceDate: LocalDate, record: ExpenseRecord): Long

    /** Removes the rule; Transactions it already created keep their `recurringRuleId` and stay. */
    suspend fun deleteRule(rule: RecurringRule)

    /**
     * Creates every due, automatic occurrence as of [today], one Room transaction at a time so a
     * crash or a concurrent run can't double-add. Returns how many Transactions were created.
     */
    suspend fun materializeDue(today: LocalDate = LocalDate.now()): Int
}
