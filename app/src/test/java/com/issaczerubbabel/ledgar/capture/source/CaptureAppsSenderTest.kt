package com.issaczerubbabel.ledgar.capture.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureAppsSenderTest {

    @Test
    fun bankAppsAndBankStyleSmsSendersAreWorthKeeping() {
        assertTrue(CaptureApps.looksLikeBankSender("HDFC Bank"))
        assertTrue(CaptureApps.looksLikeBankSender("Google Pay"))
        assertTrue(CaptureApps.looksLikeBankSender("AD-HDFCBK-T"))
        assertTrue(CaptureApps.looksLikeBankSender("JM-HDFCBN-S"))
        assertTrue(CaptureApps.looksLikeBankSender("VM-HDFCBK"))
        assertTrue(CaptureApps.looksLikeBankSender("HDFC Bank InstaAlerts"))
    }

    @Test
    fun gmailIsWatchedAndOnlyAnAdbPostStandsInForIt() {
        assertTrue(CaptureApps.isWatched(CaptureApps.GMAIL))
        // Debug builds read an adb post titled like a known sender as an email, and nothing else.
        assertTrue(CaptureApps.isDebugEmail("com.android.shell", "HDFC Bank InstaAlerts"))
        assertFalse(CaptureApps.isDebugEmail("com.android.shell", "AD-HDFCBK-T"))
        assertFalse(CaptureApps.isDebugEmail(CaptureApps.GOOGLE_MESSAGES, "HDFC Bank InstaAlerts"))
        // Amazon Pay's emails are read only once their parser lands (#181).
        assertFalse(CaptureApps.looksLikeBankSender("Amazon Pay India"))
    }

    @Test
    fun peopleAndPhoneNumbersAreNeverKept() {
        assertFalse(CaptureApps.looksLikeBankSender("Mom"))
        assertFalse(CaptureApps.looksLikeBankSender("Ajayy CFC BLR"))
        assertFalse(CaptureApps.looksLikeBankSender("+919876543210"))
        assertFalse(CaptureApps.looksLikeBankSender("Jo Ann"))
        assertFalse(CaptureApps.looksLikeBankSender(""))
    }
}
