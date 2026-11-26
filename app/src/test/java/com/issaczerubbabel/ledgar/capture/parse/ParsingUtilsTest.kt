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

    // The VPA alerts below are synthetic: no real bank alert addressed to a VPA has been captured yet.

    @Test
    fun vpaOfReadsTheHandleAfterToVpa() {
        assertEquals(
            "swiggy.stores@axb",
            ParsingUtils.vpaOf("Rs.250.00 debited from A/c XX1234 to VPA swiggy.stores@axb on 12-11-26 Ref 1234")
        )
    }

    @Test
    fun vpaOfReadsTheHandleAfterToUpiId() {
        assertEquals("9876543210@ybl", ParsingUtils.vpaOf("Rs.99 debited from A/c XX1234 to UPI ID 9876543210@ybl."))
    }

    @Test
    fun vpaOfReadsABareHandleAfterTo() {
        assertEquals("zomato-order@okhdfcbank", ParsingUtils.vpaOf("Rs.240 sent to zomato-order@okhdfcbank on 01-01-26"))
    }

    @Test
    fun vpaOfIgnoresTheSmsBlockFooter() {
        assertNull(ParsingUtils.vpaOf("Rs.100 debited from A/c XX1234. Not you? SMS BLOCK UPI to 7300123456"))
    }

    @Test
    fun vpaOfIgnoresAnEmailAddress() {
        assertNull(ParsingUtils.vpaOf("Rs.100 debited from A/c XX1234. For queries write to care@hdfcbank.net"))
    }

    @Test
    fun vpaOfWithoutAHandleIsNull() {
        assertNull(ParsingUtils.vpaOf("Sent Rs.70.00 From HDFC Bank A/C *1234 To Mrs Jane Doe On 26/09/26"))
    }
}
