package com.issaczerubbabel.ledgar.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.preferences.CashFlowChartStyle
import com.issaczerubbabel.ledgar.data.remote.ImportRecordDto
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.SyncConflict
import com.issaczerubbabel.ledgar.sync.SyncStatus
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import com.issaczerubbabel.ledgar.ui.theme.AppThemeOption
import com.issaczerubbabel.ledgar.ui.theme.ExpenseRed
import com.issaczerubbabel.ledgar.ui.theme.IncomeGreen
import com.issaczerubbabel.ledgar.data.preferences.AppLockAuthMode
import com.issaczerubbabel.ledgar.ui.components.ExportDialog
import com.issaczerubbabel.ledgar.viewmodel.ExportViewModel
import com.issaczerubbabel.ledgar.viewmodel.ImportState
import com.issaczerubbabel.ledgar.viewmodel.SettingsUiEvent
import com.issaczerubbabel.ledgar.viewmodel.SettingsViewModel
import java.io.File

@Composable
private fun responsiveTextSize(baseSp: Float, minSp: Float = 12f, maxSp: Float = 30f) =
    (
        baseSp * (LocalConfiguration.current.screenWidthDp / 411f).coerceIn(0.9f, 1.08f)
    ).coerceIn(minSp, maxSp).sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    onNavigateToDropdownManagement: () -> Unit,
    onNavigateToAppsScriptSetup: () -> Unit,
    onNavigateToChangelog: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
    exportVm: ExportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val exportState by exportVm.uiState.collectAsStateWithLifecycle()
    val sheetsState by vm.sheetsImportState.collectAsStateWithLifecycle()
    val csvState by vm.csvImportState.collectAsStateWithLifecycle()
    val backupState by vm.backupState.collectAsStateWithLifecycle()
    val syncConflicts by vm.syncConflicts.collectAsStateWithLifecycle()
    val conflictResolutionState by vm.conflictResolutionState.collectAsStateWithLifecycle()
    val showConflictSheet by vm.showConflictSheet.collectAsStateWithLifecycle()
    val heldSheetDeletions by vm.heldSheetDeletions.collectAsStateWithLifecycle()
    val possibleDuplicates by vm.possibleDuplicates.collectAsStateWithLifecycle()
    val showDuplicatesSheet by vm.showDuplicatesSheet.collectAsStateWithLifecycle()
    val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
    var heldDeletionsDismissed by remember { mutableStateOf(false) }
    val currentTheme by vm.themeState.collectAsStateWithLifecycle()
    val scriptUrl by vm.scriptUrl.collectAsStateWithLifecycle()
    val appLockEnabled by vm.appLockEnabled.collectAsStateWithLifecycle()
    val appLockAuthMode by vm.appLockAuthMode.collectAsStateWithLifecycle()
    val appLockTimeoutMinutes by vm.appLockTimeoutMinutes.collectAsStateWithLifecycle()
    val hasAppPinConfigured by vm.hasAppPinConfigured.collectAsStateWithLifecycle()
    val cashFlowChartStyle by vm.cashFlowChartStyle.collectAsStateWithLifecycle()
    var themeDropdownExpanded by remember { mutableStateOf(false) }
    var chartStyleDropdownExpanded by remember { mutableStateOf(false) }
    var authModeDropdownExpanded by remember { mutableStateOf(false) }
    var timeoutDropdownExpanded by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showRemovePinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm.resetDone) {
        if (vm.resetDone) { snackbarHostState.showSnackbar("All data deleted."); vm.clearResetDone() }
    }

    LaunchedEffect(vm) {
        vm.uiEvents.collect { event ->
            when (event) {
                is SettingsUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                SettingsUiEvent.ShowSyncCompletedToast -> Toast.makeText(context, "Sync completed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.importFromCsv(it) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { exportVm.exportDataToUri(it) }
    }

    LaunchedEffect(exportState.pendingFileName) {
        val fileName = exportState.pendingFileName ?: return@LaunchedEffect
        exportLauncher.launch(fileName)
        exportVm.consumeExportRequest()
    }

    LaunchedEffect(exportState.statusMessage) {
        val message = exportState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        exportVm.clearStatusMessage()
    }

    if (exportState.showDialog) {
        ExportDialog(
            selected = exportState.selectedInterval,
            anchorMonth = exportState.anchorMonth,
            canPickLaterMonth = exportState.canPickLaterMonth,
            customStart = exportState.customStartDateInput,
            customEnd = exportState.customEndDateInput,
            onSelect = exportVm::selectInterval,
            onPreviousMonth = exportVm::previousMonth,
            onNextMonth = exportVm::nextMonth,
            onStartChanged = exportVm::updateCustomStart,
            onEndChanged = exportVm::updateCustomEnd,
            onDismiss = exportVm::closeDialog,
            onConfirm = exportVm::requestExportDocument
        )
    }

    // Reset confirmation dialog
    if (vm.showResetConfirm) {
        AlertDialog(
            onDismissRequest = { vm.showResetConfirm = false },
            icon = { Icon(Icons.Filled.DeleteForever, null, tint = ExpenseRed) },
            title = { Text("Reset All Data?") },
            text = { Text("This will permanently delete every local transaction record. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { vm.resetAllData() },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) { Text("Delete Everything") }
            },
            dismissButton = { TextButton(onClick = { vm.showResetConfirm = false }) { Text("Cancel") } }
        )
    }

    if (heldSheetDeletions.isNotEmpty() && !heldDeletionsDismissed) {
        val count = heldSheetDeletions.size
        AlertDialog(
            onDismissRequest = { heldDeletionsDismissed = true },
            icon = { Icon(Icons.Filled.Warning, null) },
            title = { Text("$count transactions were deleted from the Sheet") },
            text = {
                Text(
                    "They're still on this phone. Delete them here too, or keep them and put them back in the Sheet? " +
                        "Nothing changes until you choose."
                )
            },
            confirmButton = {
                Button(onClick = { heldDeletionsDismissed = true; vm.deleteHeldFromPhone() }) { Text("Delete from phone") }
            },
            dismissButton = {
                TextButton(onClick = { heldDeletionsDismissed = true; vm.keepHeldAndReupload() }) { Text("Keep and re-upload") }
            }
        )
    }

    if (showDuplicatesSheet) {
        DuplicatesSheet(
            groups = possibleDuplicates,
            onDelete = vm::deleteDuplicate,
            onDismiss = vm::dismissDuplicatesSheet
        )
    }

    if (showConflictSheet && syncConflicts.isNotEmpty()) {
        SyncResolutionSheet(
            conflicts = syncConflicts,
            resolutionState = conflictResolutionState,
            onKeepLocal = vm::keepLocalConflict,
            onUpdateDevice = vm::updateDeviceConflict,
            onKeepBoth = vm::keepBothConflict,
            onDeleteFromCloud = vm::deleteFromCloudConflict,
            onDismiss = vm::dismissSyncConflictSheet
        )
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pinInput = ""
                confirmPinInput = ""
            },
            title = { Text(if (hasAppPinConfigured) "Change App PIN" else "Set App PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { value ->
                            pinInput = value.filter(Char::isDigit).take(8)
                        },
                        label = { Text("PIN (4-8 digits)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        )
                    )
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { value ->
                            confirmPinInput = value.filter(Char::isDigit).take(8)
                        },
                        label = { Text("Confirm PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setOrChangeAppPin(pinInput, confirmPinInput)
                    showPinDialog = false
                    pinInput = ""
                    confirmPinInput = ""
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDialog = false
                    pinInput = ""
                    confirmPinInput = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRemovePinDialog) {
        AlertDialog(
            onDismissRequest = { showRemovePinDialog = false },
            title = { Text("Remove App PIN?") },
            text = { Text("PIN-based unlock options will be disabled.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeAppPin()
                    showRemovePinDialog = false
                }) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemovePinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            )
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = responsiveTextSize(baseSp = 28f, minSp = 24f, maxSp = 30f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )

            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )

            SettingsListItem(title = "Theme", icon = Icons.Filled.Palette) {
                ExposedDropdownMenuBox(
                    expanded = themeDropdownExpanded,
                    onExpandedChange = { themeDropdownExpanded = !themeDropdownExpanded }
                ) {
                    TextField(
                        value = themeLabel(currentTheme),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeDropdownExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .widthIn(min = 132.dp, max = 188.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = themeDropdownExpanded,
                        onDismissRequest = { themeDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("System MUI") },
                            onClick = {
                                vm.updateTheme(AppThemeOption.SYSTEM)
                                themeDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Lavender") },
                            onClick = {
                                vm.updateTheme(AppThemeOption.LAVENDER)
                                themeDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Teal") },
                            onClick = {
                                vm.updateTheme(AppThemeOption.TEAL)
                                themeDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Red") },
                            onClick = {
                                vm.updateTheme(AppThemeOption.RED)
                                themeDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            SettingsListItem(title = "Cash Flow Graph", icon = Icons.Filled.ShowChart) {
                ExposedDropdownMenuBox(
                    expanded = chartStyleDropdownExpanded,
                    onExpandedChange = { chartStyleDropdownExpanded = !chartStyleDropdownExpanded }
                ) {
                    TextField(
                        value = cashFlowChartStyleLabel(cashFlowChartStyle),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = chartStyleDropdownExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .widthIn(min = 132.dp, max = 188.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = chartStyleDropdownExpanded,
                        onDismissRequest = { chartStyleDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Bars") },
                            onClick = {
                                vm.updateCashFlowChartStyle(CashFlowChartStyle.BAR)
                                chartStyleDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Lines") },
                            onClick = {
                                vm.updateCashFlowChartStyle(CashFlowChartStyle.LINE)
                                chartStyleDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "Security",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )

            SettingsListItem(
                title = "Enable App Lock",
                icon = Icons.Filled.Lock
            ) {
                Switch(
                    checked = appLockEnabled,
                    onCheckedChange = vm::updateAppLockEnabled
                )
            }

            if (appLockEnabled) {
                SettingsListItem(
                    title = "Unlock Method",
                    icon = Icons.Filled.Fingerprint
                ) {
                    ExposedDropdownMenuBox(
                        expanded = authModeDropdownExpanded,
                        onExpandedChange = { authModeDropdownExpanded = !authModeDropdownExpanded }
                    ) {
                        TextField(
                            value = authModeLabel(appLockAuthMode),
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = authModeDropdownExpanded)
                            },
                            colors = ExposedDropdownMenuDefaults.textFieldColors(),
                            modifier = Modifier
                                .menuAnchor()
                                .widthIn(min = 166.dp, max = 220.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = authModeDropdownExpanded,
                            onDismissRequest = { authModeDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("System biometric/device") },
                                onClick = {
                                    vm.updateAppLockAuthMode(AppLockAuthMode.SYSTEM)
                                    authModeDropdownExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("App PIN") },
                                onClick = {
                                    vm.updateAppLockAuthMode(AppLockAuthMode.PIN)
                                    authModeDropdownExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("System or App PIN") },
                                onClick = {
                                    vm.updateAppLockAuthMode(AppLockAuthMode.SYSTEM_OR_PIN)
                                    authModeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                SettingsListItem(
                    title = "Re-lock Timeout",
                    icon = Icons.Filled.Timer
                ) {
                    ExposedDropdownMenuBox(
                        expanded = timeoutDropdownExpanded,
                        onExpandedChange = { timeoutDropdownExpanded = !timeoutDropdownExpanded }
                    ) {
                        TextField(
                            value = "$appLockTimeoutMinutes min",
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = timeoutDropdownExpanded)
                            },
                            colors = ExposedDropdownMenuDefaults.textFieldColors(),
                            modifier = Modifier
                                .menuAnchor()
                                .widthIn(min = 110.dp, max = 160.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = timeoutDropdownExpanded,
                            onDismissRequest = { timeoutDropdownExpanded = false }
                        ) {
                            listOf(1, 2, 5, 10, 15, 30, 60).forEach { timeout ->
                                DropdownMenuItem(
                                    text = { Text("$timeout min") },
                                    onClick = {
                                        vm.updateAppLockTimeoutMinutes(timeout)
                                        timeoutDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                SettingsListItem(
                    title = if (hasAppPinConfigured) "App PIN Configured" else "Set App PIN",
                    icon = Icons.Filled.Password
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showPinDialog = true }) {
                            Text(if (hasAppPinConfigured) "Change" else "Set")
                        }
                        if (hasAppPinConfigured) {
                            TextButton(onClick = { showRemovePinDialog = true }) {
                                Text("Remove", color = ExpenseRed)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "Database Setup",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )

            SettingsListItem(
                title = "Database Setup (Sheets)",
                icon = Icons.Filled.CloudSync,
                onClick = onNavigateToAppsScriptSetup
            ) {
                val isConnected = !scriptUrl.isNullOrBlank()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = if (isConnected) IncomeGreen else ExpenseRed
                    )
                    Text(
                        text = if (isConnected) "Connected" else "Not Connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConnected) IncomeGreen else ExpenseRed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }
            }

            if (syncStatus == SyncStatus.NeedsScriptUpdate) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onNavigateToAppsScriptSetup)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            text = "Sync is paused: your Apps Script is out of date. Tap to open Database Setup, " +
                                "copy the new script into Apps Script and deploy a new version.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            SettingsListItem(title = "Sync now", icon = Icons.Filled.Sync, onClick = vm::syncNow) {
                Text(
                    text = when (syncStatus) {
                        SyncStatus.Syncing -> "Syncing..."
                        SyncStatus.Synced -> "Synced"
                        SyncStatus.Failed -> "Failed, will retry"
                        SyncStatus.NeedsScriptUpdate -> "Paused"
                        SyncStatus.Idle -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (syncConflicts.isNotEmpty()) {
                SettingsListItem(
                    title = "Resolve sync conflicts",
                    icon = Icons.Filled.Warning,
                    iconTint = ExpenseRed,
                    onClick = vm::openConflictSheet
                ) {
                    Text("${syncConflicts.size}", style = MaterialTheme.typography.titleMedium, color = ExpenseRed)
                }
            }

            SettingsListItem(
                title = "Find duplicate transactions",
                icon = Icons.Filled.ContentCopy,
                onClick = vm::openDuplicatesSheet
            ) {
                Text(
                    text = if (possibleDuplicates.isEmpty()) "None" else "${possibleDuplicates.size} groups",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsListItem(title = "Backup to Google Sheets") {
                ImportActionControl(
                    state = backupState,
                    idleIcon = Icons.Filled.CloudUpload,
                    onRun = vm::backupToGoogleSheets,
                    onDismiss = vm::resetBackupState
                )
            }

            SettingsListItem(title = "Import from Google Sheets") {
                ImportActionControl(
                    state = sheetsState,
                    idleIcon = Icons.Filled.CloudDownload,
                    onRun = vm::importFromSheets,
                    onDismiss = vm::resetSheetsState
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "Data",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )

            SettingsListItem(
                title = "Manage Categories & Dropdowns",
                icon = Icons.Filled.Tune,
                onClick = onNavigateToDropdownManagement
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            SettingsListItem(title = "Import from CSV") {
                ImportActionControl(
                    state = csvState,
                    idleIcon = Icons.Filled.FileOpen,
                    onRun = { csvLauncher.launch("text/*") },
                    onDismiss = vm::resetCsvState
                )
            }

            SettingsListItem(
                title = "Export to CSV",
                onClick = exportVm::openDialog
            ) {
                IconButton(onClick = exportVm::openDialog) {
                    Icon(
                        imageVector = Icons.Filled.TableChart,
                        contentDescription = "Export transactions to CSV",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            SettingsListItem(
                title = "Reset All Data",
                iconTint = ExpenseRed
            ) {
                IconButton(onClick = { vm.showResetConfirm = true }) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = "Reset data", tint = ExpenseRed)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "About",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )

            SettingsListItem(
                title = "Changelog",
                onClick = onNavigateToChangelog
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Open changelog",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            SettingsListItem(
                title = "Share App",
                onClick = { shareAppApk(context) }
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share app",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsListItem(
    title: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable RowScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .then(clickableModifier)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
private fun ImportActionControl(
    state: ImportState,
    idleIcon: ImageVector,
    onRun: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is ImportState.Loading -> Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        is ImportState.Success -> IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Dismiss import status", tint = IncomeGreen)
        }
        is ImportState.Error -> IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = "Dismiss import status", tint = ExpenseRed)
        }
        is ImportState.Idle -> IconButton(onClick = onRun) {
            Icon(idleIcon, contentDescription = "Run import")
        }
    }
}

private fun themeLabel(option: AppThemeOption): String = when (option) {
    AppThemeOption.SYSTEM -> "System MUI"
    AppThemeOption.LAVENDER -> "Lavender"
    AppThemeOption.TEAL -> "Teal"
    AppThemeOption.RED -> "Red"
}

private fun cashFlowChartStyleLabel(style: CashFlowChartStyle): String = when (style) {
    CashFlowChartStyle.BAR -> "Bars"
    CashFlowChartStyle.LINE -> "Lines"
}

private fun authModeLabel(mode: AppLockAuthMode): String = when (mode) {
    AppLockAuthMode.SYSTEM -> "System biometric/device"
    AppLockAuthMode.PIN -> "App PIN"
    AppLockAuthMode.SYSTEM_OR_PIN -> "System or App PIN"
}

private fun shareAppApk(context: Context) {
    runCatching {
        val apkFile = File(context.applicationInfo.sourceDir)
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, apkUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share App APK"))
    }.onFailure {
        Toast.makeText(context, "Unable to share APK", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncResolutionSheet(
    conflicts: List<SyncConflict>,
    resolutionState: ImportState,
    onKeepLocal: (SyncConflict) -> Unit,
    onUpdateDevice: (SyncConflict) -> Unit,
    onKeepBoth: (SyncConflict) -> Unit,
    onDeleteFromCloud: (SyncConflict) -> Unit,
    onDismiss: () -> Unit
) {
    val canResolve = resolutionState !is ImportState.Loading
    ModalBottomSheet(
        onDismissRequest = {
            if (canResolve) onDismiss()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Conflict Resolution",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "These changed differently on this phone and in the Sheet since the last sync. Choose which to keep.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(conflicts) { index, conflict ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Conflict ${index + 1} of ${conflicts.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("On this phone", style = MaterialTheme.typography.labelLarge)
                                        ConflictValue("Date", conflict.localTx.date, false)
                                        ConflictValue("Amount", conflict.localTx.amount.toString(), false)
                                        ConflictValue("Category", conflict.localTx.category.ifBlank { "-" }, false)
                                        ConflictValue("Description", conflict.localTx.description.ifBlank { "-" }, false)
                                        ConflictValue("Remarks", conflict.localTx.remarks.ifBlank { "-" }, false)
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val cloudCategory = sheetCategory(conflict.sheetTx)
                                        val amountDiff = !approximatelyEqual(conflict.localTx.amount, conflict.sheetTx.amount)
                                        val categoryDiff = !conflict.localTx.category.trim().equals(cloudCategory.trim(), ignoreCase = true)
                                        val descriptionDiff = !conflict.localTx.description.trim().equals(conflict.sheetTx.description.trim(), ignoreCase = true)
                                        val dateDiff = conflict.localTx.date != conflict.sheetTx.date
                                        val remarksDiff = conflict.localTx.remarks.trim() != conflict.sheetTx.remarks.trim()

                                        Text("In the Sheet", style = MaterialTheme.typography.labelLarge)
                                        ConflictValue("Date", conflict.sheetTx.date, dateDiff)
                                        ConflictValue("Amount", conflict.sheetTx.amount.toString(), amountDiff)
                                        ConflictValue("Category", cloudCategory.ifBlank { "-" }, categoryDiff)
                                        ConflictValue("Description", conflict.sheetTx.description.ifBlank { "-" }, descriptionDiff)
                                        ConflictValue("Remarks", conflict.sheetTx.remarks.ifBlank { "-" }, remarksDiff)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onKeepLocal(conflict) },
                                    enabled = canResolve,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Keep phone's")
                                }
                                OutlinedButton(
                                    onClick = { onUpdateDevice(conflict) },
                                    enabled = canResolve,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Keep Sheet's")
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onKeepBoth(conflict) },
                                    enabled = canResolve,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Keep both")
                                }
                                OutlinedButton(
                                    onClick = { onDeleteFromCloud(conflict) },
                                    enabled = canResolve,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Delete everywhere")
                                }
                            }
                        }
                    }
                }
            }

            if (resolutionState is ImportState.Loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuplicatesSheet(
    groups: List<List<ExpenseRecord>>,
    onDelete: (ExpenseRecord) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Possible duplicates", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "These look identical: same date, type, category, amount, description and account. " +
                    "Two coffees on the same day can be genuine, so nothing is deleted unless you choose. " +
                    "A deleted copy is removed from the Sheet too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (groups.isEmpty()) {
                Text("No possible duplicates found.", modifier = Modifier.padding(vertical = 24.dp))
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(groups) { _, group ->
                    val first = group.first()
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${first.date} · ${first.category.ifBlank { first.type }} · ${first.amount}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${group.size} copies" + (first.description.takeIf { it.isNotBlank() }?.let { " of \"$it\"" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            group.forEachIndexed { index, record ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Copy ${index + 1}" + (record.remarks.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { onDelete(record) }) { Text("Delete", color = ExpenseRed) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictValue(label: String, value: String, isDifferent: Boolean) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = if (isDifferent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    )
}

private fun sheetCategory(dto: ImportRecordDto): String {
    return dto.expCategory?.trim().takeUnless { it.isNullOrBlank() }
        ?: dto.incCategory?.trim().takeUnless { it.isNullOrBlank() }
        ?: ""
}

private fun approximatelyEqual(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) < 0.000001
