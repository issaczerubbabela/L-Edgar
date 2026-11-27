package com.issaczerubbabel.ledgar.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.spendableTopUpDataStore: DataStore<Preferences> by preferencesDataStore(name = "spendable_top_up_prefs")

/**
 * Device-local state for the Budget tab's one-time "raise spendable" card (#156, see
 * [com.issaczerubbabel.ledgar.data.bucket.SpendableTopUp]): the day this phone first had a Fund
 * holding a whole Account, and the cycles whose card the user answered with Raise or Keep. Never
 * synced or backed up: a reinstall can only show the card again once, for the running cycle.
 */
@Singleton
class SpendableTopUpPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val FUNDS_SINCE = stringPreferencesKey("funds_since")
    private val ANSWERED_CYCLE_IDS = stringSetPreferencesKey("answered_cycle_ids")

    /** Null until the app has seen a Fund holding a whole Account. */
    val fundsSince: Flow<LocalDate?> = context.spendableTopUpDataStore.data
        .map { prefs -> prefs[FUNDS_SINCE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }

    val answeredCycleIds: Flow<Set<Long>> = context.spendableTopUpDataStore.data
        .map { prefs -> prefs[ANSWERED_CYCLE_IDS].orEmpty().mapNotNullTo(HashSet()) { it.toLongOrNull() } }

    /** Keeps the first date it's given: later calls change nothing. */
    suspend fun recordFundsSince(date: LocalDate) {
        context.spendableTopUpDataStore.edit { prefs ->
            if (prefs[FUNDS_SINCE] == null) prefs[FUNDS_SINCE] = date.toString()
        }
    }

    /** Hides [cycleId]'s card for good, after Raise or Keep. */
    suspend fun markAnswered(cycleId: Long) {
        context.spendableTopUpDataStore.edit { prefs ->
            prefs[ANSWERED_CYCLE_IDS] = prefs[ANSWERED_CYCLE_IDS].orEmpty() + cycleId.toString()
        }
    }
}
