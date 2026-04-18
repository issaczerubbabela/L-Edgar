package com.issaczerubbabel.ledgar.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.issaczerubbabel.ledgar.ui.theme.AppThemeOption
import com.issaczerubbabel.ledgar.util.PinSecurity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sheetsync_prefs")

enum class AppLockAuthMode {
    SYSTEM,
    PIN,
    SYSTEM_OR_PIN
}

enum class CashFlowChartStyle {
    BAR,
    LINE
}

@Singleton
class ThemePreferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
    private val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
    private val SCRIPT_URL = stringPreferencesKey("script_url")
    private val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    private val APP_LOCK_AUTH_MODE = stringPreferencesKey("app_lock_auth_mode")
    private val APP_LOCK_TIMEOUT_MINUTES = intPreferencesKey("app_lock_timeout_minutes")
    private val APP_PIN_HASH = stringPreferencesKey("app_pin_hash")
    private val APP_PIN_SALT = stringPreferencesKey("app_pin_salt")
    private val CASH_FLOW_CHART_STYLE = stringPreferencesKey("cash_flow_chart_style")

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

    val appLockEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[APP_LOCK_ENABLED] ?: false }

    val appLockAuthMode: Flow<AppLockAuthMode> = context.dataStore.data
        .map { prefs ->
            val raw = (prefs[APP_LOCK_AUTH_MODE] ?: AppLockAuthMode.SYSTEM.name).uppercase()
            AppLockAuthMode.entries.firstOrNull { it.name == raw } ?: AppLockAuthMode.SYSTEM
        }

    val appLockTimeoutMinutes: Flow<Int> = context.dataStore.data
        .map { prefs -> (prefs[APP_LOCK_TIMEOUT_MINUTES] ?: 5).coerceIn(1, 120) }

    val hasAppPinConfigured: Flow<Boolean> = context.dataStore.data
        .map { prefs ->
            !prefs[APP_PIN_HASH].isNullOrBlank() && !prefs[APP_PIN_SALT].isNullOrBlank()
        }

    val cashFlowChartStyle: Flow<CashFlowChartStyle> = context.dataStore.data
        .map { prefs ->
            val raw = (prefs[CASH_FLOW_CHART_STYLE] ?: CashFlowChartStyle.BAR.name).uppercase()
            CashFlowChartStyle.entries.firstOrNull { it.name == raw } ?: CashFlowChartStyle.BAR
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

    suspend fun updateAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[APP_LOCK_ENABLED] = enabled
        }
    }

    suspend fun updateAppLockAuthMode(mode: AppLockAuthMode) {
        context.dataStore.edit { prefs ->
            prefs[APP_LOCK_AUTH_MODE] = mode.name
        }
    }

    suspend fun updateAppLockTimeoutMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[APP_LOCK_TIMEOUT_MINUTES] = minutes.coerceIn(1, 120)
        }
    }

    suspend fun setAppPin(rawPin: String) {
        val salt = PinSecurity.generateSaltBase64()
        val hash = PinSecurity.hashPinBase64(rawPin, salt)
        context.dataStore.edit { prefs ->
            prefs[APP_PIN_SALT] = salt
            prefs[APP_PIN_HASH] = hash
        }
    }

    suspend fun clearAppPin() {
        context.dataStore.edit { prefs ->
            prefs.remove(APP_PIN_HASH)
            prefs.remove(APP_PIN_SALT)
            if (prefs[APP_LOCK_AUTH_MODE] == AppLockAuthMode.PIN.name) {
                prefs[APP_LOCK_AUTH_MODE] = AppLockAuthMode.SYSTEM.name
            }
        }
    }

    suspend fun verifyAppPin(rawPin: String): Boolean {
        val prefs = context.dataStore.data.first()
        val storedHash = prefs[APP_PIN_HASH] ?: return false
        val storedSalt = prefs[APP_PIN_SALT] ?: return false
        return PinSecurity.verifyPin(rawPin, storedSalt, storedHash)
    }

    suspend fun updateCashFlowChartStyle(style: CashFlowChartStyle) {
        context.dataStore.edit { prefs ->
            prefs[CASH_FLOW_CHART_STYLE] = style.name
        }
    }
}
