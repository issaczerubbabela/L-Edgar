package com.sheetsync.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sheetsync.ui.theme.AppThemeOption
import com.sheetsync.ui.theme.ExpenseRed
import com.sheetsync.ui.theme.IncomeGreen
import com.sheetsync.viewmodel.ImportState
import com.sheetsync.viewmodel.SettingsUiEvent
import com.sheetsync.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    onNavigateToDropdownManagement: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val sheetsState  by vm.sheetsImportState.collectAsState()
    val csvState     by vm.csvImportState.collectAsState()
    val currentTheme by vm.themeState.collectAsState()
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

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { scaffoldPadding ->
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

            SettingsRow(title = "Theme", icon = Icons.Filled.Palette) {
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
                            .widthIn(min = 118.dp, max = 132.dp)
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

            SettingsRow(
                title = "Manage Categories & Dropdowns",
                icon = Icons.Filled.Tune,
                modifier = Modifier.clickable(onClick = onNavigateToDropdownManagement)
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsRow(title = "Import from Google Sheets", icon = Icons.Filled.CloudDownload) {
                ImportActionControl(
                    state = sheetsState,
                    idleIcon = Icons.Filled.CloudDownload,
                    onRun = vm::importFromSheets,
                    onDismiss = vm::resetSheetsState
                )
            }

            SettingsRow(title = "Import from CSV", icon = Icons.Filled.FileOpen) {
                ImportActionControl(
                    state = csvState,
                    idleIcon = Icons.Filled.FileOpen,
                    onRun = { csvLauncher.launch("text/*") },
                    onDismiss = vm::resetCsvState
                )
            }

            SettingsRow(
                title = "Reset All Data",
                icon = Icons.Filled.DeleteForever,
                iconTint = ExpenseRed,
                containerColor = ExpenseRed.copy(alpha = 0.10f)
            ) {
                IconButton(onClick = { vm.showResetConfirm = true }) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = "Reset data", tint = ExpenseRed)
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    trailing: @Composable RowScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            trailing()
        }
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
        is ImportState.Loading -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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
