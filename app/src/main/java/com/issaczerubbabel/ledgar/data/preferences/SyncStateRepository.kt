package com.issaczerubbabel.ledgar.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

    /** The last revision this phone read or wrote for a list Backup target, if any (ADR-0010). */
    suspend fun listRevision(target: String): String? =
        context.syncStateStore.data.map { it[revisionKey(target)] }.first()

    suspend fun setListRevision(target: String, revision: String?) {
        context.syncStateStore.edit { prefs ->
            if (revision.isNullOrBlank()) prefs.remove(revisionKey(target)) else prefs[revisionKey(target)] = revision
        }
    }

    /** List Backup targets the Sheet changed since this phone's last Backup, waiting for the user to decide. */
    val listsChangedInSheet: Flow<Set<String>> = context.syncStateStore.data
        .map { prefs -> prefs[LISTS_CHANGED_IN_SHEET].orEmpty() }

    suspend fun setListChangedInSheet(target: String, changed: Boolean) {
        context.syncStateStore.edit { prefs ->
            val now = prefs[LISTS_CHANGED_IN_SHEET].orEmpty().let { if (changed) it + target else it - target }
            if (now.isEmpty()) prefs.remove(LISTS_CHANGED_IN_SHEET) else prefs[LISTS_CHANGED_IN_SHEET] = now
        }
    }

    /** Import took in the Sheet's [target] list as it is at [revision]: Backups can write it again. */
    suspend fun acceptSheetList(target: String, revision: String?) {
        setListRevision(target, revision)
        setListChangedInSheet(target, false)
    }

    /** Overwrite: the next Backup of [targets] goes without a base, replacing whatever the Sheet has. */
    suspend fun overwriteListsInSheet(targets: Collection<String>) {
        context.syncStateStore.edit { prefs ->
            targets.forEach { prefs.remove(revisionKey(it)) }
            val now = prefs[LISTS_CHANGED_IN_SHEET].orEmpty() - targets.toSet()
            if (now.isEmpty()) prefs.remove(LISTS_CHANGED_IN_SHEET) else prefs[LISTS_CHANGED_IN_SHEET] = now
        }
    }

    private companion object {
        val LISTS_MERGED = booleanPreferencesKey("lists_merged_from_sheet")
        val LISTS_CHANGED_IN_SHEET = stringSetPreferencesKey("lists_changed_in_sheet")
        fun revisionKey(target: String) = stringPreferencesKey("list_revision_" + target)
        val HELD_SHEET_DELETIONS = stringSetPreferencesKey("held_sheet_deletions")
    }
}
