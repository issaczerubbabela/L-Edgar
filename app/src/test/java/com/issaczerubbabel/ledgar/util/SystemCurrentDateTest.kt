package com.issaczerubbabel.ledgar.util

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/** A clock that can be moved past midnight or into another time zone, as a phone's can. */
private class MovableClock(private var instant: Instant, private var zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MovableClock(instant, zone)
    override fun instant(): Instant = instant
    fun moveTo(newInstant: Instant) { instant = newInstant }
    fun moveToZone(newZone: ZoneId) { zone = newZone }
}

/** Today moves on under a running app (#22). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SystemCurrentDateTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val sep30 = LocalDate.of(2026, 9, 30)
    private val oct1 = LocalDate.of(2026, 10, 1)

    private fun startedAt(instant: String, zone: ZoneId = ZoneOffset.UTC): Pair<MovableClock, SystemCurrentDate> {
        val clock = MovableClock(Instant.parse(instant), zone)
        return clock to SystemCurrentDate(context, clock).also { it.start() }
    }

    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun theDateChangingAtMidnightMovesToday() {
        val (clock, currentDate) = startedAt("2026-09-30T23:50:00Z")
        assertEquals(sep30, currentDate.today.value)

        clock.moveTo(Instant.parse("2026-10-01T00:00:01Z"))
        broadcast(Intent.ACTION_DATE_CHANGED)

        assertEquals(oct1, currentDate.today.value)
    }

    @Test
    fun theUserSettingTheClockMovesToday() {
        val (clock, currentDate) = startedAt("2026-09-30T10:00:00Z")

        clock.moveTo(Instant.parse("2026-10-01T10:00:00Z"))
        broadcast(Intent.ACTION_TIME_CHANGED)

        assertEquals(oct1, currentDate.today.value)
    }

    @Test
    fun aTimeZoneChangeThatChangesTheDateMovesToday() {
        val (clock, currentDate) = startedAt("2026-10-01T02:00:00Z")
        assertEquals(oct1, currentDate.today.value)

        // 02:00 UTC is still 30 Sep five hours west.
        clock.moveToZone(ZoneOffset.ofHours(-5))
        broadcast(Intent.ACTION_TIMEZONE_CHANGED)

        assertEquals(sep30, currentDate.today.value)
    }

    @Test
    fun readingNowCatchesAMissedBroadcast() {
        val (clock, currentDate) = startedAt("2026-09-30T23:50:00Z")

        clock.moveTo(Instant.parse("2026-10-01T08:00:00Z"))

        assertEquals(oct1, currentDate.now())
        assertEquals(oct1, currentDate.today.value)
    }

    @Test
    fun theAppComingBackIntoViewCatchesAMissedBroadcast() {
        val (clock, currentDate) = startedAt("2026-09-30T23:50:00Z")

        clock.moveTo(Instant.parse("2026-10-01T08:00:00Z"))
        Robolectric.buildActivity(Activity::class.java).setup()

        assertEquals(oct1, currentDate.today.value)
    }

    @Test
    fun theDeviceClockFollowsTheDevicesTimeZone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
            assertEquals(ZoneId.of("Asia/Kolkata"), DeviceZoneClock.zone)

            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals(ZoneId.of("America/New_York"), DeviceZoneClock.zone)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
