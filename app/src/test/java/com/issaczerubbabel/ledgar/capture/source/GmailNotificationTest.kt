package com.issaczerubbabel.ledgar.capture.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What auto-capture takes from a Gmail notification. The layout was read off a phone on 2026-11-28
 * with `dumpsys notification --noredact`: the title is the sender's display name, or for a thread
 * the senders' short names ("HDFC, alerts"); the text is the subject; the big text the subject, a
 * newline and the email's text; the sub text the label. Texts here are blanked.
 */
class GmailNotificationTest {

    private val subject = "❗  You have done a UPI txn. Check details!"
    private val preview = "Rs.20.00 is debited from your account ending 1234 towards VPA xxxx@okaxis (SHOP) on 28-11-26."
    private val label = "Bank alerts"

    private fun read(title: String?, bigText: String? = "$subject\n$preview", subText: String? = label, text: String? = subject) =
        GmailNotification.read(title = title, text = text, bigText = bigText, subText = subText, label = label)

    @Test
    fun aKnownSendersEmailIsTheSubjectThenItsText() {
        val alert = read("HDFC Bank InstaAlerts")!!

        assertEquals("HDFC Bank InstaAlerts", alert.sender)
        assertEquals("$subject\n$preview", alert.text)
    }

    @Test
    fun aThreadOfHdfcsEmailsIsReadUnderTheSendersName() {
        // HDFC's UPI alerts share a subject, so Gmail threads them and titles the thread with short names.
        assertEquals("HDFC Bank InstaAlerts", read("HDFC, alerts")!!.sender)
        assertEquals("HDFC Bank InstaAlerts", read("alerts, HDFC")!!.sender)
    }

    @Test
    fun onlyUnderTheUsersLabel() {
        assertNull(read("HDFC Bank InstaAlerts", subText = "Promotions"))
        assertNull(read("HDFC Bank InstaAlerts", subText = null))
        // Gmail's label is matched however it is capitalised.
        assertEquals("HDFC Bank InstaAlerts", read("HDFC Bank InstaAlerts", subText = "bank ALERTS")!!.sender)
        assertNull(GmailNotification.read("HDFC Bank InstaAlerts", subject, preview, subText = "", label = ""))
    }

    @Test
    fun withoutALabelToCheckOnlyTheSenderCounts() {
        // Only the debug build's adb posts, which have no label.
        val alert = GmailNotification.read("HDFC Bank InstaAlerts", text = preview, bigText = preview, subText = null, label = null)!!

        assertEquals(preview, alert.text)
    }

    @Test
    fun aBigTextWithoutTheSubjectGetsIt() {
        assertEquals("$subject\n$preview", read("HDFC Bank InstaAlerts", bigText = preview)!!.text)
    }

    @Test
    fun withNoBigTextTheSubjectIsAllThereIs() {
        assertEquals(subject, read("HDFC Bank InstaAlerts", bigText = null)!!.text)
    }

    @Test
    fun anyOtherSenderIsDropped() {
        assertNull(read("A Friend", bigText = "Lunch?\nI owe you Rs.500"))
        // HDFC's other address, for news and offers.
        assertNull(read("HDFC Bank"))
        assertNull(read("HDFC Bank InstaAlerts Fake"))
        assertNull(read(null))
        // A short name alone, or with anyone else's, is not HDFC's thread.
        assertNull(read("alerts"))
        assertNull(read("HDFC, A Friend"))
    }

    @Test
    fun amazonPayIsInTheFilterButNotReadYet() {
        assertNull(read("Amazon Pay India"))
        assertTrue(BankEmails.FILTER_QUERY.contains("no-reply@amazonpay.in"))
    }

    @Test
    fun aSummaryOfSeveralEmailsIsDropped() {
        assertNull(read("18 new messages", bigText = null, text = null))
    }

    @Test
    fun aBlankNotificationIsDropped() {
        assertNull(read("HDFC Bank InstaAlerts", bigText = null, text = " "))
    }

    @Test
    fun theFilterQueryIsWhatTheSetupStepsShow() {
        assertEquals("alerts@hdfcbank.bank.in OR no-reply@amazonpay.in", BankEmails.FILTER_QUERY)
    }
}
