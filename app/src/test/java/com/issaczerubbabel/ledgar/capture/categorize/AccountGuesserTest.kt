package com.issaczerubbabel.ledgar.capture.categorize

import com.issaczerubbabel.ledgar.capture.source.CaptureApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountGuesserTest {

    @Test
    fun aBankAppWithOneAliasedAccountSuggestsIt() {
        assertEquals(7L, AccountGuesser.guess(isBankApp = true, accountsAliasedForBank = setOf(7L)))
    }

    @Test
    fun twoAccountsAtTheBankOrNoneMeansNoGuess() {
        assertNull(AccountGuesser.guess(isBankApp = true, accountsAliasedForBank = setOf(7L, 8L)))
        assertNull(AccountGuesser.guess(isBankApp = true, accountsAliasedForBank = emptySet()))
    }

    @Test
    fun aUpiAppOrAnSmsIsNotABank() {
        assertNull(AccountGuesser.guess(isBankApp = false, accountsAliasedForBank = setOf(7L)))
        assertTrue(CaptureApps.isBankApp("HDFC Bank"))
        assertFalse(CaptureApps.isBankApp("Google Pay"))
        assertFalse(CaptureApps.isBankApp("AD-HDFCBK-T"))
    }
}
