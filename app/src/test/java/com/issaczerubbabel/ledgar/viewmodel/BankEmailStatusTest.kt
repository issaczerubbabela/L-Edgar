package com.issaczerubbabel.ledgar.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class BankEmailStatusTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun beforeTheFirstEmailItPointsAtTheFilter() {
        assertEquals("None yet, check your Gmail filter", bankEmailStatus(null, zone))
    }

    @Test
    fun afterOneItSaysWhen() {
        val at = ZonedDateTime.of(2026, 11, 28, 15, 47, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals("Last bank email read: 28 Nov, 3:47 PM", bankEmailStatus(at, zone))
    }
}
