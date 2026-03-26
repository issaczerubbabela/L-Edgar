package com.sheetsync.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sheetsync_prefs")

@Singleton
class ThemePreferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")

    /** Emits true for dark theme (default), false for light. */
    val isDarkTheme: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[IS_DARK_THEME] ?: true }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.dataStore.edit { prefs -> prefs[IS_DARK_THEME] = isDark }
    }
}
