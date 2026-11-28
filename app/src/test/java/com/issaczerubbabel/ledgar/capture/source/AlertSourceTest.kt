package com.issaczerubbabel.ledgar.capture.source

import com.issaczerubbabel.ledgar.capture.CaptureSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertSourceTest {

    private fun notification(sender: String) = AlertSource.of(CaptureSource.NOTIFICATION, sender)

    @Test
    fun aBankSmsIsTheBanksSmsWhateverItsSenderPrefix() {
        val hdfc = notification("AD-HDFCBK-S")

        assertEquals(AlertSource(AlertSourceKind.SMS, "HDFC"), hdfc)
        assertEquals(hdfc, notification("JM-HDFCBN-T"))
        assertEquals("SMS from HDFC", hdfc.label)
        assertEquals("SMS from CUB", notification("VM-CUBANK").label)
        assertEquals("SMS from SBIUPI", notification("AX-SBIUPI-S").label)
    }

    @Test
    fun aBanksInsurerOrFundIsNotTheBanksSms() {
        assertTrue(notification("AD-HDFCLI-S") != notification("AD-HDFCBK-S"))
        assertTrue(notification("VM-AXISMF") != notification("VM-AXISBK"))
    }

    @Test
    fun appsAreTheirOwnSourceAndOnlyAUpiAppIsNoBank() {
        val googlePay = notification("Google Pay")
        val hdfcApp = notification("HDFC Bank")

        assertEquals(AlertSourceKind.UPI_APP, googlePay.kind)
        assertEquals("Google Pay", googlePay.label)
        assertFalse(googlePay.isBank)
        assertEquals(AlertSourceKind.BANK_APP, hdfcApp.kind)
        assertEquals("HDFC Bank", hdfcApp.label)
        assertTrue(hdfcApp.isBank)
        assertTrue(hdfcApp != notification("AD-HDFCBK-S"))
    }

    @Test
    fun anEmailIsTheBanksEmail() {
        val email = AlertSource.of(CaptureSource.EMAIL, "HDFC Bank InstaAlerts")

        assertEquals(AlertSourceKind.EMAIL, email.kind)
        assertEquals("Email from HDFC", email.label)
        assertTrue(email.isBank)
    }

    @Test
    fun aKeyReadsBackAsTheSameSource() {
        listOf(notification("AD-HDFCBK-S"), notification("Google Pay"), notification("City Union Bank")).forEach {
            assertEquals(it, AlertSource.fromKey(it.key))
        }
        assertNull(AlertSource.fromKey("nonsense"))
    }
}
