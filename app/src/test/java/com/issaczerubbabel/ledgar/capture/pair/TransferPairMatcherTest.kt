package com.issaczerubbabel.ledgar.capture.pair

import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import org.junit.Assert.assertEquals
import org.junit.Test

class TransferPairMatcherTest {

    private val hour = 60 * 60 * 1000L
    private val aliases = mapOf("1111" to HDFC, "2222" to CUB)

    private fun capture(
        id: Long,
        direction: String,
        amount: Double = 5_000.0,
        hint: String? = "2222",
        txnTime: Long = T0,
        status: String = "PENDING"
    ) = CapturedTransaction(
        id = id, sources = "NOTIF", sender = "Bank", rawText = "", rawHash = "h$id", capturedAt = txnTime, txnTime = txnTime,
        amount = amount, direction = direction, channel = "UPI", accountHint = hint, status = status
    )

    private val hdfcDebit = capture(1, "DEBIT", hint = "1111")

    private fun match(vararg waiting: CapturedTransaction, amount: Double = 5_000.0) =
        TransferPairMatcher.match(hdfcDebit, amount, CUB, waiting.toList()) { aliases[it] }

    @Test
    fun theCubCreditForTheSameAmountIsTheOtherHalf() {
        val cubCredit = capture(2, "CREDIT", txnTime = T0 + 3 * hour)

        assertEquals(TransferPairing.One(cubCredit), match(cubCredit))
    }

    @Test
    fun twoCandidatesPairNeither() {
        assertEquals(TransferPairing.Ambiguous(2), match(capture(2, "CREDIT"), capture(3, "CREDIT", txnTime = T0 + hour)))
    }

    @Test
    fun amountsAreComparedToThePaisa() {
        assertEquals(TransferPairing.None, match(capture(2, "CREDIT", amount = 5_000.01)))
        assertEquals(TransferPairing.One(capture(3, "CREDIT", amount = 5_000.004)), match(capture(3, "CREDIT", amount = 5_000.004)))
    }

    @Test
    fun anEditedAmountIsTheOneMatched() {
        val credit = capture(2, "CREDIT", amount = 4_999.0)

        assertEquals(TransferPairing.One(credit), match(credit, amount = 4_999.0))
    }

    @Test
    fun onlyWithinTwoDays() {
        assertEquals(TransferPairing.None, match(capture(2, "CREDIT", txnTime = T0 + 49 * hour)))
        assertEquals(TransferPairing.One(capture(3, "CREDIT", txnTime = T0 - 48 * hour)), match(capture(3, "CREDIT", txnTime = T0 - 48 * hour)))
    }

    @Test
    fun itNeedsTheOtherAccountsAlias() {
        assertEquals("no digits", TransferPairing.None, match(capture(2, "CREDIT", hint = null)))
        assertEquals("unknown digits", TransferPairing.None, match(capture(3, "CREDIT", hint = "9999")))
        assertEquals("another Account's digits", TransferPairing.None, match(capture(4, "CREDIT", hint = "1111")))
    }

    @Test
    fun theSameDirectionResolvedOrItselfIsNeverAPair() {
        assertEquals(TransferPairing.None, match(capture(2, "DEBIT")))
        assertEquals(TransferPairing.None, match(capture(3, "CREDIT", status = "CONFIRMED")))
        assertEquals(TransferPairing.None, match(hdfcDebit))
    }

    private companion object {
        const val HDFC = 1L
        const val CUB = 2L
        const val T0 = 1_760_000_000_000L
    }
}
