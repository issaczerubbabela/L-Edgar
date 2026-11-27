package com.issaczerubbabel.ledgar.capture.pair

import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction

/**
 * A waiting debit and credit that look like one Transfer between the user's own Accounts: money
 * leaving [debit]'s Account and arriving on [credit]'s. Only a suggestion: the user confirms it as
 * one Transfer or splits it back into two captures (ADR-0005, ADR-0006).
 */
data class TransferSuggestion(val debit: CapturedTransaction, val credit: CapturedTransaction)

/**
 * Finds Suggested Transfers among waiting captures (ADR-0006): a debit and a credit that are each
 * other's only possible other half by [TransferPairMatcher.couldBeOtherHalf] (same amount to the
 * paisa, within 2 days), where at least one side's own Account is known. Amount and date alone never
 * pair them. Both sides on the same Account is no Transfer, and a pair either side has been split
 * from ([CapturedTransaction] ids in [split]) is never suggested again.
 */
object TransferSuggestions {

    /**
     * [ownAccountOf] is a capture's Account from its digits' Account alias, or null when they have
     * none: the own-account signal.
     */
    fun find(
        waiting: List<CapturedTransaction>,
        split: Set<Long> = emptySet(),
        ownAccountOf: (CapturedTransaction) -> Long?
    ): List<TransferSuggestion> {
        val open = waiting.filter { it.status in WAITING && it.id !in split }
        val debits = open.filter { it.direction != "CREDIT" }
        val credits = open.filter { it.direction == "CREDIT" }

        fun halves(capture: CapturedTransaction, others: List<CapturedTransaction>) =
            others.filter { TransferPairMatcher.couldBeOtherHalf(capture, capture.amount, it) }

        return debits.mapNotNull { debit ->
            val credit = halves(debit, credits).singleOrNull() ?: return@mapNotNull null
            // Each must be the other's only match: with two candidates either way, nothing is suggested.
            if (halves(credit, debits).singleOrNull()?.id != debit.id) return@mapNotNull null
            val from = ownAccountOf(debit)
            val to = ownAccountOf(credit)
            when {
                from == null && to == null -> null
                from != null && from == to -> null
                else -> TransferSuggestion(debit, credit)
            }
        }
    }

    private val WAITING = setOf("PENDING", "POSSIBLE_DUPLICATE")
}
