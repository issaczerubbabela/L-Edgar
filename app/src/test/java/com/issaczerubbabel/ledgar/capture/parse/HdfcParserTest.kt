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

    // Synthetic: no real HDFC alert addressed to a VPA has been captured yet.
    @Test
    fun parsesAVpaDebitWithTheHandleAsMerchant() {
        val text = "Rs.250.00 debited from HDFC Bank A/c XX1234 to VPA swiggy.stores@axb on 12-11-26. " +
            "Not you? Call 18002586161/SMS BLOCK UPI to 7308080808"

        val parsed = parser.parse(text)!!

        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("swiggy.stores@axb", parsed.merchantRaw)
    }

    @Test
    fun parsesANeftDeduction() {
        val parsed = parser.parse(
            "Amt Deducted! Rs.5000.00 from your HDFC Bank A/c XX1234 for NEFT txn via HDFC Bank Online Banking " +
                "Not you?Call 18002586161/SMS BLOCK OB to 7308080808"
        )!!

        assertEquals(5000.0, parsed.amount, 0.001)
        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("1234", parsed.accountHint)
        assertEquals(Channel.NEFT, parsed.channel)
    }

    @Test
    fun parsesANetBankingPaymentWithThePayeeAsMerchant() {
        val parsed = parser.parse(
            "Payment Successful! Rs. 2500.00 from A/c **********1234 to PAYUAMAZON via HDFC Bank NetBanking. " +
                "Not you?Call 18002586161/SMS BLOCK OB to 7308080808"
        )!!

        assertEquals(2500.0, parsed.amount, 0.001)
        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("1234", parsed.accountHint)
        assertEquals("PAYUAMAZON", parsed.merchantRaw)
    }

    @Test
    fun parsesAnRdBookingAsADebitToTheRd() {
        val parsed = parser.parse("RD Booked! Your HDFC Bank RD XX5678 is booked on 12OCT-26 for INR 2000")!!

        assertEquals(2000.0, parsed.amount, 0.001)
        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("5678", parsed.counterpartyAccountHint)
        assertNull(parsed.accountHint)
    }

    @Test
    fun parsesAnRdInstalmentThroughMobileAsADebitToTheRd() {
        val parsed = parser.parse(
            "UPDATE: INR 2,000.00 debited from HDFC Bank XX1234 on 12-NOV-26 Info: RD through MOBILE-XXXXXXXXXX5678 " +
                ":<NAME>. Avl bal:INR 10,000.00"
        )!!

        assertEquals(2000.0, parsed.amount, 0.001)
        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("1234", parsed.accountHint)
        assertEquals("5678", parsed.counterpartyAccountHint)
    }

    @Test
    fun stillParsesACreditAlertAndAMandateDebit() {
        val credit = parser.parse(
            "Credit Alert! Rs.500.00 credited to HDFC Bank A/c XX1234 on 12-11-26 from VPA xxxxxxxxxx@ybl (UPI 111111111111)"
        )!!
        assertEquals(500.0, credit.amount, 0.001)
        assertEquals(Direction.CREDIT, credit.direction)
        assertEquals("1234", credit.accountHint)

        val mandate = parser.parse(
            "UPI Mandate: Sent Rs.2000.00 from HDFC Bank A/c 1234 To ZERODHA BROKING LTD On 05/11/26 Ref 111111111111 Not You? Call 18002586161"
        )!!
        assertEquals(2000.0, mandate.amount, 0.001)
        assertEquals(Direction.DEBIT, mandate.direction)
        assertEquals("1234", mandate.accountHint)
        assertEquals("ZERODHA BROKING LTD", mandate.merchantRaw)
    }
}
