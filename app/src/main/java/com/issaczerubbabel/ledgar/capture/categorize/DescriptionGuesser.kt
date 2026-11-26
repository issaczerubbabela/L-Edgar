package com.issaczerubbabel.ledgar.capture.categorize

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

/**
 * A capture's Suggested description: [title] is filled in as its title (marked as a guess), and up
 * to [DescriptionGuesser.MAX_ALTERNATIVES] [alternatives] are offered as chips. Empty when there is
 * nothing worth guessing.
 *
 * [perPayment] is true when the merchant itself has been given two or more different Descriptions
 * (Lunch and Tea at one canteen): its Descriptions then name the payment, not the merchant, so the
 * guess is the title even over a saved rename, which is otherwise the merchant's name.
 */
data class DescriptionGuess(
    val title: String?,
    val alternatives: List<String> = emptyList(),
    val perPayment: Boolean = false
) {
    /** Every guessed Description, the title first. */
    val all: List<String> get() = listOfNotNull(title) + alternatives

    companion object {
        val NONE = DescriptionGuess(null)
    }
}

/**
 * Guesses a capture's Description from the user's Transactions, by merchant, amount, time of day
 * and weekday. A merchant seen before outweighs everything else: when the merchant has history,
 * only its own Descriptions are ranked, and amount and time decide between them (₹80 at 1pm is
 * "Lunch", ₹30 at 4:30pm is "Tea"). With no history for the merchant, a Description is only
 * guessed when several close Transactions agree. It never touches the Category or the Confidence
 * band. A saved rename on the merchant's rule wins over it unless [DescriptionGuess.perPayment]
 * (that choice is made where the title is shown, `CaptureTrace.guessWinsOver`).
 */
object DescriptionGuesser {

    const val MAX_ALTERNATIVES = 2

    /** How close in rupees, on a ratio scale: ₹80 vs ₹90 scores about 0.7, ₹80 vs ₹30 about 0.04. */
    private const val AMOUNT_SCALE = 0.3

    /** Minutes apart at which time of day counts a third as much. */
    private const val TIME_SCALE_MINUTES = 120.0

    /** Unknown time (a hand-typed Transaction) counts as half a match. */
    private const val UNKNOWN_TIME = 0.5

    /** A weekend spend compared with a weekday one. */
    private const val OTHER_DAY_KIND = 0.7

    /** A use this many days old counts half as much as one from today. */
    private const val HALF_LIFE_DAYS = 90.0

    /** Without the merchant's own history, the evidence a Description needs, about three close spends. */
    private const val MIN_SCORE_WITHOUT_MERCHANT = 1.5

    private val GENERIC = setOf("payment", "money received")

    /**
     * [ignore] holds texts that aren't worth suggesting because the card shows them anyway, such as
     * the bank's own merchant text.
     */
    fun guess(
        facts: CaptureFacts,
        history: List<PastTransaction>,
        ignore: Collection<String> = emptyList(),
        today: LocalDate = facts.at.toLocalDate()
    ): DescriptionGuess {
        val skip = (ignore.map { normalize(it) } + GENERIC).toSet()
        val candidates = history.filter { it.type == facts.type && normalize(it.description).let { d -> d.isNotEmpty() && d !in skip } }

        val ownHistory = facts.merchantNorm.takeIf { it.isNotBlank() }
            ?.let { merchant -> candidates.filter { it.merchantNorm == merchant } }
            .orEmpty()
        val pool = ownHistory.ifEmpty { candidates }
        val minScore = if (ownHistory.isNotEmpty()) 0.0 else MIN_SCORE_WITHOUT_MERCHANT

        val ranked = pool
            .groupBy { normalize(it.description) }
            .map { (_, uses) ->
                val newest = uses.maxWith(compareBy<PastTransaction> { it.date }.thenBy { it.id })
                newest.description.trim().replace(WHITESPACE, " ") to uses.sumOf { similarity(facts, it, today) }
            }
            .filter { it.second > minScore }
            .sortedByDescending { it.second }
            .map { it.first }

        if (ranked.isEmpty()) return DescriptionGuess.NONE
        return DescriptionGuess(
            title = ranked.first(),
            alternatives = ranked.drop(1).take(MAX_ALTERNATIVES),
            perPayment = ownHistory.isNotEmpty() && ranked.size > 1
        )
    }

    private fun similarity(facts: CaptureFacts, past: PastTransaction, today: LocalDate): Double {
        val amount = exp(-abs(ln(facts.amount.coerceAtLeast(0.01) / past.amount.coerceAtLeast(0.01))) / AMOUNT_SCALE)
        val time = past.minuteOfDay?.let { exp(-minutesApart(facts.minuteOfDay, it) / TIME_SCALE_MINUTES) } ?: UNKNOWN_TIME
        val day = if (past.date.isWeekend() == facts.at.toLocalDate().isWeekend()) 1.0 else OTHER_DAY_KIND
        val recency = 0.5.pow(ChronoUnit.DAYS.between(past.date, today).coerceAtLeast(0) / HALF_LIFE_DAYS)
        return amount * time * day * recency
    }

    private fun minutesApart(a: Int, b: Int): Double {
        val d = abs(a - b)
        return min(d, 24 * 60 - d).toDouble()
    }

    private fun normalize(text: String) = text.trim().replace(WHITESPACE, " ").lowercase()

    private val WHITESPACE = Regex("\\s+")
}
