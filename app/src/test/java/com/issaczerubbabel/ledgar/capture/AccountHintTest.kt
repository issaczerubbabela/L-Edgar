package com.issaczerubbabel.ledgar.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AccountHintTest {

    @Test
    fun digitsShowAsTheCardShowsThem() {
        assertEquals("XX1234", AccountHint.label("1234"))
    }

    @Test
    fun aSourceShowsItsOwnName() {
        assertEquals("Amazon Pay Balance", AccountHint.label(AccountHint.AMAZON_PAY_BALANCE))
    }

    @Test
    fun aSourceIsNeverMistakenForDigits() {
        assertNotEquals("1234", AccountHint.ofSource("1234"))
    }
}
