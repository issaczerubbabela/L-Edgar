package com.issaczerubbabel.ledgar.capture.notify

import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureNotificationTextTest {

    private fun capture(merchant: String?, amount: Double, type: String = "Expense") = CapturedTransaction(
        sources = "NOTIF", sender = "HDFC Bank", rawText = "x", rawHash = "h", capturedAt = 0, txnTime = 0,
        amount = amount, direction = if (type == "Income") "CREDIT" else "DEBIT", channel = "UPI",
        merchantRaw = merchant, suggestedType = type
    )

    @Test
    fun titleNamesTheMerchantAndTheExactAmount() {
        val content = CaptureNotificationText.build(capture("Swiggy Instamart", 212.4), "Swiggy Instamart", "Groceries", "HDFC Savings")

        assertEquals("Swiggy Instamart  −₹212.40", content.title)
        assertTrue(content.text.startsWith("Groceries · HDFC Savings"))
    }

    @Test
    fun incomeIsShownWithAPlus() {
        val content = CaptureNotificationText.build(capture("ACME", 52000.0, "Income"), "ACME", "Salary", "Axis")

        assertEquals("ACME  +₹52,000", content.title)
    }

    @Test
    fun theLockScreenVersionRevealsNeitherMerchantNorAmount() {
        val content = CaptureNotificationText.build(capture("Swiggy Instamart", 450.0), "Instamart", "Groceries", "HDFC Savings")

        assertFalse(content.publicTitle.contains("Instamart"))
        assertFalse(content.publicTitle.contains("450"))
    }

    @Test
    fun aTransferRuleReadsTransferToItsOtherAccountAndTheLockScreenStaysGeneric() {
        val content = CaptureNotificationText.build(
            capture("CRED", 5000.0, "Transfer"), "CRED", "Transfer", "HDFC Savings", otherAccountName = "HDFC Credit Card"
        )

        assertEquals("Transfer ₹5,000 to HDFC Credit Card", content.title)
        assertTrue(content.text.startsWith("CRED · from HDFC Savings"))
        assertEquals("A captured transaction is waiting", content.publicTitle)
    }

    @Test
    fun aCreditTransferReadsFromItsOtherAccount() {
        val credit = capture("ISSAC", 2000.0, "Transfer").copy(direction = "CREDIT")

        val content = CaptureNotificationText.build(credit, "ISSAC", "Transfer", "CUB Savings", otherAccountName = "HDFC Savings")

        assertEquals("Transfer ₹2,000 from HDFC Savings", content.title)
        assertTrue(content.text.startsWith("ISSAC · to CUB Savings"))
    }

    @Test
    fun aMissingMerchantFallsBackToAPlainWord() {
        assertEquals("Payment", capture(null, 10.0).displayTitle())
        assertEquals("Money received", capture("  ", 10.0, "Income").displayTitle())
    }

    @Test
    fun aRenamedMerchantIsShownByItsNewName() {
        assertEquals("Instamart", capture("SWIGGY*BLR INSTAMART", 10.0).displayTitle(rename = "Instamart"))
        assertEquals("SWIGGY*BLR INSTAMART", capture("SWIGGY*BLR INSTAMART", 10.0).displayTitle(rename = " "))
    }
}
