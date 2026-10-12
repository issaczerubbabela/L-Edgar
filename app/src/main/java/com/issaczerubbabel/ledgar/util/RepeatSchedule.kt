package com.issaczerubbabel.ledgar.util

import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import java.time.LocalDate

/**
 * What More > Repeating transactions and the Ledger show for each Repeat rule on a given day: which
 * occurrences are due, which wait for the user, what's coming up, and where a resumed rule picks up.
 * Pure, like [RecurrenceCalculator], so it is unit tested without Android.
 */
object RepeatSchedule {

    /** Every occurrence of [rule] due on or before [today], oldest first, ignoring pause. */
    fun dueDates(rule: RecurringRule, today: LocalDate): List<LocalDate> =
        RecurrenceCalculator.dueOccurrences(
            nextDate = LocalDate.parse(rule.nextDate),
            frequency = rule.frequency,
            interval = rule.interval,
            anchorDay = rule.anchorDay,
            today = today,
            endDate = rule.endDate?.let(LocalDate::parse),
            remainingCount = rule.remainingCount
        )

    /** True once an end date or a count has used the rule up: it will never add anything again. */
    fun isFinished(rule: RecurringRule): Boolean {
        if (rule.remainingCount != null && rule.remainingCount <= 0) return true
        val end = rule.endDate?.let(LocalDate::parse) ?: return false
        return LocalDate.parse(rule.nextDate).isAfter(end)
    }

    /** An Ask me first rule with an occurrence due: it waits for Add, Edit & add or Skip. */
    fun isWaiting(rule: RecurringRule, today: LocalDate): Boolean =
        !rule.autoAdd && !rule.isPaused && dueDates(rule, today).isNotEmpty()

    /** The rules waiting for the user, oldest due date first. */
    fun waiting(rules: List<RecurringRule>, today: LocalDate): List<RecurringRule> =
        rules.filter { isWaiting(it, today) }.sortedWith(compareBy({ it.nextDate }, { it.id }))

    /** Running rules whose next occurrence falls after [today] and within [days] days of it. */
    fun upcoming(rules: List<RecurringRule>, today: LocalDate, days: Long = 30): List<RecurringRule> {
        val last = today.plusDays(days)
        return rules.filter { rule ->
            val next = LocalDate.parse(rule.nextDate)
            !rule.isPaused && !isFinished(rule) && next.isAfter(today) && !next.isAfter(last)
        }.sortedWith(compareBy({ it.nextDate }, { it.id }))
    }

    /**
     * Where a resumed rule picks up: its first occurrence on or after [today], so the time it was
     * paused is skipped rather than back-filled.
     */
    fun resumeDate(rule: RecurringRule, today: LocalDate): LocalDate {
        var next = LocalDate.parse(rule.nextDate)
        var guard = 0
        while (next.isBefore(today) && guard < MAX_STEPS) {
            next = RecurrenceCalculator.nextAfter(next, rule.frequency, rule.interval, rule.anchorDay)
            guard++
        }
        return next
    }

    /** The day a rule repeats on, from the date it starts: day of week for weekly, day of month otherwise. */
    fun anchorDayFor(frequency: String, date: LocalDate): Int =
        if (frequency == RecurrenceFrequency.WEEKLY) date.dayOfWeek.value else date.dayOfMonth

    /** The Ledger chip: "1 repeat waiting", "3 repeats waiting", or null when none are. */
    fun waitingChipLabel(waiting: Int): String? = when {
        waiting <= 0 -> null
        waiting == 1 -> "1 repeat waiting"
        else -> "$waiting repeats waiting"
    }

    // Weekly over more than 40 years: far beyond any real pause, and it can't loop forever.
    private const val MAX_STEPS = 2_100
}
