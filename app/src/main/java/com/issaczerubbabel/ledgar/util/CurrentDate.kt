package com.issaczerubbabel.ledgar.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.issaczerubbabel.ledgar.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The device's local "today". Anything that defaults a date to "today" (a new Transaction, a
 * budget cycle's day count) should read [today] or call [now] at the moment it's needed, never
 * [LocalDate.now] directly — a value read once and kept goes stale if the screen is left open
 * across midnight. See issue #22.
 */
interface CurrentDate {
    val today: StateFlow<LocalDate>
    fun now(): LocalDate
}

@Singleton
class SystemCurrentDate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope
) : CurrentDate {

    private val _today = MutableStateFlow(LocalDate.now(clock))
    override val today: StateFlow<LocalDate> = _today.asStateFlow()

    override fun now(): LocalDate = LocalDate.now(clock)

    /** Starts watching for the date changing underneath the app; called once from SheetSyncApp.onCreate(). */
    fun start() {
        scope.launch {
            dateChangeSignals().collect { _today.value = now() }
        }
    }

    /** One signal per date/time/timezone broadcast; [now] is re-read by the collector, not carried here. */
    private fun dateChangeSignals() = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { trySend(Unit) }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
