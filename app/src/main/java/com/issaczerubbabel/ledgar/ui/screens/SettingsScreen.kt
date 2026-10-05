package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material.icons.automirrored.filled.ShowChart
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
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
import com.issaczerubbabel.ledgar.data.preferences.ChartPalette
import com.issaczerubbabel.ledgar.ui.theme.swatches
import com.issaczerubbabel.ledgar.data.remote.ImportRecordDto
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.SyncConflict
import com.issaczerubbabel.ledgar.sync.SyncStatus
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    onBack: () -> Unit,
    onNavigateToAppsScriptSetup: () -> Unit,
    onNavigateToChangelog: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val sheetsState by vm.sheetsImportState.collectAsStateWithLifecycle()
    val backupState by vm.backupState.collectAsStateWithLifecycle()
    val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
    val currentTheme by vm.themeState.collectAsStateWithLifecycle()
    val scriptUrl by vm.scriptUrl.collectAsStateWithLifecycle()
    val appLockEnabled by vm.appLockEnabled.collectAsStateWithLifecycle()
    val appLockAuthMode by vm.appLockAuthMode.collectAsStateWithLifecycle()
    val appLockTimeoutMinutes by vm.appLockTimeoutMinutes.collectAsStateWithLifecycle()
    val hasAppPinConfigured by vm.hasAppPinConfigured.collectAsStateWithLifecycle()
    val chartPalette by vm.chartPalette.collectAsStateWithLifecycle()
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

    // Erase confirmation: typing the word keeps a stray tap from wiping the phone.
    if (vm.showResetConfirm) {
        var typedWord by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.showResetConfirm = false },
            icon = { Icon(Icons.Filled.DeleteForever, null, tint = ExpenseRed) },
            title = { Text("Erase all local data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This permanently deletes every local transaction record on this phone. This cannot be undone.")
                    OutlinedTextField(
                        value = typedWord,
                        onValueChange = { typedWord = it },
                        label = { Text("Type ERASE to confirm") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.resetAllData() },
                    enabled = typedWord.trim() == "ERASE",
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) { Text("Erase everything") }
            },
            dismissButton = { TextButton(onClick = { vm.showResetConfirm = false }) { Text("Cancel") } }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = responsiveTextSize(baseSp = 28f, minSp = 24f, maxSp = 30f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
            }


            SettingsSectionHeader("Appearance")

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
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .widthIn(min = 132.dp, max = 188.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = themeDropdownExpanded,
                        onDismissRequest = { themeDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("System default") },
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

            SettingsListItem(title = "Chart colours", icon = Icons.AutoMirrored.Filled.ShowChart) {
                ExposedDropdownMenuBox(
                    expanded = chartStyleDropdownExpanded,
                    onExpandedChange = { chartStyleDropdownExpanded = !chartStyleDropdownExpanded }
                ) {
                    TextField(
                        value = chartPalette.label,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        leadingIcon = { PaletteSwatches(chartPalette) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = chartStyleDropdownExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .widthIn(min = 132.dp, max = 200.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = chartStyleDropdownExpanded,
                        onDismissRequest = { chartStyleDropdownExpanded = false }
                    ) {
                        ChartPalette.entries.forEach { palette ->
                            DropdownMenuItem(
                                leadingIcon = { PaletteSwatches(palette) },
                                text = {
                                    Column {
                                        Text(palette.label)
                                        if (!palette.colourBlindSafe) {
                                            Text(
                                                text = "Not colour-blind safe",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    vm.updateChartPalette(palette)
                                    chartStyleDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingsSectionHeader("Security")

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
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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

            SettingsSectionHeader("Sync & backup")

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

            SettingsListItem(title = "Back up to Google Sheets", icon = Icons.Filled.CloudUpload) {
                ImportActionControl(
                    state = backupState,
                    idleIcon = Icons.Filled.PlayArrow,
                    onRun = vm::backupToGoogleSheets,
                    onDismiss = vm::resetBackupState
                )
            }

            SettingsListItem(title = "Restore from Google Sheets", icon = Icons.Filled.CloudDownload) {
                ImportActionControl(
                    state = sheetsState,
                    idleIcon = Icons.Filled.PlayArrow,
                    onRun = vm::importFromSheets,
                    onDismiss = vm::resetSheetsState
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingsSectionHeader("About")

            SettingsListItem(
                title = "What's new",
                icon = Icons.Filled.Info,
                onClick = onNavigateToChangelog
            ) { NavChevron() }

            SettingsListItem(
                title = "Share app",
                icon = Icons.Filled.Share,
                onClick = { shareAppApk(context) }
            ) { NavChevron() }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingsSectionHeader("Danger zone", danger = true)

            SettingsListItem(
                title = "Erase all local data",
                icon = Icons.Filled.DeleteForever,
                iconTint = ExpenseRed,
                onClick = { vm.showResetConfirm = true }
            ) { NavChevron() }
        }
    }
}

@Composable
internal fun SettingsSectionHeader(text: String, danger: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = if (danger) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false
    )
}

@Composable
internal fun NavChevron() {
    Icon(
        imageVector = Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp)
    )
}

@Composable
internal fun SettingsListItem(
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
internal fun ImportActionControl(
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
    AppThemeOption.SYSTEM -> "System default"
    AppThemeOption.LAVENDER -> "Lavender"
    AppThemeOption.TEAL -> "Teal"
    AppThemeOption.RED -> "Red"
}

/** The palette's money in, money out and saved colours, side by side. */
@Composable
private fun PaletteSwatches(palette: ChartPalette) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        palette.swatches().forEach { color ->
            Box(Modifier.size(10.dp).background(color, CircleShape))
        }
    }
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
internal fun SyncResolutionSheet(
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
internal fun DuplicatesSheet(
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
