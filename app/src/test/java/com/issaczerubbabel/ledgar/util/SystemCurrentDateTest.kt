package com.issaczerubbabel.ledgar.util

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A [Clock] whose instant and zone can be moved, the way a real device's clock moves across midnight. */
private class MutableClock(private var instant: Instant, private var zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(newZone: ZoneId): Clock = MutableClock(instant, newZone)
    override fun instant(): Instant = instant
    fun advanceTo(newInstant: Instant) { instant = newInstant }
    fun changeZone(newZone: ZoneId) { zone = newZone }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SystemCurrentDateTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `now reads the clock directly, with no broadcast needed`() {
        val clock = MutableClock(Instant.parse("2026-10-01T00:05:00Z"), ZoneOffset.UTC)
        val currentDate = SystemCurrentDate(context, clock, scope = CoroutineScope(Dispatchers.Unconfined))

        assertEquals(LocalDate.parse("2026-10-01"), currentDate.now())
    }

    @Test
    fun `today moves to the new day once the clock moves and a date-changed broadcast fires`() = runTest {
        val clock = MutableClock(Instant.parse("2026-09-30T23:50:00Z"), ZoneOffset.UTC)
        val currentDate = SystemCurrentDate(context, clock, backgroundScope)

        currentDate.start()
        runCurrent()
        assertEquals(LocalDate.parse("2026-09-30"), currentDate.today.value)

        clock.advanceTo(Instant.parse("2026-10-01T00:05:00Z"))
        context.sendBroadcast(Intent(Intent.ACTION_DATE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()

        assertEquals(LocalDate.parse("2026-10-01"), currentDate.today.value)
    }

    @Test
    fun `a timezone change moves today when the new zone shifts the date`() = runTest {
        val clock = MutableClock(Instant.parse("2026-10-01T02:00:00Z"), ZoneOffset.UTC)
        val currentDate = SystemCurrentDate(context, clock, backgroundScope)

        currentDate.start()
        runCurrent()
        assertEquals(LocalDate.parse("2026-10-01"), currentDate.today.value)

        // The device's zone changes to one where 02:00 UTC is still the previous day.
        clock.changeZone(ZoneOffset.ofHours(-5))
        context.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()

        assertEquals(LocalDate.parse("2026-09-30"), currentDate.today.value)
    }
}
