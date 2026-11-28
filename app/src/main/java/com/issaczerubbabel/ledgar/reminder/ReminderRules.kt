package com.issaczerubbabel.ledgar.reminder

import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.util.formatRupees
import java.time.Instant
import java.time.ZoneId

/**
 * Pure scheduling rules for the daily reminder: when it next fires, and whether it should fire at
 * all. Kept free of Android classes so the DST and boundary cases run as plain JVM tests.
 */
object ReminderRules {

    /**
     * The next instant after [now] that is [minuteOfDay] wall-clock minutes (0..1439) into the day
     * in [zone]. Builds the candidate with [java.time.LocalDateTime] arithmetic before resolving the
     * zone, so a DST transition between midnight and the target time shifts the UTC instant rather
     * than the wall-clock time the user picked.
     */
    fun nextReminderAt(now: Instant, minuteOfDay: Int, zone: ZoneId): Instant {
        val nowZoned = now.atZone(zone)
        val todayAtTime = nowZoned.toLocalDate().atStartOfDay().plusMinutes(minuteOfDay.toLong())
        val todayCandidate = todayAtTime.atZone(zone)
        val target = if (todayCandidate.isAfter(nowZoned)) todayCandidate else todayAtTime.plusDays(1).atZone(zone)
        return target.toInstant()
    }

    /** True when [lastLoggedEpochDay] isn't today, i.e. nothing has been logged on the phone yet. */
    fun shouldRemind(todayEpochDay: Long, lastLoggedEpochDay: Long?): Boolean =
        lastLoggedEpochDay != todayEpochDay

    /**
     * The notification's text. With captures [waiting], how many and what their debits add up to
     * ("6 waiting to review · ₹1,240"), then Left to spend if a cycle has money left: the day's
     * spends are already there to confirm, so there's nothing to type. Otherwise "₹X left to spend
     * this cycle" while a Salary cycle is running with money left, else one of a few short lines
     * picked by the day so it doesn't go stale.
     */
    fun message(todayEpochDay: Long, leftToSpend: Double?, waiting: WaitingCaptures = WaitingCaptures.NONE): String {
        val left = leftToSpend?.takeIf { it > 0.0 }?.let(::formatRupees)
        if (waiting.any) {
            val total = if (waiting.debitTotal > 0.0) " · ${formatRupees(waiting.debitTotal)}" else ""
            return "${waiting.count} waiting to review$total." + (left?.let { " $it left to spend this cycle." } ?: "")
        }
        if (left != null) return "$left left to spend this cycle. Add today's spending to keep it right."
        return MESSAGES[Math.floorMod(todayEpochDay, MESSAGES.size.toLong()).toInt()]
    }

    private val MESSAGES = listOf(
        "Add today's spending before you forget.",
        "A minute now beats guessing later. Log today's spends.",
        "Nothing logged yet today. Quick Add takes a few taps.",
        "Keep Left to spend accurate. Add what you spent today."
    )
}

/**
 * Captured transactions waiting in To review (status PENDING or POSSIBLE_DUPLICATE) when the
 * reminder fires: how many, and the total of the debits among them.
 */
data class WaitingCaptures(val count: Int, val debitTotal: Double) {
    val any: Boolean get() = count > 0

    companion object {
        val NONE = WaitingCaptures(count = 0, debitTotal = 0.0)

        /** From the captures waiting in To review; money received doesn't add to the total. */
        fun of(captures: List<CapturedTransaction>): WaitingCaptures = WaitingCaptures(
            count = captures.size,
            debitTotal = captures.filter { it.direction == "DEBIT" }.sumOf { it.amount }
        )
    }
}
