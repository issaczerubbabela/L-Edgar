package com.issaczerubbabel.ledgar.capture.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CubParserTest {

    private val parser = CubParser()

    @Test
    fun parsesCreditWithCounterpartyDebit() {
        val text = "Your a/c no. XXXXXXXX1234 is credited for Rs.35.00 on 14-09-2026 and debited " +
            "from a/c no. XXXXXXXX5424 (UPI Ref no 62575731739387) -CUB"

        assertTrue(parser.canParse("CUB", text))
        val parsed = parser.parse(text)!!

        assertEquals(35.00, parsed.amount, 0.001)
        assertEquals(Direction.CREDIT, parsed.direction)
        assertEquals("1234", parsed.accountHint)
        assertEquals("5424", parsed.counterpartyAccountHint)
        assertEquals("62575731739387", parsed.refNumber)
        assertEquals(Channel.UPI, parsed.channel)
    }

    @Test
    fun parsesDebitWithCounterpartyCredit() {
        val text = "Your a/c no. XXXXXXXX1234 is debited for Rs.120.00 on 21-06-2026 and credited " +
            "to a/c no. XXXXXXXX1234 (UPI Ref no 653801123456)"

        val parsed = parser.parse(text)!!

        assertEquals(120.00, parsed.amount, 0.001)
        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("1234", parsed.accountHint)
        assertEquals("653801123456", parsed.refNumber)
    }

    @Test
    fun doesNotClaimUnrelatedText() {
        assertEquals(false, parser.canParse("HDFC Bank", "Rs.100 debited from HDFC Bank A/c XX1234"))
    }
}
