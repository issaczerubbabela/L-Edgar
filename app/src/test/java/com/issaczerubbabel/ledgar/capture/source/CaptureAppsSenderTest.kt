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
