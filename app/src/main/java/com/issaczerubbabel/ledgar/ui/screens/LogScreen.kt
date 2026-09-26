package com.issaczerubbabel.ledgar.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.ui.components.AmountDisplay
import com.issaczerubbabel.ledgar.ui.components.BucketPreviewSlot
import com.issaczerubbabel.ledgar.ui.components.ChipRow
import com.issaczerubbabel.ledgar.ui.components.ChipSpec
import com.issaczerubbabel.ledgar.ui.components.NumericKeypad
import com.issaczerubbabel.ledgar.ui.components.OptionPickerSheet
import com.issaczerubbabel.ledgar.ui.components.PickerOption
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.util.applyKeypadAction
import com.issaczerubbabel.ledgar.viewmodel.AccountTarget
import com.issaczerubbabel.ledgar.viewmodel.LogViewModel
import com.issaczerubbabel.ledgar.sync.SyncStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private sealed interface LogSheet {
    data object Category : LogSheet
    data class Account(val target: AccountTarget) : LogSheet
    data object Note : LogSheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    showEnterAppButton: Boolean = false,
    onEnterApp: () -> Unit = {},
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {},
    vm: LogViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<LogSheet?>(null) }
    var savePulseSignal by remember { mutableIntStateOf(0) }
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val expenseCategories by vm.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()
    val bucketContext by vm.bucketContext.collectAsStateWithLifecycle()

    BackHandler(
        enabled = !showDatePicker && !showDeleteConfirm && activeSheet == null,
        onBack = onBack
    )

    LaunchedEffect(Unit) {
        vm.startSyncStatusObserver()
    }

    LaunchedEffect(vm.saveSuccess) {
        if (vm.saveSuccess) {
            savePulseSignal++
            if (vm.isEditMode) {
                onSaved()
            } else {
                snackbarHostState.showSnackbar("Transaction saved")
            }
            vm.resetSaveSuccess()
        }
    }
    LaunchedEffect(vm.errorMessage) {
        vm.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            LogTopBar(
                isEditMode = vm.isEditMode,
                showEnterAppButton = showEnterAppButton,
                syncStatus = vm.syncStatus,
                onRetrySync = vm::retrySync,
                onBack = onBack,
                onEnterApp = onEnterApp,
                onDeleteClick = { showDeleteConfirm = true }
            )

            val transferContext = if (vm.selectedType == TransactionType.TRANSFER) {
                val from = accounts.nameOf(vm.selectedFromAccountId)
                val to = accounts.nameOf(vm.selectedToAccountId)
                if (from != null || to != null) "${from ?: "?"} → ${to ?: "?"}" else null
            } else {
                null
            }

            DatePillRow(
                date = vm.selectedDate,
                contextText = transferContext,
                onClick = { showDatePicker = true }
            )

            BucketPreviewSlot(preview = vm.bucketPreview(bucketContext))

            TypeRow(
                selectedType = vm.selectedType,
                onSelect = { type ->
                    vm.selectedType = type
                    vm.selectedCategory = if (type == TransactionType.TRANSFER) TransactionType.TRANSFER else ""
                    vm.selectedAccountId = null
                    vm.selectedFromAccountId = null
                    vm.selectedToAccountId = null
                }
            )

            AmountDisplay(
                amount = vm.amount,
                type = vm.selectedType,
                pulseSignal = savePulseSignal,
                modifier = Modifier.weight(1f)
            )

            val isTransfer = vm.selectedType == TransactionType.TRANSFER
            val accountName = accounts.nameOf(vm.selectedAccountId)
            val fromName = accounts.nameOf(vm.selectedFromAccountId)
            val toName = accounts.nameOf(vm.selectedToAccountId)
            val hasNote = vm.description.isNotBlank() || vm.remarks.isNotBlank()

            ChipRow(
                primaryChips = if (isTransfer) {
                    listOf(
                        ChipSpec(fromName ?: "From account", fromName != null) {
                            activeSheet = LogSheet.Account(AccountTarget.From)
                        },
                        ChipSpec(toName ?: "To account", toName != null) {
                            activeSheet = LogSheet.Account(AccountTarget.To)
                        }
                    )
                } else {
                    listOf(
                        ChipSpec(vm.selectedCategory.ifBlank { "Category" }, vm.selectedCategory.isNotBlank()) {
                            activeSheet = LogSheet.Category
                        },
                        ChipSpec(accountName ?: "Account", accountName != null) {
                            activeSheet = LogSheet.Account(AccountTarget.Account)
                        }
                    )
                },
                swapKey = isTransfer,
                trailingChip = ChipSpec(if (hasNote) "Note added" else "+ Note", hasNote) {
                    activeSheet = LogSheet.Note
                }
            )

            NumericKeypad(
                onAction = { action -> vm.amount = applyKeypadAction(vm.amount, action) },
                onCommit = vm::save,
                commitEnabled = vm.amount.isNotBlank()
            )
        }
    }

    when (val sheet = activeSheet) {
        LogSheet.Category -> {
            val categories = if (vm.selectedType == TransactionType.INCOME) incomeCategories else expenseCategories
            OptionPickerSheet(
                title = "Category",
                options = categories.map { PickerOption(it) },
                selectedKey = vm.selectedCategory,
                onSelect = {
                    vm.selectedCategory = it
                    activeSheet = null
                },
                onCreate = {
                    vm.addCategoryInline(it)
                    activeSheet = null
                },
                onDismiss = { activeSheet = null },
                manageHint = "Reorder or delete in More → Dropdowns"
            )
        }

        is LogSheet.Account -> {
            val (title, selectedId) = when (sheet.target) {
                AccountTarget.Account -> "Account" to vm.selectedAccountId
                AccountTarget.From -> "From Account" to vm.selectedFromAccountId
                AccountTarget.To -> "To Account" to vm.selectedToAccountId
            }
            OptionPickerSheet(
                title = title,
                options = accounts.map { PickerOption(key = it.id.toString(), label = it.accountName) },
                selectedKey = selectedId?.toString(),
                onSelect = { key ->
                    key.toLongOrNull()?.let { vm.setAccount(sheet.target, it) }
                    activeSheet = null
                },
                onDismiss = { activeSheet = null },
                manageHint = "Add accounts from the Accounts tab"
            )
        }

        LogSheet.Note -> NoteSheet(
            description = vm.description,
            remarks = vm.remarks,
            onDescriptionChange = { vm.description = it },
            onRemarksChange = { vm.remarks = it },
            onDismiss = { activeSheet = null }
        )

        null -> Unit
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transaction?") },
            text = { Text("This transaction will be removed locally now and deleted from Google Sheets on next sync.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.deleteCurrent()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = vm.selectedDate
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        vm.selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}

private fun List<AccountRecord>.nameOf(id: Long?): String? = firstOrNull { it.id == id }?.accountName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogTopBar(
    isEditMode: Boolean,
    showEnterAppButton: Boolean,
    syncStatus: SyncStatus,
    onRetrySync: () -> Unit,
    onBack: () -> Unit,
    onEnterApp: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Spacer(Modifier.weight(1f))
        SyncStatusIndicator(status = syncStatus, onRetry = onRetrySync)
        if (isEditMode) {
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete transaction")
            }
        }
        if (showEnterAppButton) {
            Spacer(Modifier.width(8.dp))
            Button(onClick = onEnterApp) {
                Text("Enter App")
            }
        }
    }
}

