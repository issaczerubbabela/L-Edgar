package com.issaczerubbabel.ledgar.capture.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AxisParserTest {

    private val parser = AxisParser()

    @Test
    fun parsesUpiMerchantDebit() {
        val text = "INR 100.00 was debited from your A/c no. XX1236 on 19-12-24 at 10:19:54 IST via " +
            "UPI/P2M/435476373861/Shri Ujagar Fuels. Avail. Bal: INR 5,420.10."

        assertTrue(parser.canParse("AXISBK", text))
        val parsed = parser.parse(text)!!

        assertEquals(100.00, parsed.amount, 0.001)
        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("1236", parsed.accountHint)
        assertEquals("Shri Ujagar Fuels", parsed.merchantRaw)
        assertEquals("435476373861", parsed.refNumber)
        assertEquals(Channel.UPI, parsed.channel)
    }

    @Test
    fun parsesImpsCredit() {
        val text = "Dear Customer, INR 5,000.00 has been credited to your Axis Bank A/c XX3113 on " +
            "28-03-26 by IMPS Ref 608721234567. Avail. Bal: INR 10,420.10."

        val parsed = parser.parse(text)!!

        assertEquals(5000.00, parsed.amount, 0.001)
        assertEquals(Direction.CREDIT, parsed.direction)
        assertEquals("3113", parsed.accountHint)
        assertNull(parsed.merchantRaw)
        assertEquals("608721234567", parsed.refNumber)
        assertEquals(Channel.IMPS, parsed.channel)
    }

    @Test
    fun usesTheTransactionAmountNotTheBalance() {
        val text = "INR 100.00 was debited from your A/c no. XX1236 via UPI/P2M/435476373861/Shop. " +
            "Avail. Bal: INR 5,420.10."

        assertEquals(100.00, parser.parse(text)!!.amount, 0.001)
    }

    @Test
    fun doesNotClaimUnrelatedText() {
        assertEquals(false, parser.canParse("HDFC Bank", "Rs.100 debited from HDFC Bank A/c XX1234"))
    }
}
