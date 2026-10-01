package com.issaczerubbabel.ledgar.capture.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HdfcParserTest {

    private val parser = HdfcParser()

    @Test
    fun parsesUpiP2pSend() {
        val text = """
            Sent Rs.70.00
            From HDFC Bank A/C *1234
            To Mrs Jane Doe
            On 26/09/26
            Ref 66353123456788
            Not You?
            Call 123456789/SMS BLOCK UPI to 7300123456
        """.trimIndent()

        assertTrue(parser.canParse("HDFC Bank", text))
        val parsed = parser.parse(text)!!

        assertEquals(70.00, parsed.amount, 0.001)
        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("1234", parsed.accountHint)
        assertEquals("Mrs Jane Doe", parsed.merchantRaw)
        assertEquals("66353123456788", parsed.refNumber)
        assertEquals(Channel.UPI, parsed.channel)
    }

    @Test
    fun parsesUpiAtmWithdrawal() {
        val text = """
            UPI ATM Withdrawal:Rs.2000.
            From:HDFC Bank A/c 1234
            At:HDFC ATM ATM
            UPI RRN:663586931247 on 26/09/26
            Not you? Call 18002586161/SMS BLOCK UPI to 7308080808
        """.trimIndent()

        val parsed = parser.parse(text)!!

        assertEquals(2000.0, parsed.amount, 0.001)
        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("1234", parsed.accountHint)
        assertEquals("ATM Withdrawal", parsed.merchantRaw)
        assertEquals("663586931247", parsed.refNumber)
        assertEquals(Channel.ATM, parsed.channel)
    }

    @Test
    fun parsesDepositNarration() {
        val text = "Update! INR 2,55,758.00 deposited in HDFC Bank A/c XX1234 on 25-SEP-26 for " +
            "WFISPL CREDIT.Avl bal INR 5,89,777.71. Cheque deposits in A/C are subject to clearing"

        val parsed = parser.parse(text)!!

        assertEquals(255758.00, parsed.amount, 0.001)
        assertEquals(Direction.CREDIT, parsed.direction)
        assertEquals("1234", parsed.accountHint)
        assertEquals("WFISPL", parsed.merchantRaw)
    }

    @Test
    fun rejectsOtpMessages() {
        val text = "123456 is your OTP for txn of Rs.2,500 at FLIPKART. Do not share."

        assertNull(parser.parse(text))
    }

    @Test
    fun canParseRequiresHdfcAndAnAmount() {
        assertTrue(parser.canParse("HDFC Bank", "Rs.100 debited from HDFC Bank A/c XX1234"))
        assertEquals(false, parser.canParse("City Union Bank", "Rs.100 debited"))
        assertEquals(false, parser.canParse("HDFC Bank", "No amount mentioned here"))
    }
}
