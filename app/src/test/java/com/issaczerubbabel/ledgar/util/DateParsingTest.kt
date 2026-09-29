package com.issaczerubbabel.ledgar.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TimeZone

class DateParsingTest {

    @Test
    fun parsesJavaScriptLongDateString() {
        val raw = "Mon Apr 06 2026 00:00:00 GMT+0530 (India Standard Time)"

        val parsed = parseFlexibleDate(raw)

        assertNotNull(parsed)
        assertEquals(LocalDate.of(2026, 4, 6), parsed)
    }

    private val defaultZone: TimeZone = TimeZone.getDefault()

    @After
    fun restoreDefaultZone() {
        TimeZone.setDefault(defaultZone)
    }

    /**
     * Regression test for issue #16: `DatePickerState.selectedDateMillis` is always UTC-midnight,
     * so converting through the device zone (as the four picker call sites used to) shifts the
     * date by one day in most zones. [toPickerMillis]/[pickerMillisToLocalDate] must round-trip
     * regardless of the device's zone.
     */
    @Test
    fun pickerMillisRoundTripsAcrossTimeZones() {
        val zones = listOf("Asia/Kolkata", "America/Los_Angeles", "Pacific/Kiritimati", "Pacific/Pago_Pago")
        val dates = listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 28), LocalDate.of(2026, 12, 31))

        for (zone in zones) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            for (date in dates) {
                val roundTripped = date.toPickerMillis().pickerMillisToLocalDate()
                assertEquals("zone=$zone date=$date", date, roundTripped)
            }
        }
    }

    @Test
    fun pickerMillisToLocalDateIgnoresTimeOfDayWithinTheUtcDay() {
        val base = LocalDate.of(2026, 9, 28).toPickerMillis()
        val anyTimeThatSameUtcDay = base + 23 * 60 * 60 * 1000

        assertEquals(LocalDate.of(2026, 9, 28), anyTimeThatSameUtcDay.pickerMillisToLocalDate())
    }

    @Test
    fun asOfDateTimeRoundTrips() {
        val value = LocalDateTime.of(2026, 9, 28, 14, 30, 5)

        assertEquals(value, parseAsOfDateTime(formatAsOfDateTime(value)))
    }
}
