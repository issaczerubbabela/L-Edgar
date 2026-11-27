package com.issaczerubbabel.ledgar.capture.pair

import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferSuggestionsTest {

    private val hour = 60 * 60 * 1000L

    private fun capture(
        id: Long,
        direction: String,
        accountId: Long?,
        amount: Double = 5_000.0,
        txnTime: Long = T0,
        status: String = "PENDING"
    ) = CapturedTransaction(
        id = id, sources = "NOTIF", sender = "Bank", rawText = "", rawHash = "h$id", capturedAt = txnTime, txnTime = txnTime,
        amount = amount, direction = direction, channel = "IMPS", accountHint = "$id$id", accountId = accountId, status = status
    )

    private fun find(vararg waiting: CapturedTransaction, split: Set<Long> = emptySet()) =
        TransferSuggestions.find(waiting.toList(), split) { it.accountId }

    @Test
    fun anAliasedDebitAndAnUnknownCreditOnTheSameDayAreOneSuggestedTransfer() {
        val debit = capture(1, "DEBIT", HDFC)
        val credit = capture(2, "CREDIT", null, txnTime = T0 + 3 * hour)

        assertEquals(listOf(TransferSuggestion(debit, credit)), find(debit, credit))
    }

    @Test
    fun anUnknownDebitAndAnAliasedCreditAreSuggestedToo() {
        val debit = capture(1, "DEBIT", null)
        val credit = capture(2, "CREDIT", CUB)

        assertEquals(listOf(TransferSuggestion(debit, credit)), find(debit, credit))
    }

    @Test
    fun withNeitherSideAliasedAmountAndDateAloneNeverPairThem() {
        assertTrue(find(capture(1, "DEBIT", null), capture(2, "CREDIT", null)).isEmpty())
    }

    @Test
    fun bothSidesOnTheSameAccountAreNoTransfer() {
        assertTrue(find(capture(1, "DEBIT", HDFC), capture(2, "CREDIT", HDFC)).isEmpty())
    }

    @Test
    fun theAmountMustMatchToThePaisaWithinTwoDays() {
        assertTrue(find(capture(1, "DEBIT", HDFC), capture(2, "CREDIT", CUB, amount = 5_000.01)).isEmpty())
        assertTrue(find(capture(1, "DEBIT", HDFC), capture(2, "CREDIT", CUB, txnTime = T0 + 49 * hour)).isEmpty())
        assertEquals(1, find(capture(1, "DEBIT", HDFC), capture(2, "CREDIT", CUB, txnTime = T0 - 47 * hour)).size)
    }

    @Test
    fun twoPossibleCreditsOrTwoPossibleDebitsSuggestNothing() {
        assertTrue(find(capture(1, "DEBIT", HDFC), capture(2, "CREDIT", CUB), capture(3, "CREDIT", null, txnTime = T0 + hour)).isEmpty())
        assertTrue(find(capture(1, "DEBIT", HDFC), capture(2, "DEBIT", null, txnTime = T0 + hour), capture(3, "CREDIT", CUB)).isEmpty())
    }

    @Test
    fun aResolvedOrSplitCaptureIsNeverSuggested() {
        assertTrue(find(capture(1, "DEBIT", HDFC), capture(2, "CREDIT", CUB, status = "CONFIRMED")).isEmpty())
        assertTrue(find(capture(1, "DEBIT", HDFC), capture(2, "CREDIT", CUB), split = setOf(1L)).isEmpty())
    }

    @Test
    fun aPossibleDuplicateStillWaitsSoItCanBeSuggested() {
        assertEquals(1, find(capture(1, "DEBIT", HDFC, status = "POSSIBLE_DUPLICATE"), capture(2, "CREDIT", CUB)).size)
    }

    private companion object {
        const val T0 = 1_760_000_000_000L
        const val HDFC = 10L
        const val CUB = 20L
    }
}
