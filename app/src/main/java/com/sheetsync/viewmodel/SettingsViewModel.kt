package com.sheetsync.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.preferences.ThemePreferenceRepository
import com.sheetsync.data.repository.ExpenseRepository
import com.sheetsync.util.CsvParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── State sealed classes ─────────────────────────────────────────────────────

sealed class ImportState {
    object Idle : ImportState()
    object Loading : ImportState()
    data class Success(val imported: Int, val skipped: Int) : ImportState()
    data class Error(val message: String) : ImportState()
}

sealed class SettingsUiEvent {
    data class ShowMessage(val message: String) : SettingsUiEvent()
}

// ── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ExpenseRepository,
    private val themeRepository: ThemePreferenceRepository
) : ViewModel() {

    // ── Theme toggle ─────────────────────────────────────────────────────────
    val isDarkTheme: StateFlow<Boolean> = themeRepository.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun toggleTheme() {
        viewModelScope.launch { themeRepository.setDarkTheme(!isDarkTheme.value) }
    }

    // ── Duplicate control (user-facing toggle) ───────────────────────────────
    /** When true, records that already exist in Room are skipped during import. */
    var skipDuplicates by mutableStateOf(true)

    // ── Reset confirmation state ─────────────────────────────────────────────
    var showResetConfirm by mutableStateOf(false)
    var resetDone by mutableStateOf(false)

    // ── Import state flows ───────────────────────────────────────────────────
    private val _sheetsState = MutableStateFlow<ImportState>(ImportState.Idle)
    val sheetsImportState: StateFlow<ImportState> = _sheetsState

    private val _csvState = MutableStateFlow<ImportState>(ImportState.Idle)
    val csvImportState: StateFlow<ImportState> = _csvState

    private val _uiEvents = MutableSharedFlow<SettingsUiEvent>(replay = 0)
    val uiEvents: SharedFlow<SettingsUiEvent> = _uiEvents.asSharedFlow()

    // ── Google Sheets import ─────────────────────────────────────────────────

    fun importFromSheets() {
        if (_sheetsState.value is ImportState.Loading) return
        viewModelScope.launch {
            _sheetsState.value = ImportState.Loading
            try {
                val result = repository.importFromGoogleSheets()
                _sheetsState.value = ImportState.Success(result.imported, result.skipped)
                _uiEvents.emit(
                    SettingsUiEvent.ShowMessage(
                        "Sync complete. ${result.imported} new transactions imported, ${result.restoredDropdowns} dropdown options restored, and ${result.restoredBudgets} budget rows restored."
                    )
                )
            } catch (e: Exception) {
                _sheetsState.value = ImportState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ── CSV import ───────────────────────────────────────────────────────────

    fun importFromCsv(uri: Uri) {
        if (_csvState.value is ImportState.Loading) return
        viewModelScope.launch {
            _csvState.value = ImportState.Loading
            try {
                val result = CsvParser.parse(context, uri)
                val (imported, dedupSkipped) = insertRecords(result.records)
                _csvState.value = ImportState.Success(imported, dedupSkipped + result.skippedLines)
            } catch (e: Exception) {
                _csvState.value = ImportState.Error(e.message ?: "Could not read file")
            }
        }
    }

    // ── Reset all data ───────────────────────────────────────────────────────

    fun resetAllData() {
        viewModelScope.launch {
            repository.deleteAll()
            showResetConfirm = false
            resetDone = true
        }
    }

    fun clearResetDone() { resetDone = false }

    // ── Shared helpers ───────────────────────────────────────────────────────

    /**
     * Insert [records] into Room.
     * If [skipDuplicates] is true, checks for an existing record with the same
     * date + type + category + amount before inserting.
     * Returns (importedCount, skippedCount).
     */
    private suspend fun insertRecords(records: List<ExpenseRecord>): Pair<Int, Int> {
        var imported = 0; var skipped = 0
        for (record in records) {
            val isDup = skipDuplicates && repository.isDuplicate(record.date, record.type, record.category, record.amount)
            if (isDup) { skipped++ } else { repository.save(record); imported++ }
        }
        return imported to skipped
    }

    fun resetSheetsState() { _sheetsState.value = ImportState.Idle }
    fun resetCsvState() { _csvState.value = ImportState.Idle }
}
