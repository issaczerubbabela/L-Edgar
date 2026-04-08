package com.issaczerubbabel.ledgar.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class DateParsingTest {

    @Test
    fun parsesJavaScriptLongDateString() {
        val raw = "Mon Apr 06 2026 00:00:00 GMT+0530 (India Standard Time)"

        val parsed = parseFlexibleDate(raw)

        assertNotNull(parsed)
        assertEquals(LocalDate.of(2026, 4, 6), parsed)
    }
}
