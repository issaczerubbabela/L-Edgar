package com.issaczerubbabel.ledgar.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncStateStore: DataStore<Preferences> by preferencesDataStore(name = "sync_state")

/** Sync state that must outlive a single run. */
@Singleton
class SyncStateRepository @Inject constructor(@ApplicationContext private val context: Context) {

    /** Whether this phone has taken in the Sheet's Accounts, Dropdown options and Budgets at least once. */
    suspend fun hasMergedListsFromSheet(): Boolean =
        context.syncStateStore.data.map { it[LISTS_MERGED] ?: false }.first()

    suspend fun setMergedListsFromSheet() {
        context.syncStateStore.edit { it[LISTS_MERGED] = true }
    }

    /** Local ids of Transactions the Sheet no longer has, held back until the user decides. */
    val heldSheetDeletions: Flow<Set<Long>> = context.syncStateStore.data
        .map { prefs -> prefs[HELD_SHEET_DELETIONS].orEmpty().mapNotNull(String::toLongOrNull).toSet() }

    suspend fun setHeldSheetDeletions(ids: Collection<Long>) {
        context.syncStateStore.edit { prefs ->
            if (ids.isEmpty()) prefs.remove(HELD_SHEET_DELETIONS)
            else prefs[HELD_SHEET_DELETIONS] = ids.map(Long::toString).toSet()
        }
    }

    private companion object {
        val LISTS_MERGED = booleanPreferencesKey("lists_merged_from_sheet")
        val HELD_SHEET_DELETIONS = stringSetPreferencesKey("held_sheet_deletions")
    }
}
