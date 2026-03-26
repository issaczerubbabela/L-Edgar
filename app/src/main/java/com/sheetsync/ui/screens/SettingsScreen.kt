package com.sheetsync.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sheetsync.ui.theme.ExpenseRed
import com.sheetsync.ui.theme.IncomeGreen
import com.sheetsync.viewmodel.ImportState
import com.sheetsync.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(innerPadding: PaddingValues, vm: SettingsViewModel = hiltViewModel()) {
    val sheetsState  by vm.sheetsImportState.collectAsState()
    val csvState     by vm.csvImportState.collectAsState()
    val isDark       by vm.isDarkTheme.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm.resetDone) {
        if (vm.resetDone) { snackbarHostState.showSnackbar("All data deleted."); vm.clearResetDone() }
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

            // ── Theme toggle ─────────────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            if (isDark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text("Theme", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (isDark) "Dark (Teal)" else "Light",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(checked = isDark, onCheckedChange = { vm.toggleTheme() })
                }
            }

            // ── Duplicate control toggle ─────────────────────────────────────
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Skip Duplicates on Import", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (vm.skipDuplicates)
                                "Records matching an existing entry (date + type + category + amount) will be skipped."
                            else
                                "All records will be inserted, even if they already exist.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = vm.skipDuplicates, onCheckedChange = { vm.skipDuplicates = it })
                }
            }


            // ── Import from Sheets ───────────────────────────────────────────
            ImportCard(
                title = "Import from Google Sheets",
                description = "Pulls all existing records directly from your connected Google Sheet.",
                icon = Icons.Filled.CloudDownload,
                state = sheetsState,
                onAction = { vm.importFromSheets() },
                actionLabel = "Import from Sheets",
                onDismiss = { vm.resetSheetsState() }
            )

            // ── Import from CSV ──────────────────────────────────────────────
            ImportCard(
                title = "Import from CSV",
                description = "Pick a CSV exported from Google Sheets. Columns are auto-detected from the header row.",
                icon = Icons.Filled.FileOpen,
                state = csvState,
                onAction = { csvLauncher.launch("text/*") },
                actionLabel = "Pick CSV File",
                onDismiss = { vm.resetCsvState() }
            )

            // ── Reset all data ───────────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ExpenseRed.copy(alpha = 0.10f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.DeleteForever, null, tint = ExpenseRed)
                        Text("Reset All Data", style = MaterialTheme.typography.titleMedium, color = ExpenseRed)
                    }
                    Text(
                        "Permanently deletes all local transaction records. Google Sheets data is not affected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { vm.showResetConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseRed)
                    ) {
                        Icon(Icons.Filled.DeleteForever, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reset All Data")
                    }
                }
            }
        }
    }
}

// ── Reusable import card ─────────────────────────────────────────────────────

@Composable
private fun ImportCard(
    title: String, description: String, icon: ImageVector,
    state: ImportState, onAction: () -> Unit, actionLabel: String, onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (state) {
                is ImportState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Importing…", style = MaterialTheme.typography.bodyMedium)
                }
                is ImportState.Success -> {
                    StatusRow(Icons.Filled.CheckCircle, IncomeGreen,
                        "Imported ${state.imported} record${if (state.imported != 1) "s" else ""}." +
                        if (state.skipped > 0) " Skipped ${state.skipped} duplicate${if (state.skipped != 1) "s" else ""}." else "")
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
                is ImportState.Error -> {
                    StatusRow(Icons.Filled.ErrorOutline, ExpenseRed, state.message)
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
                is ImportState.Idle -> Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Icon(icon, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun StatusRow(icon: ImageVector, color: Color, message: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
