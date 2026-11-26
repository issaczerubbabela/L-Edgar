package com.issaczerubbabel.ledgar.capture.pair

import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Finds the Transaction already in the Ledger that a capture is probably the bank's alert for: one
 * logged by hand, with Quick Add or by a Repeat rule. It only suggests; the user decides with Same
 * one or Add anyway (ADR-0005).
 *
 * A Transaction matches when it has the same amount to the paisa, the capture's direction (a debit
 * is an Expense, a credit an Income; Transfers and Balance adjustments never match), a date within
 * [WINDOW_DAYS] of the capture's, the same Account when both have one, isn't waiting to be deleted,
 * and no capture has been confirmed from or linked to it yet: each Transaction absorbs one alert.
 */
object LedgerMatcher {

    /** UPI and card alerts arrive within minutes; this also covers a spend logged either side of midnight. */
    const val WINDOW_DAYS = 3L

    /** How far back Transactions are offered at all: a month of waiting captures plus the window. */
    const val LOOKBACK_DAYS = 35L

    /**
     * The best match for [capture] among [candidates], or null. [linkedIds] are Transactions a
     * capture already resolved (its `confirmedExpenseId`). With several, the one on the capture's
     * Account comes first, then the nearest date, then the most recently added.
     */
    fun bestMatch(
        capture: CapturedTransaction,
        candidates: List<ExpenseRecord>,
        linkedIds: Set<Long>,
        zone: ZoneId
    ): ExpenseRecord? = matches(capture, candidates, linkedIds, zone).firstOrNull()

    /** Every match for [capture], best first. */
    fun matches(
        capture: CapturedTransaction,
        candidates: List<ExpenseRecord>,
        linkedIds: Set<Long>,
        zone: ZoneId
    ): List<ExpenseRecord> {
        val captureDate = dateOf(capture, zone)
        val paise = paise(capture.amount)
        val type = typeFor(capture.direction)
        return candidates
            .mapNotNull { record ->
                val gap = gapDays(record, captureDate) ?: return@mapNotNull null
                val accountMismatch = capture.accountId != null && record.accountId != null && record.accountId != capture.accountId
                val ok = record.id !in linkedIds &&
                    record.syncAction != "DELETE" &&
                    record.type == type &&
                    paise(record.amount) == paise &&
                    gap <= WINDOW_DAYS &&
                    !accountMismatch
                if (ok) Ranked(record, sameAccount = capture.accountId != null && record.accountId == capture.accountId, gap) else null
            }
            .sortedWith(compareByDescending<Ranked> { it.sameAccount }.thenBy { it.gap }.thenByDescending { it.record.id })
            .map { it.record }
    }

    /** Whether [record] matches [capture] by the rule above: the re-check before a link is saved. */
    fun isMatch(capture: CapturedTransaction, record: ExpenseRecord, linkedIds: Set<Long>, zone: ZoneId): Boolean =
        matches(capture, listOf(record), linkedIds, zone).isNotEmpty()

    /** The Transaction type a capture in [direction] can match. */
    fun typeFor(direction: String): String = if (direction == "CREDIT") TransactionType.INCOME else TransactionType.EXPENSE

    fun dateOf(capture: CapturedTransaction, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(capture.txnTime).atZone(zone).toLocalDate()

    private fun gapDays(record: ExpenseRecord, captureDate: LocalDate): Long? {
        val date = runCatching { LocalDate.parse(record.date) }.getOrNull() ?: return null
        return abs(ChronoUnit.DAYS.between(date, captureDate))
    }

    private fun paise(amount: Double): Long = (amount * 100).roundToLong()

    private data class Ranked(val record: ExpenseRecord, val sameAccount: Boolean, val gap: Long)
}
