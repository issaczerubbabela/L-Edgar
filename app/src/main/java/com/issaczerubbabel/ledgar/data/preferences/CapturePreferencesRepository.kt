package com.issaczerubbabel.ledgar.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.issaczerubbabel.ledgar.capture.CaptureSettingsSource
import com.issaczerubbabel.ledgar.capture.source.BankEmails
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
    private val readBankEmailsKey = booleanPreferencesKey("capture_read_bank_emails")
    private val lastBankEmailReadKey = longPreferencesKey("capture_last_bank_email_read_at")
    private val bankEmailLabelKey = stringPreferencesKey("capture_bank_email_label")
    private val gson = Gson()
    private val mappingType = object : TypeToken<Map<String, String>>() {}.type

    val enabled: Flow<Boolean> = context.captureDataStore.data.map { it[enabledKey] ?: false }

    val categoryMappingFlow: Flow<Map<String, String>> = context.captureDataStore.data.map { prefs ->
        parseMapping(prefs[categoryMappingKey])
    }

    /** Read bank emails, off by default: Gmail is ignored until the user turns it on (#180). */
    val readBankEmails: Flow<Boolean> = context.captureDataStore.data.map { it[readBankEmailsKey] ?: false }

    /** When the latest bank email was read, or null if none has been, for the setup's status line. */
    val lastBankEmailReadAt: Flow<Long?> = context.captureDataStore.data.map { it[lastBankEmailReadKey] }

    override suspend fun isEnabled(): Boolean = enabled.first()

    override suspend fun readsBankEmails(): Boolean = readBankEmails.first()

    /** The Gmail label the user's filter applies; a bank email is read only under it. */
    val bankEmailLabelFlow: Flow<String> = context.captureDataStore.data.map { it[bankEmailLabelKey] ?: BankEmails.DEFAULT_LABEL }

    suspend fun bankEmailLabel(): String = bankEmailLabelFlow.first()

    suspend fun setBankEmailLabel(label: String) {
        context.captureDataStore.edit { it[bankEmailLabelKey] = label.trim() }
    }

    suspend fun setReadBankEmails(on: Boolean) {
        context.captureDataStore.edit { it[readBankEmailsKey] = on }
    }

    /** Notes that a bank email posted at [postedAt] was read; an older one read again never moves it back. */
    suspend fun markBankEmailRead(postedAt: Long) {
        context.captureDataStore.edit { prefs ->
            if (postedAt > (prefs[lastBankEmailReadKey] ?: 0L)) prefs[lastBankEmailReadKey] = postedAt
        }
    }

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
