package com.issaczerubbabel.ledgar.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class UpiLinkTest {

    @Test
    fun linkCarriesPayeeNameAndAmountInRupees() {
        assertEquals(
            "upi://pay?pa=rahul.k%40oksbi&pn=Rahul%20K&am=1080.50&cu=INR&tn=Trip%20settle-up",
            UpiLink.build(" rahul.k@oksbi ", "Rahul K", 108050)
        )
    }
}
