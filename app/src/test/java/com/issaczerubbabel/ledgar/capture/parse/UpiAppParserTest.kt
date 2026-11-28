package com.issaczerubbabel.ledgar.capture.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Samples are real Google Pay notifications read off a phone on 2026-11-28, with names blanked.
 * The listener hands a UPI app's alert over as its title, a newline, then its text.
 */
class UpiAppParserTest {

    private val parser = UpiAppParser()

    @Test
    fun moneyReceivedIsACreditFromThePayerInTheTitle() {
        val text = "A B KUMAR paid you ₹1.00\nTap to view."

        assertTrue(parser.canParse("Google Pay", text))
        val parsed = parser.parse(text)!!

        assertEquals(1.0, parsed.amount, 0.001)
        assertEquals(Direction.CREDIT, parsed.direction)
        assertEquals("A B KUMAR", parsed.merchantRaw)
        assertNull(parsed.accountHint)
        assertNull(parsed.refNumber)
        assertEquals(Channel.UPI, parsed.channel)
    }

    @Test
    fun moneyReceivedKeepsThousandsAndPeriodsInTheName() {
        val parsed = parser.parse("Dr. A. Rao paid you ₹1,250.50\nTap to view.")!!

        assertEquals(1250.5, parsed.amount, 0.001)
        assertEquals("Dr. A. Rao", parsed.merchantRaw)
    }

    @Test
    fun autopayIsADebitToThePayee() {
        val text = "Payment successful\n" +
            "Payment for Autopay of ₹2,000.00 to Zerodha Broking Ltd was successful. Tap to view."

        assertTrue(parser.canParse("Google Pay", text))
        val parsed = parser.parse(text)!!

        assertEquals(2000.0, parsed.amount, 0.001)
        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("Zerodha Broking Ltd", parsed.merchantRaw)
        assertNull(parsed.accountHint)
        assertEquals(Channel.UPI, parsed.channel)
    }

    @Test
    fun offersAndRewardsAreNeverAPayment() {
        listOf(
            "Up to ₹2,500 in welcome rewards\nApply for Flex credit card to unlock now",
            "A's low EMI loan is ready 🎉\nEMI starts at ₹1,000 per month. Check loan offer now!",
            "Earn flat ₹11 cashback 💰\nOn your kid's first Pocket Money payment",
            "Get up to ₹200 cashback\nAdd your Mastercard to earn ₹50 on your first 4 payments",
            "Limited time: Flat 20% cashback on Google Play\nPay with UPI on games, apps & subscriptions"
        ).forEach { offer ->
            assertNull(offer, parser.parse(offer))
            assertNull(offer, ParserRegistry.default().parse("Google Pay", offer))
        }
    }

    @Test
    fun onlyClaimsGooglePayAndPaytmSenders() {
        val text = "A B KUMAR paid you ₹1.00\nTap to view."

        assertFalse(parser.canParse("HDFC Bank", text))
        assertTrue(parser.canParse("Paytm", text))
    }
}
