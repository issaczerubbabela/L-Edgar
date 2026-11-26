package com.issaczerubbabel.ledgar.capture.pair

import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import kotlin.math.abs
import kotlin.math.roundToLong

/** What confirming a Transfer does to the capture on its other side. */
sealed interface TransferPairing {
    /** No waiting capture looks like the other half. */
    data object None : TransferPairing

    /** Exactly one does: it is resolved with the Transfer. */
    data class One(val capture: CapturedTransaction) : TransferPairing

    /** Several do, so none is paired: they stay waiting for the user. */
    data class Ambiguous(val count: Int) : TransferPairing
}

/**
 * Finds the other half of a Transfer being confirmed (ADR-0006): a waiting capture in the opposite
 * direction, for the same amount to the paisa, within [WINDOW_MS] of it, whose account digits are an
 * Account alias of the Transfer's other Account. The alias is the own-account signal: amount and
 * date alone would pair two unrelated payments that happen to match.
 */
object TransferPairMatcher {

    const val WINDOW_MS = 2L * 24 * 60 * 60 * 1000

    fun opposite(direction: String): String = if (direction == "CREDIT") "DEBIT" else "CREDIT"

    /**
     * [transfer] is the capture being confirmed, for [amount] (the Transaction's, which an edit may
     * have corrected). [waiting] are captures still waiting, and [aliasAccountId] resolves account
     * digits to an Account id through the saved Account aliases.
     */
    fun match(
        transfer: CapturedTransaction,
        amount: Double,
        otherAccountId: Long,
        waiting: List<CapturedTransaction>,
        aliasAccountId: (String) -> Long?
    ): TransferPairing {
        val direction = opposite(transfer.direction)
        val paise = paise(amount)
        val matches = waiting.filter { candidate ->
            candidate.id != transfer.id &&
                candidate.status in WAITING &&
                candidate.direction == direction &&
                paise(candidate.amount) == paise &&
                abs(candidate.txnTime - transfer.txnTime) <= WINDOW_MS &&
                candidate.accountHint?.let(aliasAccountId) == otherAccountId
        }
        return when (matches.size) {
            0 -> TransferPairing.None
            1 -> TransferPairing.One(matches.single())
            else -> TransferPairing.Ambiguous(matches.size)
        }
    }

    private fun paise(amount: Double): Long = (amount * 100).roundToLong()

    private val WAITING = setOf("PENDING", "POSSIBLE_DUPLICATE")
}
