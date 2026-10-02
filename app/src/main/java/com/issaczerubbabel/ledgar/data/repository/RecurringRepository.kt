package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import java.time.LocalDate

interface RecurringRepository {

    suspend fun createRule(rule: RecurringRule): Long

    suspend fun getById(id: Long): RecurringRule?

    /** Removes the rule; Transactions it already created keep their `recurringRuleId` and stay. */
    suspend fun deleteRule(rule: RecurringRule)

    /**
     * Creates every due, automatic occurrence as of [today], one Room transaction at a time so a
     * crash or a concurrent run can't double-add. Returns how many Transactions were created.
     */
    suspend fun materializeDue(today: LocalDate = LocalDate.now()): Int
}
