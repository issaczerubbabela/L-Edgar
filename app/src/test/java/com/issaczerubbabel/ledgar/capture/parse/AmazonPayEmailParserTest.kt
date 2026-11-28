package com.issaczerubbabel.ledgar.capture.parse

import com.issaczerubbabel.ledgar.capture.AccountHint
import com.issaczerubbabel.ledgar.capture.CaptureKinds
import com.issaczerubbabel.ledgar.capture.source.BankEmails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amazon Pay's emails as Gmail's notification gives them: the subject, a newline, then the preview.
 * Every sample is a real email (#181, all six in the mailbox on 2026-11-28 were one of these three)
 * with names, order ids, references and products blanked.
 */
class AmazonPayEmailParserTest {

    private val registry = ParserRegistry.default()
    private val sender = BankEmails.AMAZON_PAY.displayName

    private fun read(text: String): ParsedTxn = registry.parse(sender, text)!!

    @Test
    fun aBalancePaymentIsADebitOnAmazonWithItsOrderId() {
        val parsed = read(
            "Rs 903.00 was paid on Amazon.in\nHi NAME, Thank you for using Amazon Pay Balance. Your payment was successful. " +
                "Paid on Amount Amazon.in ₹903.00 To report any unauthorised transaction, please click here Order ID 000-0000000-0000000"
        )

        assertEquals(903.0, parsed.amount, 0.001)
        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("Amazon.in", parsed.merchantRaw)
        assertEquals("000-0000000-0000000", parsed.refNumber)
        assertEquals(AccountHint.AMAZON_PAY_BALANCE, parsed.accountHint)
        assertNull(parsed.kind)
    }

    @Test
    fun aPaymentThatDoesNotSayAmazonPayBalanceIsNotRead() {
        // Not seen yet. Paid some other way, the bank's own alert reports it, and the Order ID would keep the two apart.
        val text = "Rs 1,250.50 was paid on Amazon.in\nHi NAME, Your payment was successful. Order ID 000-0000000-0000000"

        assertEquals(AlertReading.Unreadable, registry.read(sender, text))
    }

    @Test
    fun aPaymentWithACommaInItsAmount() {
        val parsed = read("Rs 1,250.50 was paid on Amazon.in\nHi NAME, Thank you for using Amazon Pay Balance. Order ID 000-0000000-0000000")

        assertEquals(1250.5, parsed.amount, 0.001)
    }

    @Test
    fun aGiftCardAddedIsACreditIntoTheBalance() {
        val parsed = read(
            "Amazon Pay Gift Card added to your Amazon Pay balance\nHi NAME, Amazon Pay Gift Card has been added to your " +
                "Amazon Pay balance. Your balance now reflects the additional funds added to your account. Received Amount " +
                "Amazon Pay eGift Card ₹833.00 Reference"
        )

        assertEquals(833.0, parsed.amount, 0.001)
        assertEquals(Direction.CREDIT, parsed.direction)
        assertEquals("Amazon Pay eGift Card", parsed.merchantRaw)
        assertEquals(AccountHint.AMAZON_PAY_BALANCE, parsed.accountHint)
        assertNull(parsed.refNumber)
        assertNull(parsed.kind)
    }

    @Test
    fun aGiftCardWithoutItsReceivedAmountIsNotRead() {
        // Never guessed from another amount in the email, such as a balance.
        val text = "Amazon Pay Gift Card added to your Amazon Pay balance\nHi NAME, Your balance is now ₹1,000.00"

        assertEquals(AlertReading.Unreadable, registry.read(sender, text))
    }

    @Test
    fun aCashbackIsACreditOfTheCashbackKind() {
        val parsed = read(
            "Your cashback of ₹25.00 is here!\nHi NAME, Yay! Here’s ₹25.00 cashback! You’ve got this cashback for PRODUCT " +
                "placed during Amazon Shopping. Sometimes your cashback could get credited in"
        )

        assertEquals(25.0, parsed.amount, 0.001)
        assertEquals(Direction.CREDIT, parsed.direction)
        assertEquals(CaptureKinds.CASHBACK, parsed.kind)
        assertEquals("Amazon Pay Cashback", parsed.merchantRaw)
        assertEquals(AccountHint.AMAZON_PAY_BALANCE, parsed.accountHint)
    }

    @Test
    fun aCashbackWithAStraightApostropheAndATrailingNewline() {
        val parsed = read("Your cashback of ₹70.00 is here!\nHi NAME, Yay! Here's ₹70.00 cashback! You've got this cashback for PRODUCT\n")

        assertEquals(70.0, parsed.amount, 0.001)
        assertEquals(CaptureKinds.CASHBACK, parsed.kind)
    }

    @Test
    fun anyOtherAmazonPayEmailIsClaimedButNotRead() {
        // So an offer or a new format lands under Alerts we couldn't read, never with the generic parser's guess.
        val offer = "Get ₹100 back on your next bill\nHi NAME, Pay your electricity bill with Amazon Pay and get Rs 100 back"

        assertTrue(AmazonPayEmailParser().canParse(sender, offer))
        assertEquals(AlertReading.Unreadable, registry.read(sender, offer))
    }

    @Test
    fun onlyAmazonPaysEmailIsClaimed() {
        assertTrue(!AmazonPayEmailParser().canParse("Amazon", "Rs 903.00 was paid on Amazon.in"))
        assertTrue(!AmazonPayEmailParser().canParse(BankEmails.HDFC.displayName, "Rs 903.00 was paid on Amazon.in"))
    }
}
