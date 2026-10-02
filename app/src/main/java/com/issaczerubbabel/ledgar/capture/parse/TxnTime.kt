package com.issaczerubbabel.ledgar.capture.parse

import java.time.LocalDate
import java.time.ZoneId

/** Decides the moment a capture is filed under, from the date its alert states (if any) and when it arrived. */
object TxnTime {

    /**
     * An alert's own date wins, so a late SMS, or one read back from the notification shade, is not
     * filed under today. A date that is missing, or later than tomorrow (a misread), falls back to
     * the arrival time. A past date keeps the arrival's time of day, since alerts rarely state one.
     */
    fun resolve(alertDate: LocalDate?, arrivedAtMillis: Long, zone: ZoneId): Long {
        if (alertDate == null) return arrivedAtMillis
        val arrived = java.time.Instant.ofEpochMilli(arrivedAtMillis).atZone(zone)
        val today = arrived.toLocalDate()
        if (alertDate == today || alertDate.isAfter(today.plusDays(1))) return arrivedAtMillis
        return alertDate.atTime(arrived.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
    }
}
