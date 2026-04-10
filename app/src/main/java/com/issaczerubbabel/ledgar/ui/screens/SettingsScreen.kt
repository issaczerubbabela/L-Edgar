package com.issaczerubbabel.ledgar.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import com.issaczerubbabel.ledgar.ui.theme.AppThemeOption
import com.issaczerubbabel.ledgar.ui.theme.ExpenseRed
import com.issaczerubbabel.ledgar.ui.theme.IncomeGreen
import com.issaczerubbabel.ledgar.data.preferences.AppLockAuthMode
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
    vm: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val sheetsState by vm.sheetsImportState.collectAsStateWithLifecycle()
    val csvState by vm.csvImportState.collectAsStateWithLifecycle()
    val backupState by vm.backupState.collectAsStateWithLifecycle()
    val currentTheme by vm.themeState.collectAsStateWithLifecycle()
    val scriptUrl by vm.scriptUrl.collectAsStateWithLifecycle()
    val appLockEnabled by vm.appLockEnabled.collectAsStateWithLifecycle()
    val appLockAuthMode by vm.appLockAuthMode.collectAsStateWithLifecycle()
    val appLockTimeoutMinutes by vm.appLockTimeoutMinutes.collectAsStateWithLifecycle()
    val hasAppPinConfigured by vm.hasAppPinConfigured.collectAsStateWithLifecycle()
    var themeDropdownExpanded by remember { mutableStateOf(false) }
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
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.importFromCsv(it) }
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
