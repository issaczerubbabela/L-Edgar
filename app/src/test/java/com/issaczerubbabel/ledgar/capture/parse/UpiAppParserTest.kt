package com.issaczerubbabel.ledgar.capture.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiAppParserTest {

    private val parser = UpiAppParser()

    @Test
    fun parsesGooglePayPayment() {
        val text = "Paid ₹500 to Ramesh Kumar.\n" +
            "₹500.00 debited from Bank Account (XXXX1234). UPI Ref: 632112345678."

        assertTrue(parser.canParse("Google Pay", text))
        val parsed = parser.parse(text)!!

        assertEquals(500.0, parsed.amount, 0.001)
        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("Ramesh Kumar", parsed.merchantRaw)
        assertEquals("1234", parsed.accountHint)
        assertEquals("632112345678", parsed.refNumber)
        assertEquals(Channel.UPI, parsed.channel)
    }

    @Test
    fun parsesGooglePayReceipt() {
        val text = "Received ₹1,200 from Sneha Sharma.\n₹1,200.00 credited to Bank Account (XXXX1234)."

        val parsed = parser.parse(text)!!

        assertEquals(1200.0, parsed.amount, 0.001)
        assertEquals(Direction.CREDIT, parsed.direction)
        assertEquals("Sneha Sharma", parsed.merchantRaw)
        assertEquals("1234", parsed.accountHint)
        assertNull(parsed.refNumber)
    }

    @Test
    fun keepsPeriodsInsidePayeeNames() {
        val text = "Paid ₹80 to Dr. A. Rao.\n₹80.00 debited from Bank Account (XXXX1234)."

        assertEquals("Dr. A. Rao", parser.parse(text)!!.merchantRaw)
    }

    @Test
    fun handlesPaymentWithoutSecondSentence() {
        assertEquals("Ramesh Kumar", parser.parse("Paid ₹500 to Ramesh Kumar.")!!.merchantRaw)
    }

    @Test
    fun onlyClaimsGooglePayAndPaytmSenders() {
        assertFalse(parser.canParse("HDFC Bank", "Paid ₹500 to Ramesh Kumar."))
        assertTrue(parser.canParse("Paytm", "Paid ₹500 to Ramesh Kumar."))
    }
}
