package com.issaczerubbabel.ledgar.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.preferences.AppLockAuthMode
import com.issaczerubbabel.ledgar.data.preferences.CashFlowChartStyle
import com.issaczerubbabel.ledgar.data.preferences.ThemePreferenceRepository
import com.issaczerubbabel.ledgar.data.remote.ApiService
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.data.repository.SyncConflict
import com.issaczerubbabel.ledgar.sync.BackupWorker
import com.issaczerubbabel.ledgar.sync.SyncScheduler
import com.issaczerubbabel.ledgar.sync.SyncStatus
import com.issaczerubbabel.ledgar.data.preferences.SyncStateRepository
import com.issaczerubbabel.ledgar.ui.theme.AppThemeOption
import com.issaczerubbabel.ledgar.util.CsvParser
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

private const val BUCKET_SCRIPT_OUTDATED_MESSAGE =
    "Your Apps Script is out of date, so your buckets were not backed up. " +
        "Copy the latest script from Database Setup and redeploy it."

sealed class SettingsUiEvent {
    data class ShowMessage(val message: String) : SettingsUiEvent()
    data object ShowSyncCompletedToast : SettingsUiEvent()
}

sealed class ConnectionTestState {
    object Idle : ConnectionTestState()
    object Testing : ConnectionTestState()
    object Success : ConnectionTestState()
    data class Error(val message: String) : ConnectionTestState()
}

// ── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ExpenseRepository,
    private val themeRepository: ThemePreferenceRepository,
    private val apiService: ApiService,
    private val syncScheduler: SyncScheduler,
    private val syncState: SyncStateRepository
) : ViewModel() {

    init {
        observeBackupStatus()
    }

    val themeState: StateFlow<AppThemeOption> = themeRepository.themePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppThemeOption.SYSTEM)

    // Backward-compatible dark flag while UI migrates to a theme picker.
    val isDarkTheme: StateFlow<Boolean> = themeRepository.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val scriptUrl: StateFlow<String?> = themeRepository.scriptUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val appLockEnabled: StateFlow<Boolean> = themeRepository.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appLockAuthMode: StateFlow<AppLockAuthMode> = themeRepository.appLockAuthMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLockAuthMode.SYSTEM)

    val appLockTimeoutMinutes: StateFlow<Int> = themeRepository.appLockTimeoutMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val hasAppPinConfigured: StateFlow<Boolean> = themeRepository.hasAppPinConfigured
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val cashFlowChartStyle: StateFlow<CashFlowChartStyle> = themeRepository.cashFlowChartStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CashFlowChartStyle.BAR)

    fun updateTheme(option: AppThemeOption) {
        viewModelScope.launch { themeRepository.updateTheme(option) }
    }

    fun updateCashFlowChartStyle(style: CashFlowChartStyle) {
        viewModelScope.launch {
            themeRepository.updateCashFlowChartStyle(style)
            _uiEvents.emit(
                SettingsUiEvent.ShowMessage(
                    if (style == CashFlowChartStyle.LINE) {
                        "Cash flow chart style set to line"
                    } else {
                        "Cash flow chart style set to bar"
                    }
                )
            )
        }
    }

    fun toggleTheme() {
        viewModelScope.launch { themeRepository.setDarkTheme(!isDarkTheme.value) }
    }

    fun updateScriptUrl(newUrl: String) {
        viewModelScope.launch { themeRepository.updateScriptUrl(newUrl) }
    }

    fun updateAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themeRepository.updateAppLockEnabled(enabled)
            if (enabled) {
                _uiEvents.emit(SettingsUiEvent.ShowMessage("App lock enabled"))
            } else {
                _uiEvents.emit(SettingsUiEvent.ShowMessage("App lock disabled"))
            }
        }
    }

    fun updateAppLockAuthMode(mode: AppLockAuthMode) {
        viewModelScope.launch {
            if ((mode == AppLockAuthMode.PIN || mode == AppLockAuthMode.SYSTEM_OR_PIN) && !hasAppPinConfigured.value) {
                _uiEvents.emit(SettingsUiEvent.ShowMessage("Set an app PIN first"))
                return@launch
            }
            themeRepository.updateAppLockAuthMode(mode)
            _uiEvents.emit(SettingsUiEvent.ShowMessage("Unlock method updated"))
        }
    }

    fun updateAppLockTimeoutMinutes(minutes: Int) {
        viewModelScope.launch {
            themeRepository.updateAppLockTimeoutMinutes(minutes)
            _uiEvents.emit(SettingsUiEvent.ShowMessage("Re-lock timeout updated to $minutes min"))
        }
    }

    fun setOrChangeAppPin(pin: String, confirmPin: String) {
        val normalizedPin = pin.trim()
        if (!normalizedPin.matches(Regex("^[0-9]{4,8}$"))) {
            viewModelScope.launch {
                _uiEvents.emit(SettingsUiEvent.ShowMessage("PIN must be 4 to 8 digits"))
            }
            return
        }
        if (normalizedPin != confirmPin.trim()) {
            viewModelScope.launch {
                _uiEvents.emit(SettingsUiEvent.ShowMessage("PIN and confirmation do not match"))
            }
            return
        }

        viewModelScope.launch {
            themeRepository.setAppPin(normalizedPin)
            if (appLockAuthMode.value == AppLockAuthMode.SYSTEM) {
                themeRepository.updateAppLockAuthMode(AppLockAuthMode.SYSTEM_OR_PIN)
            }
            _uiEvents.emit(SettingsUiEvent.ShowMessage("App PIN saved"))
        }
    }

    fun removeAppPin() {
        viewModelScope.launch {
            themeRepository.clearAppPin()
            _uiEvents.emit(SettingsUiEvent.ShowMessage("App PIN removed"))
        }
    }

    fun testScriptConnection(url: String) {
        if (_connectionTestState.value is ConnectionTestState.Testing) return
        val normalized = url.trim()
        if (normalized.isBlank()) {
            _connectionTestState.value = ConnectionTestState.Error("Enter a Web App URL")
            return
        }

        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.Testing
            try {
                val response = apiService.importDropdownOptions(
                    url = normalized,
                    target = "dropdowns"
                )
                val body = response.body()
                _connectionTestState.value = if (
                    response.isSuccessful && body?.status.equals("ok", ignoreCase = true)
                ) {
                    ConnectionTestState.Success
                } else {
                    ConnectionTestState.Error(body?.message ?: "HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                _connectionTestState.value = ConnectionTestState.Error(e.message ?: "Connection failed")
            }
        }
    }

    fun resetConnectionTestState() {
        _connectionTestState.value = ConnectionTestState.Idle
    }

    fun backupToGoogleSheets() {
        if (_backupState.value is ImportState.Loading) return
        viewModelScope.launch {
            _backupState.value = ImportState.Loading
            backupRequestedFromSettings = true
            syncScheduler.backupNow()
            _uiEvents.emit(SettingsUiEvent.ShowMessage("Backup started. Syncing accounts, budgets, and dropdown options to Google Sheets."))
        }
    }

    // ── Reset confirmation state ─────────────────────────────────────────────
    var showResetConfirm by mutableStateOf(false)
    var resetDone by mutableStateOf(false)

    // ── Import state flows ───────────────────────────────────────────────────
    private val _sheetsState = MutableStateFlow<ImportState>(ImportState.Idle)
    val sheetsImportState: StateFlow<ImportState> = _sheetsState

    private val _csvState = MutableStateFlow<ImportState>(ImportState.Idle)
    val csvImportState: StateFlow<ImportState> = _csvState

    private val _backupState = MutableStateFlow<ImportState>(ImportState.Idle)
    val backupState: StateFlow<ImportState> = _backupState

    val syncConflicts: StateFlow<List<SyncConflict>> = repository.observeSyncConflicts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showConflictSheet = MutableStateFlow(false)
    val showConflictSheet: StateFlow<Boolean> = _showConflictSheet

    /** Local ids of Transactions the Sheet no longer has, waiting for the user to decide. */
    val heldSheetDeletions: StateFlow<Set<Long>> = syncState.heldSheetDeletions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val possibleDuplicates: StateFlow<List<List<ExpenseRecord>>> = repository.observePossibleDuplicates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showDuplicatesSheet = MutableStateFlow(false)
    val showDuplicatesSheet: StateFlow<Boolean> = _showDuplicatesSheet

    val syncStatus: StateFlow<SyncStatus> = syncScheduler.transactionSyncStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.Idle)

    private val _conflictResolutionState = MutableStateFlow<ImportState>(ImportState.Idle)
    val conflictResolutionState: StateFlow<ImportState> = _conflictResolutionState

    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState

    private var backupRequestedFromSettings: Boolean = false

    private val _uiEvents = MutableSharedFlow<SettingsUiEvent>(replay = 0)
    val uiEvents: SharedFlow<SettingsUiEvent> = _uiEvents.asSharedFlow()

    // ── Google Sheets import ─────────────────────────────────────────────────

    fun importFromSheets() {
        if (_sheetsState.value is ImportState.Loading) return
        viewModelScope.launch {
            _sheetsState.value = ImportState.Loading
            _conflictResolutionState.value = ImportState.Idle
            try {
                val result = repository.importFromGoogleSheets()
                _sheetsState.value = ImportState.Success(result.imported, result.skipped)
                _uiEvents.emit(
                    SettingsUiEvent.ShowMessage(
                        "Sync import complete. ${result.restoredDropdowns} dropdown options restored, ${result.restoredAccounts} accounts restored, ${result.restoredBudgets} budget rows restored, ${result.restoredCycles} budget ${if (result.restoredCycles == 1) "cycle" else "cycles"} restored, and ${result.imported} transaction changes pulled from the Sheet."
                    )
                )
                if (result.conflicts.isNotEmpty()) {
                    _showConflictSheet.value = true
                    _uiEvents.emit(
                        SettingsUiEvent.ShowMessage(
                            "${result.conflicts.size} sync conflicts detected. Resolve them to complete sync."
                        )
                    )
                }
            } catch (e: Exception) {
                _sheetsState.value = ImportState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun openConflictSheet() { _showConflictSheet.value = true }

    fun keepLocalConflict(conflict: SyncConflict) {
        completeConflictAction(actionLabel = "Kept the phone's version") { repository.resolveConflictKeepPhone(conflict) }
    }

    fun updateDeviceConflict(conflict: SyncConflict) {
        completeConflictAction(actionLabel = "Kept the Sheet's version") { repository.resolveConflictKeepSheet(conflict) }
    }

    fun keepBothConflict(conflict: SyncConflict) {
        completeConflictAction(actionLabel = "Kept both") { repository.resolveConflictKeepBoth(conflict) }
    }

    fun deleteFromCloudConflict(conflict: SyncConflict) {
        completeConflictAction(actionLabel = "Deleted everywhere") { repository.resolveConflictDeleteEverywhere(conflict) }
    }

    fun dismissSyncConflictSheet() {
        _showConflictSheet.value = false
        _conflictResolutionState.value = ImportState.Idle
    }

    // ── Transactions deleted from the Sheet, held back by the safety brake ───

    fun deleteHeldFromPhone() = syncScheduler.requestFullSync(allowMassDelete = true)

    fun keepHeldAndReupload() {
        viewModelScope.launch { repository.keepTransactionsMissingFromSheet(heldSheetDeletions.value) }
    }

    // ── Possible duplicates ──────────────────────────────────────────────────

    fun openDuplicatesSheet() { _showDuplicatesSheet.value = true }
    fun dismissDuplicatesSheet() { _showDuplicatesSheet.value = false }

    /** Deletes one copy on the phone; Sync then deletes it from the Sheet by its Transaction ID. */
    fun deleteDuplicate(record: ExpenseRecord) {
        viewModelScope.launch { repository.delete(record) }
    }

    fun syncNow() {
        viewModelScope.launch { syncScheduler.retrySync() }
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
     * Records matching an existing entry (date + type + category + amount)
     * are skipped to prevent duplicate imports.
     * Returns (importedCount, skippedCount).
     */
    private suspend fun insertRecords(records: List<ExpenseRecord>): Pair<Int, Int> {
        var imported = 0; var skipped = 0
        for (record in records) {
            val isDup = repository.isDuplicate(record.date, record.type, record.category, record.amount)
            if (isDup) { skipped++ } else { repository.save(record); imported++ }
        }
        return imported to skipped
    }

    fun resetSheetsState() { _sheetsState.value = ImportState.Idle }
    fun resetCsvState() { _csvState.value = ImportState.Idle }
    fun resetBackupState() { _backupState.value = ImportState.Idle }

    private fun completeConflictAction(actionLabel: String, action: suspend () -> Unit) {
        if (_conflictResolutionState.value is ImportState.Loading) return
        viewModelScope.launch {
            _conflictResolutionState.value = ImportState.Loading
            try {
                val remaining = (syncConflicts.value.size - 1).coerceAtLeast(0)
                action()
                // Conflicts don't count as unsynced changes, so the resolved one needs an explicit Sync.
                syncScheduler.requestSync()
                _conflictResolutionState.value = ImportState.Success(imported = 1, skipped = remaining)
                _uiEvents.emit(SettingsUiEvent.ShowMessage("$actionLabel. Conflicts left: $remaining."))
                if (remaining == 0) {
                    _showConflictSheet.value = false
                    _uiEvents.emit(SettingsUiEvent.ShowSyncCompletedToast)
                }
            } catch (e: Exception) {
                _conflictResolutionState.value = ImportState.Error(e.message ?: "Conflict resolution failed")
            }
        }
    }

    private fun observeBackupStatus() {
        viewModelScope.launch {
            syncScheduler.backupWorkInfos.collect { infos ->
                if (!backupRequestedFromSettings) return@collect

                val latest = infos.firstOrNull() ?: return@collect
                when (latest.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.RUNNING,
                    WorkInfo.State.BLOCKED -> {
                        _backupState.value = ImportState.Loading
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        val dropdownCount = latest.outputData.getInt(BackupWorker.KEY_DROPDOWN_BACKUP_COUNT, 0)
                        val budgetCount = latest.outputData.getInt(BackupWorker.KEY_BUDGET_BACKUP_COUNT, 0)
                        val accountCount = latest.outputData.getInt(BackupWorker.KEY_ACCOUNTS_BACKUP_COUNT, 0)
                        val bucketCount = latest.outputData.getInt(BackupWorker.KEY_BUCKET_BACKUP_COUNT, 0)
                        val total = (dropdownCount + budgetCount + accountCount + bucketCount.coerceAtLeast(0)).coerceAtLeast(0)
                        _backupState.value = ImportState.Success(imported = total, skipped = 0)
                        if (bucketCount == BackupWorker.SCRIPT_OUTDATED) {
                            _uiEvents.emit(SettingsUiEvent.ShowMessage(BUCKET_SCRIPT_OUTDATED_MESSAGE))
                        }
                        backupRequestedFromSettings = false
                    }

                    WorkInfo.State.FAILED,
                    WorkInfo.State.CANCELLED -> {
                        val reason = latest.outputData.getString(BackupWorker.KEY_ERROR_MESSAGE)
                            ?: "Backup failed"
                        _backupState.value = ImportState.Error(reason)
                        _uiEvents.emit(SettingsUiEvent.ShowMessage(reason))
                        backupRequestedFromSettings = false
                    }
                }
            }
        }
    }
}
