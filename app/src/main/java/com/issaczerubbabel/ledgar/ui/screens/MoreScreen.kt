package com.issaczerubbabel.ledgar.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.sync.SyncStatus
import com.issaczerubbabel.ledgar.ui.components.ExportDialog
import com.issaczerubbabel.ledgar.ui.navigation.MoreFeature
import com.issaczerubbabel.ledgar.ui.navigation.MoreFeatures
import com.issaczerubbabel.ledgar.ui.theme.ExpenseRed
import com.issaczerubbabel.ledgar.ui.theme.IncomeGreen
import com.issaczerubbabel.ledgar.viewmodel.ExportViewModel
import com.issaczerubbabel.ledgar.viewmodel.SettingsUiEvent
import com.issaczerubbabel.ledgar.viewmodel.SettingsViewModel

/**
 * The More tab: what the app can do. Sync status first, then the features, then data tools.
 * App configuration lives on [SettingsScreen], opened from the gear.
 */
@Composable
fun MoreScreen(
    innerPadding: PaddingValues,
    onNavigateToSettings: () -> Unit,
    onNavigateToFeature: (route: String) -> Unit,
    onNavigateToDropdownManagement: () -> Unit,
    onNavigateToAppsScriptSetup: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
    exportVm: ExportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val exportState by exportVm.uiState.collectAsStateWithLifecycle()
    val csvState by vm.csvImportState.collectAsStateWithLifecycle()
    val syncConflicts by vm.syncConflicts.collectAsStateWithLifecycle()
    val conflictResolutionState by vm.conflictResolutionState.collectAsStateWithLifecycle()
    val showConflictSheet by vm.showConflictSheet.collectAsStateWithLifecycle()
    val heldSheetDeletions by vm.heldSheetDeletions.collectAsStateWithLifecycle()
    val possibleDuplicates by vm.possibleDuplicates.collectAsStateWithLifecycle()
    val showDuplicatesSheet by vm.showDuplicatesSheet.collectAsStateWithLifecycle()
    val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
    val scriptUrl by vm.scriptUrl.collectAsStateWithLifecycle()
    var heldDeletionsDismissed by remember { mutableStateOf(false) }
    var showAllFeatures by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

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
                Text(
                    text = "More",
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }

            SyncStatusCard(
                syncStatus = syncStatus,
                isConnected = !scriptUrl.isNullOrBlank(),
                conflictCount = syncConflicts.size,
                onSyncNow = vm::syncNow,
                onReviewConflicts = vm::openConflictSheet,
                onOpenSetup = onNavigateToAppsScriptSetup
            )

            SettingsSectionHeader("Features")

            MoreFeatures.tiles.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { feature ->
                        FeatureTile(
                            feature = feature,
                            onClick = { onNavigateToFeature(feature.route) },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            if (MoreFeatures.overflow.isNotEmpty()) {
                SettingsListItem(
                    title = "All features",
                    icon = Icons.Filled.Apps,
                    onClick = { showAllFeatures = !showAllFeatures }
                ) {
                    Icon(
                        imageVector = if (showAllFeatures) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (showAllFeatures) "Hide more features" else "Show more features",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showAllFeatures) {
                    MoreFeatures.overflow.forEach { feature ->
                        SettingsListItem(
                            title = feature.title,
                            icon = feature.icon,
                            onClick = { onNavigateToFeature(feature.route) }
                        ) { NavChevron() }
                    }
                }
            }

            SettingsSectionHeader("Manage data")

            SettingsListItem(
                title = "Categories & dropdowns",
                icon = Icons.Filled.Tune,
                onClick = onNavigateToDropdownManagement
            ) { NavChevron() }

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

            SettingsListItem(title = "Import from CSV", icon = Icons.Filled.FileOpen) {
                ImportActionControl(
                    state = csvState,
                    idleIcon = Icons.Filled.PlayArrow,
                    onRun = { csvLauncher.launch("text/*") },
                    onDismiss = vm::resetCsvState
                )
            }

            SettingsListItem(
                title = "Export to CSV",
                icon = Icons.Filled.TableChart,
                onClick = exportVm::openDialog
            ) { NavChevron() }
        }
    }
}

@Composable
private fun SyncStatusCard(
    syncStatus: SyncStatus,
    isConnected: Boolean,
    conflictCount: Int,
    onSyncNow: () -> Unit,
    onReviewConflicts: () -> Unit,
    onOpenSetup: () -> Unit
) {
    val problem = syncStatus == SyncStatus.Failed || syncStatus == SyncStatus.NeedsScriptUpdate
    val headline: String
    val detail: String
    val icon: ImageVector
    val tint: Color
    when {
        !isConnected -> {
            headline = "Not connected to a Sheet"
            detail = "Your data is saved on this phone."
            icon = Icons.Filled.CloudOff
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        }
        syncStatus == SyncStatus.Syncing -> {
            headline = "Syncing"
            detail = "Sending your latest changes."
            icon = Icons.Filled.Sync
            tint = MaterialTheme.colorScheme.primary
        }
        syncStatus == SyncStatus.Synced -> {
            headline = "All synced"
            detail = "Your Sheet is up to date."
            icon = Icons.Filled.CheckCircle
            tint = IncomeGreen
        }
        syncStatus == SyncStatus.Failed -> {
            headline = "Sync failed"
            detail = "It will retry on its own. You can also sync now."
            icon = Icons.Filled.ErrorOutline
            tint = ExpenseRed
        }
        syncStatus == SyncStatus.NeedsScriptUpdate -> {
            headline = "Sync paused"
            detail = "Your Apps Script is out of date."
            icon = Icons.Filled.ErrorOutline
            tint = ExpenseRed
        }
        else -> {
            headline = "Sync is on"
            detail = "Changes go to your Sheet in the background."
            icon = Icons.Filled.CloudSync
            tint = MaterialTheme.colorScheme.primary
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(icon, contentDescription = null, tint = tint)
                Column(modifier = Modifier.weight(1f)) {
                    Text(headline, style = MaterialTheme.typography.titleMedium)
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!isConnected || syncStatus == SyncStatus.NeedsScriptUpdate) {
                    TextButton(onClick = onOpenSetup) { Text(if (isConnected) "Fix" else "Set up") }
                } else {
                    TextButton(onClick = onSyncNow, enabled = syncStatus != SyncStatus.Syncing) { Text("Sync now") }
                }
            }
            if (conflictCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onReviewConflicts)
                ) {
                    Row(
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            text = if (conflictCount == 1) "1 sync conflict needs a decision" else "$conflictCount sync conflicts need a decision",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "Review",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureTile(feature: MoreFeature, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .defaultMinSize(minHeight = 96.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(feature.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(feature.title, style = MaterialTheme.typography.titleMedium)
            Text(
                feature.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
