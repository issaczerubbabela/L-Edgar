package com.issaczerubbabel.ledgar.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.issaczerubbabel.ledgar.ui.theme.AppThemeOption
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
    private val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
    private val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
    private val SCRIPT_URL = stringPreferencesKey("script_url")

    val themePreference: Flow<AppThemeOption> = context.dataStore.data.map { prefs ->
        val raw = (prefs[THEME_PREFERENCE] ?: AppThemeOption.SYSTEM.name).uppercase()
        AppThemeOption.entries.firstOrNull { it.name == raw } ?: AppThemeOption.SYSTEM
    }

    /** Backward-compatible dark mode flow while callers migrate to [themePreference]. */
    val isDarkTheme: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[IS_DARK_THEME] ?: true }

    val scriptUrl: Flow<String?> = context.dataStore.data
        .map { prefs ->
            prefs[SCRIPT_URL]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

    suspend fun updateTheme(option: AppThemeOption) {
        context.dataStore.edit { prefs ->
            prefs[THEME_PREFERENCE] = option.name
            // Legacy key kept in sync as dark-only invariant.
            prefs[IS_DARK_THEME] = true
        }
    }

    /** Backward-compatible API while callers migrate to [updateTheme]. */
    suspend fun setDarkTheme(isDark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_DARK_THEME] = isDark
            if (!isDark) {
                prefs[THEME_PREFERENCE] = AppThemeOption.LAVENDER.name
            }
        }
    }

    suspend fun updateScriptUrl(newUrl: String) {
        context.dataStore.edit { prefs ->
            val normalized = newUrl.trim()
            if (normalized.isEmpty()) {
                prefs.remove(SCRIPT_URL)
            } else {
                prefs[SCRIPT_URL] = normalized
            }
        }
    }
}
