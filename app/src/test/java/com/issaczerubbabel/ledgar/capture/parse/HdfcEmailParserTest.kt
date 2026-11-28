package com.issaczerubbabel.ledgar.capture.parse

import com.issaczerubbabel.ledgar.capture.source.BankEmails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * HDFC's alert emails as Gmail's notification gives them: the subject, a newline, then the preview.
 * Every sample is a real email (#180) with names, account and card digits, VPAs and references blanked.
 */
class HdfcEmailParserTest {

    private val parser = HdfcEmailParser()
    private val registry = ParserRegistry.default()
    private val sender = BankEmails.HDFC.displayName

    private fun read(text: String): ParsedTxn = registry.parse(sender, text)!!

    @Test
    fun aUpiDebitNamesThePayeeInBrackets() {
        val parsed = read(
            "❗  You have done a UPI txn. Check details!\nDear Customer, Greetings from HDFC Bank! Rs.2000.00 is debited from " +
                "your account ending 1234 towards VPA xxxxxxxx@validyes (Indian Clearing Corporation Ltd) on 28-11-26. " +
                "UPI transaction reference no.: 111111111111. If you"
        )

        assertEquals(2000.0, parsed.amount, 0.001)
        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("1234", parsed.accountHint)
        assertEquals("Indian Clearing Corporation Ltd", parsed.merchantRaw)
        assertEquals("111111111111", parsed.refNumber)
        assertEquals(Channel.UPI, parsed.channel)
        assertEquals(LocalDate.of(2026, 11, 28), parsed.txnDate)
    }

    @Test
    fun aThreadedUpiDebitAsGmailsNotificationShowsIt() {
        // Gmail's big text for a later email in HDFC's thread, read off a phone: no greeting, the footer run on.
        val parsed = read(
            "❗  You have done a UPI txn. Check details!\nRs.99.00 is debited from your account ending 1234 towards VPA " +
                "****xxxx@pineaxis (ACME (P) TECHNOLOGIES PRIVATE LIMITED) on 27-11-26.\nUPI transaction reference no.: " +
                "111111111111.For more details on Service charges and Fees, click here.\n© HDFC Bank"
        )

        assertEquals(99.0, parsed.amount, 0.001)
        assertEquals("ACME (P) TECHNOLOGIES PRIVATE LIMITED", parsed.merchantRaw)
        assertEquals("1234", parsed.accountHint)
        assertEquals("111111111111", parsed.refNumber)
    }

    @Test
    fun aUpiCreditWithTransactionDetailsNamesTheSender() {
        val parsed = read(
            "View: Account update for your HDFC Bank A/c\nDear Customer,\nGreetings from HDFC Bank!\nWe're writing to inform " +
                "you that Rs.5.00 has been successfully credited to your HDFC Bank account ending in 1234.\nTransaction Details: " +
                "\na. Date: 27-11-26 \nb. Sender: A B KUMAR(VPA: *********00-0@okaxis) \nc. UPI Reference No.: 111111111111 " +
                "\nNeed Help? \nIndia (Toll-Free): 0000 000 0000"
        )

        assertEquals(5.0, parsed.amount, 0.001)
        assertEquals(Direction.CREDIT, parsed.direction)
        assertEquals("1234", parsed.accountHint)
        assertEquals("A B KUMAR", parsed.merchantRaw)
        assertEquals("111111111111", parsed.refNumber)
        assertEquals(Channel.UPI, parsed.channel)
        assertEquals(LocalDate.of(2026, 11, 27), parsed.txnDate)
    }

    @Test
    fun aUpiDebitWithoutBracketsTakesTheVpa() {
        val parsed = read(
            "You have done a UPI txn. Check details!\nDear Customer, Greetings from HDFC Bank! Rs.80.00 is debited from " +
                "your account ending 1234 towards VPA xxxxxxxx@okicici on 27-11-26. UPI transaction reference no.: 222222222222."
        )

        assertEquals("xxxxxxxx@okicici", parsed.merchantRaw)
        assertEquals("222222222222", parsed.refNumber)
    }

    @Test
    fun aDebitCardPaymentNamesTheMerchantAndTheCardIsTheAccount() {
        val parsed = read(
            "Rs.2399.00 debited via Debit Card **5678\nDear Customer, Greetings from HDFC Bank! Rs.2399.00 is debited from " +
                "your HDFC Bank Debit Card ending 5678 at ANTHROPIC* CLAUDE SUB on 18 Nov, 2026 at 15:47:45. For more " +
                "details on this transaction,"
        )

        assertEquals(2399.0, parsed.amount, 0.001)
        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("5678", parsed.accountHint)
        assertEquals("ANTHROPIC* CLAUDE SUB", parsed.merchantRaw)
        assertNull(parsed.refNumber)
        assertEquals(Channel.CARD, parsed.channel)
        assertEquals(LocalDate.of(2026, 11, 18), parsed.txnDate)
    }

    @Test
    fun anImpsTransferGivesTheOtherAccountsDigits() {
        val parsed = read(
            "View: Account update for your HDFC Bank A/c\nDear Customer, INR 47,000.00 has been debited from your account " +
                "ending xxxxxxxxxx1234 on 25-11-26 and credited to the account ending xxxxxxxxxx5678 via IMPS. IMPS " +
                "Reference No: 111111111111"
        )

        assertEquals(47000.0, parsed.amount, 0.001)
        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("1234", parsed.accountHint)
        assertEquals("5678", parsed.counterpartyAccountHint)
        assertNull(parsed.merchantRaw)
        assertEquals("111111111111", parsed.refNumber)
        assertEquals(Channel.IMPS, parsed.channel)
        assertEquals(LocalDate.of(2026, 11, 25), parsed.txnDate)
    }

    @Test
    fun aUpiCreditNamesThePayerAfterTheVpa() {
        val parsed = read(
            "View: Account update for your HDFC Bank A/c\nDear Customer, Rs. 1.00 is successfully credited to your account " +
                "**1234 by VPA xxxxxxxx@hdfcbank A B BROKING LTD on 06-03-26. Your UPI transaction reference number is " +
                "111111111111."
        )

        assertEquals(1.0, parsed.amount, 0.001)
        assertEquals(Direction.CREDIT, parsed.direction)
        assertEquals("1234", parsed.accountHint)
        assertEquals("A B BROKING LTD", parsed.merchantRaw)
        assertEquals("111111111111", parsed.refNumber)
        assertEquals(Channel.UPI, parsed.channel)
        assertEquals(LocalDate.of(2026, 3, 6), parsed.txnDate)
    }

    @Test
    fun aDepositAlertNamesThePayerFromItsReference() {
        val parsed = read(
            "❗ New Deposit Alert: Check your A/c balance now!\nDear Customer, You have received a credit in your HDFC Bank " +
                "account. Details of the transaction: Amount received: INR 1,23,456.00 Account: XX1234 Date: 26-NOV-2026 " +
                "Reference Details: ACMEPL CREDIT"
        )

        assertEquals(123456.0, parsed.amount, 0.001)
        assertEquals(Direction.CREDIT, parsed.direction)
        assertEquals("1234", parsed.accountHint)
        assertEquals("ACMEPL", parsed.merchantRaw)
        assertEquals(LocalDate.of(2026, 11, 26), parsed.txnDate)
    }

    @Test
    fun aDepositAlertCutBeforeItsReferenceStillParses() {
        // Gmail's preview drops a comma from the amount and can end anywhere.
        val parsed = read(
            "New Deposit Alert: Check your A/c balance now!\nDear Customer, You have received a credit in your HDFC Bank " +
                "account. Details of the transaction: Amount received: INR 1,23456.00 Account: XX1234 Date: 26-NOV-2026"
        )

        assertEquals(123456.0, parsed.amount, 0.001)
        assertEquals(Direction.CREDIT, parsed.direction)
        assertNull(parsed.merchantRaw)
    }

    @Test
    fun onlyTheHdfcEmailSenderIsClaimed() {
        assertTrue(parser.canParse(sender, "anything"))
        assertFalse(parser.canParse("HDFC Bank", "Rs.70.00 debited"))
        assertFalse(parser.canParse("AD-HDFCBK-T", "Rs.70.00 debited"))
    }

    @Test
    fun hdfcEmailsThatReportNoPaymentAreNotRead() {
        // Real, with no amount in the preview: never a capture, and too empty to keep as unreadable.
        assertNull(
            parser.parse(
                "View: Account update for your HDFC Bank A/c\nDear Customer, Biometric authentication has been enabled for " +
                    "UPI payments on (AmazonPayApp) linked to your bank account (1234)."
            )
        )
        assertNull(
            parser.parse(
                "View: Account update for your HDFC Bank A/c\nDear Customer, Greetings from HDFC Bank! Your ACME bill, set " +
                    "up through E-mandate (Auto payment), has been successfully paid using your HDFC Bank Debit Card ending 5678. " +
                    "Transaction Details:"
            )
        )
    }

    @Test
    fun theForexNoticeForACardPaymentIsNotAPayment() {
        // Real: it repeats a card payment already alerted, from the same source, so it must not be a second capture.
        val reading = registry.read(
            sender,
            "Important: Forex Conversion Markup Fee on cross border transaction using Debit Card Transaction\nDear Customer, " +
                "Thank you for choosing HDFC Bank! Recent International Transaction Alert: Your HDFC Bank Debit Card ending in " +
                "XX5678 was used for an international purchase of INR 2399.00 on 18/11/2026"
        )

        assertEquals(AlertReading.NotAPayment, reading)
    }
}
