package com.issaczerubbabel.ledgar.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The suggested repeats the user answered Not a repeat to, by `RepeatSuggestion.key`. Device-local
 * like Repeat rules themselves: never synced or backed up, so a reinstall offers them again.
 */
interface DismissedRepeatSuggestions {
    val keys: Flow<Set<String>>
    suspend fun dismiss(key: String)
    suspend fun undismiss(key: String)
}

private val Context.repeatDataStore: DataStore<Preferences> by preferencesDataStore(name = "repeat_prefs")

@Singleton
class RepeatSuggestionPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) : DismissedRepeatSuggestions {

    private val DISMISSED = stringSetPreferencesKey("dismissed_suggestions")

    override val keys: Flow<Set<String>> = context.repeatDataStore.data.map { prefs -> prefs[DISMISSED].orEmpty() }

    override suspend fun dismiss(key: String) {
        context.repeatDataStore.edit { prefs -> prefs[DISMISSED] = prefs[DISMISSED].orEmpty() + key }
    }

    override suspend fun undismiss(key: String) {
        context.repeatDataStore.edit { prefs -> prefs[DISMISSED] = prefs[DISMISSED].orEmpty() - key }
    }
}
