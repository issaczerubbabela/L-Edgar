package com.issaczerubbabel.ledgar.capture.pair

import com.issaczerubbabel.ledgar.capture.source.AlertSource
import kotlin.math.abs
import kotlin.math.roundToLong

/** One Alert as [SamePaymentMatcher] sees it. [arrivedAt] is when its notification was posted. */
data class AlertFacts(
    val source: AlertSource,
    val amount: Double,
    /** DEBIT or CREDIT, as the Alert says. */
    val direction: String,
    val refNumber: String?,
    val arrivedAt: Long
)

/** One of a capture's Alerts, as far as matching needs it: its source and when it arrived. */
data class AlertSeen(val source: AlertSource, val arrivedAt: Long)

/** A Captured transaction as [SamePaymentMatcher] sees it: what it says, and the Alerts it came from. */
data class MergeCandidate(
    val captureId: Long,
    val status: String,
    val amount: Double,
    val direction: String,
    /** At most one: two different reference numbers are never merged. */
    val refNumber: String?,
    val alerts: List<AlertSeen>,
    /** Sources the user split off with "Not the same payment" ([com.issaczerubbabel.ledgar.capture.CaptureTrace.keptApartFrom]). */
    val keptApartFrom: Set<AlertSource> = emptySet()
) {
    val isWaiting: Boolean get() = status == "PENDING" || status == "POSSIBLE_DUPLICATE"
}

/**
 * Finds the Captured transaction a new Alert repeats, from another Alert source (ADR-0012). They
 * match on an equal reference number, or, when at most one of them shows one, on the same amount to
 * the paisa within [WINDOW_MS] of any of the capture's Alerts. Either way the direction must agree,
 * so a reversal that reuses a payment's reference stays its own capture.
 *
 * Two different reference numbers never match, and an Alert never joins a capture that already has
 * one from its own source, or that the user split from it: nothing but a reference tells two
 * identical payments from one source apart (the `rawHash` handles a repeat of one Alert).
 */
object SamePaymentMatcher {

    /** The window the Google Pay dedupe already uses. */
    const val WINDOW_MS = 10 * 60 * 1000L

    /**
     * The capture [alert] belongs to, or null for a new payment. A reference match wins, then a
     * capture still waiting (a late Alert enriches the card rather than a resolved capture), then the
     * nearest in time. A resolved capture still matches, so its late Alert attaches quietly.
     */
    fun pick(alert: AlertFacts, candidates: List<MergeCandidate>): MergeCandidate? =
        candidates.filter { matches(alert, it) }
            .minWithOrNull(
                compareBy<MergeCandidate> { if (alert.refNumber != null && it.refNumber == alert.refNumber) 0 else 1 }
                    .thenBy { if (it.isWaiting) 0 else 1 }
                    .thenBy { candidate -> candidate.alerts.minOfOrNull { abs(it.arrivedAt - alert.arrivedAt) } ?: Long.MAX_VALUE }
                    .thenBy { it.captureId }
            )

    fun matches(alert: AlertFacts, candidate: MergeCandidate): Boolean {
        if (candidate.alerts.any { it.source == alert.source } || alert.source in candidate.keptApartFrom) return false
        if (candidate.direction != alert.direction) return false
        if (alert.refNumber != null && candidate.refNumber != null) return alert.refNumber == candidate.refNumber
        return paise(candidate.amount) == paise(alert.amount) &&
            candidate.alerts.any { abs(it.arrivedAt - alert.arrivedAt) <= WINDOW_MS }
    }

    private fun paise(amount: Double): Long = (amount * 100).roundToLong()
}
