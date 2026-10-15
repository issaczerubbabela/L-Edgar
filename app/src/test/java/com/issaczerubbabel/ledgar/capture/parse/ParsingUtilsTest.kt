package com.issaczerubbabel.ledgar.capture.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsingUtilsTest {

    @Test
    fun directionOfOnlyDebitWordIsDebit() {
        assertEquals(Direction.DEBIT, ParsingUtils.directionOf("Rs.100 debited from your account"))
    }

    @Test
    fun directionOfOnlyCreditWordIsCredit() {
        assertEquals(Direction.CREDIT, ParsingUtils.directionOf("Rs.100 credited to your account"))
    }

    @Test
    fun directionOfDebitThenCreditIsDebit() {
        assertEquals(
            Direction.DEBIT,
            ParsingUtils.directionOf("Acct XX123 debited for Rs 240.00 on 28-Sep-26; ZOMATO credited.")
        )
    }

    @Test
    fun directionOfCreditThenDebitIsCredit() {
        assertEquals(
            Direction.CREDIT,
            ParsingUtils.directionOf("Rs.500 credited to your a/c XX1234 which was debited from a/c XX9999")
        )
    }

    @Test
    fun directionOfNeitherWordIsNull() {
        assertNull(ParsingUtils.directionOf("Your statement is ready to view."))
    }
}
