package com.issaczerubbabel.ledgar.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.issaczerubbabel.ledgar.capture.CaptureSettingsSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.captureDataStore: DataStore<Preferences> by preferencesDataStore(name = "capture_prefs")

@Singleton
class CapturePreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : CaptureSettingsSource {

    private val enabledKey = booleanPreferencesKey("capture_enabled")
    private val categoryMappingKey = stringPreferencesKey("capture_category_mapping")
    private val gson = Gson()
    private val mappingType = object : TypeToken<Map<String, String>>() {}.type

    val enabled: Flow<Boolean> = context.captureDataStore.data.map { it[enabledKey] ?: false }

    val categoryMappingFlow: Flow<Map<String, String>> = context.captureDataStore.data.map { prefs ->
        parseMapping(prefs[categoryMappingKey])
    }

    override suspend fun isEnabled(): Boolean = enabled.first()

    override suspend fun categoryMapping(): Map<String, String> = categoryMappingFlow.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.captureDataStore.edit { it[enabledKey] = enabled }
    }

    /** Maps [canonical] to one of the user's Categories, or clears the mapping when [category] is blank. */
    suspend fun mapCategory(canonical: String, category: String?) {
        context.captureDataStore.edit { prefs ->
            val current = parseMapping(prefs[categoryMappingKey]).toMutableMap()
            if (category.isNullOrBlank()) current.remove(canonical) else current[canonical] = category.trim()
            prefs[categoryMappingKey] = gson.toJson(current)
        }
    }

    private fun parseMapping(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching { gson.fromJson<Map<String, String>>(json, mappingType) }.getOrNull().orEmpty()
    }
}
