package com.issaczerubbabel.ledgar.util

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The device's local "today". Code that defaults a date to today reads [now] at the moment it
 * needs it, or watches [today], instead of keeping a [LocalDate.now] from earlier: a value read
 * once goes stale when the app is left open past midnight (#22).
 */
interface CurrentDate {
    /** Today's date, moving on at midnight and when the device's time or time zone changes. */
    val today: StateFlow<LocalDate>

    /** Today's date, read from the clock now. */
    fun now(): LocalDate
}

/**
 * The system clock in the device's time zone as it is at each read. [Clock.systemDefaultZone]
 * keeps the zone it was made with, so it would miss the user changing time zone.
 */
object DeviceZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}

@Singleton
class SystemCurrentDate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock
) : CurrentDate {

    private val _today = MutableStateFlow(LocalDate.now(clock))
    override val today: StateFlow<LocalDate> = _today.asStateFlow()

    private var started = false

    /** Also moves [today] on, so a date change a broadcast missed is caught the next time anyone asks. */
    override fun now(): LocalDate = LocalDate.now(clock).also { _today.value = it }

    /**
     * Watches for the date changing under the running app: the system's date, time and time zone
     * broadcasts, and, in case one of those is missed, any screen of the app coming back into view.
     * Called once from SheetSyncApp.onCreate().
     */
    fun start() {
        if (started) return
        started = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                now()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // These are protected system broadcasts, which reach a receiver that isn't exported.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    now()
                }
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }
}
