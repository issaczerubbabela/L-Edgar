package com.issaczerubbabel.ledgar.capture.categorize

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One Expense or Income from `expense_records`, as the on-phone guessers see it. Hand-typed rows
 * have no [merchantNorm] and no [minuteOfDay]; a row confirmed from a capture carries both, from
 * the capture it came from.
 */
data class PastTransaction(
    val id: Long,
    val type: String,
    val category: String,
    val description: String,
    val amount: Double,
    val date: LocalDate,
    val merchantNorm: String? = null,
    /** Minutes after midnight, or null when unknown (a hand-typed Transaction has only a date). */
    val minuteOfDay: Int? = null
)

/** What the guessers know about the capture being guessed. */
data class CaptureFacts(
    val merchantNorm: String,
    val type: String,
    val amount: Double,
    val at: LocalDateTime
) {
    val minuteOfDay: Int get() = at.hour * 60 + at.minute
}

internal fun LocalDate.isWeekend(): Boolean = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
