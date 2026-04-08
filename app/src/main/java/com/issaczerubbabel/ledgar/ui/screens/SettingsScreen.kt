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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.issaczerubbabel.ledgar.ui.theme.AppThemeOption
import com.issaczerubbabel.ledgar.ui.theme.ExpenseRed
import com.issaczerubbabel.ledgar.ui.theme.IncomeGreen
import com.issaczerubbabel.ledgar.viewmodel.ImportState
import com.issaczerubbabel.ledgar.viewmodel.SettingsUiEvent
import com.issaczerubbabel.ledgar.viewmodel.SettingsViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    onNavigateToDropdownManagement: () -> Unit,
    onNavigateToAppsScriptSetup: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val sheetsState  by vm.sheetsImportState.collectAsState()
    val csvState     by vm.csvImportState.collectAsState()
    val backupState  by vm.backupState.collectAsState()
    val currentTheme by vm.themeState.collectAsState()
    val scriptUrl by vm.scriptUrl.collectAsState()
    var themeDropdownExpanded by remember { mutableStateOf(false) }
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
            Text("Settings", style = MaterialTheme.typography.headlineMedium)

            Text("Appearance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
                            .width(150.dp)
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

            Text("Database Setup", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
                        color = if (isConnected) IncomeGreen else ExpenseRed
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

            Text("Data", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SettingsListItem(
                title = "Manage Categories & Dropdowns",
                icon = Icons.Filled.Tune,
                onClick = onNavigateToDropdownManagement
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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

            Text("About", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SettingsListItem(
                title = "Share App",
                onClick = { shareAppApk(context) }
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share app",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
