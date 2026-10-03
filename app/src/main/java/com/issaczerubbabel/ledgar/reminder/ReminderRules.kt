package com.issaczerubbabel.ledgar.reminder

import java.time.Instant
import java.time.ZoneId

/**
 * Pure scheduling rules for the daily reminder: when it next fires, and whether it should fire at
 * all. Kept free of Android classes so the DST and boundary cases run as plain JVM tests.
 */
object ReminderRules {

    /**
     * The next instant after [now] that is [minuteOfDay] wall-clock minutes (0..1439) into the day
     * in [zone]. Builds the candidate with [java.time.LocalDateTime] arithmetic before resolving the
     * zone, so a DST transition between midnight and the target time shifts the UTC instant rather
     * than the wall-clock time the user picked.
     */
    fun nextReminderAt(now: Instant, minuteOfDay: Int, zone: ZoneId): Instant {
        val nowZoned = now.atZone(zone)
        val todayAtTime = nowZoned.toLocalDate().atStartOfDay().plusMinutes(minuteOfDay.toLong())
        val todayCandidate = todayAtTime.atZone(zone)
        val target = if (todayCandidate.isAfter(nowZoned)) todayCandidate else todayAtTime.plusDays(1).atZone(zone)
        return target.toInstant()
    }

    /** True when [lastLoggedEpochDay] isn't today, i.e. nothing has been logged on the phone yet. */
    fun shouldRemind(todayEpochDay: Long, lastLoggedEpochDay: Long?): Boolean =
        lastLoggedEpochDay != todayEpochDay
}