@Composable
private fun DatePillRow(date: LocalDate, contextText: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = "Change date",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (contextText != null) {
                Text(
                    text = contextText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeRow(selectedType: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val types = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)
        types.forEachIndexed { index, label ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index, types.size),
                selected = selectedType == label,
                onClick = { onSelect(label) },
                label = { Text(label) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSheet(
    description: String,
    remarks: String,
    onDescriptionChange: (String) -> Unit,
    onRemarksChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Note", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text("Description") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = remarks,
                onValueChange = onRemarksChange,
                label = { Text("Remarks (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun SyncStatusIndicator(status: SyncStatus, onRetry: () -> Unit) {
    val (text, containerColor, contentColor) = when (status) {
        SyncStatus.Idle -> Triple(
            "Sync idle",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        SyncStatus.Syncing -> Triple(
            "Syncing...",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        SyncStatus.Synced -> Triple(
            "Synced",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        SyncStatus.Failed -> Triple(
            "Retry sync",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        SyncStatus.NeedsScriptUpdate -> Triple(
            "Update script",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    val isRetryEnabled = status == SyncStatus.Failed || status == SyncStatus.NeedsScriptUpdate

    SuggestionChip(
        onClick = onRetry,
        enabled = isRetryEnabled,
        label = { Text(text) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = containerColor,
            disabledContainerColor = containerColor,
            labelColor = contentColor,
            disabledLabelColor = contentColor
        )
    )
}
