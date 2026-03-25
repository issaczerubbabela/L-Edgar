package com.sheetsync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sheetsync.ui.theme.ExpenseRed
import com.sheetsync.ui.theme.IncomeGreen
import com.sheetsync.viewmodel.ImportState
import com.sheetsync.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(innerPadding: PaddingValues, vm: SettingsViewModel = hiltViewModel()) {
    val importState by vm.importState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        // ── Import Historical Data card ──────────────────────────────────────
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Import Historical Data", style = MaterialTheme.typography.titleMedium)
                }

                Text(
                    "Pull all existing records from your Google Sheet into this app. " +
                    "Imported records are marked as already synced and will not be pushed back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (val state = importState) {
                    is ImportState.Loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Importing…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    is ImportState.Success -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(20.dp))
                            Text(
                                "Imported ${state.count} records successfully.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = IncomeGreen
                            )
                        }
                        TextButton(onClick = { vm.resetImportState() }) { Text("Dismiss") }
                    }
                    is ImportState.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(20.dp))
                            Text(state.message, style = MaterialTheme.typography.bodySmall, color = ExpenseRed)
                        }
                        TextButton(onClick = { vm.resetImportState() }) { Text("Dismiss") }
                    }
                    is ImportState.Idle -> {
                        Button(
                            onClick = { vm.importHistoricalData() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Import from Google Sheets")
                        }
                    }
                }
            }
        }

        // ── Future settings can go here ──────────────────────────────────────
        Text(
            "More settings coming soon…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
