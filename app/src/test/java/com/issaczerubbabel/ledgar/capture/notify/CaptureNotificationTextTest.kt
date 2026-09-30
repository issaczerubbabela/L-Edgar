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
        val content = CaptureNotificationText.build(capture("Swiggy Instamart", 212.4), "Groceries", "HDFC Savings")

        assertEquals("Swiggy Instamart  −₹212.40", content.title)
        assertTrue(content.text.startsWith("Groceries · HDFC Savings"))
    }

    @Test
    fun incomeIsShownWithAPlus() {
        val content = CaptureNotificationText.build(capture("ACME", 52000.0, "Income"), "Salary", "Axis")

        assertEquals("ACME  +₹52,000", content.title)
    }

    @Test
    fun theLockScreenVersionRevealsNeitherMerchantNorAmount() {
        val content = CaptureNotificationText.build(capture("Swiggy Instamart", 450.0), "Groceries", "HDFC Savings")

        assertFalse(content.publicTitle.contains("Swiggy"))
        assertFalse(content.publicTitle.contains("450"))
    }

    @Test
    fun aMissingMerchantFallsBackToAPlainWord() {
        assertEquals("Payment", capture(null, 10.0).displayTitle())
        assertEquals("Money received", capture("  ", 10.0, "Income").displayTitle())
    }
}
