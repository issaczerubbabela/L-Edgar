package com.sheetsync.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sheetsync.data.local.entity.AccountRecord
import com.sheetsync.viewmodel.LogViewModel
import com.sheetsync.viewmodel.SyncStatusUi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    innerPadding: PaddingValues,
    onSaved: () -> Unit = {},
    vm: LogViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val accounts by vm.accounts.collectAsState()
    val expenseCategories by vm.expenseCategories.collectAsState()
    val incomeCategories by vm.incomeCategories.collectAsState()
    val paymentModes by vm.paymentModes.collectAsState()

    LaunchedEffect(vm.saveSuccess) {
        if (vm.saveSuccess) {
            onSaved()
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEditMode) "Edit Transaction" else "Log Transaction") },
                actions = {
                    if (vm.isEditMode) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete transaction")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                    Button(
                        onClick = vm::save,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(if (vm.isEditMode) "Update" else "Save")
                    }
                }
            }
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Log Transaction", style = MaterialTheme.typography.headlineMedium)
            SyncStatusIndicator(status = vm.syncStatus, onRetry = vm::retrySync)

            // Date picker field
            OutlinedTextField(
                value = vm.selectedDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                onValueChange = {},
                label = { Text("Date") },
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.CalendarMonth, "Pick date")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Expense / Income toggle
            Text("Type", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val types = listOf("Expense", "Income", "Transfer")
                types.forEachIndexed { index, label ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index, types.size),
                        selected = vm.selectedType == label,
                        onClick = {
                            vm.selectedType = label
                            vm.selectedCategory = if (label == "Transfer") "Transfer" else ""
                            vm.selectedFromAccountId = null
                            vm.selectedToAccountId = null
                            vm.selectedPaymentMode = if (label == "Transfer") "Transfer" else ""
                        },
                        label = { Text(label) }
                    )
                }
            }

            if (vm.selectedType == "Transfer") {
                AccountDropdownField(
                    label = "From Account",
                    options = accounts,
                    selectedId = vm.selectedFromAccountId,
                    onSelect = { vm.selectedFromAccountId = it }
                )
                AccountDropdownField(
                    label = "To Account",
                    options = accounts,
                    selectedId = vm.selectedToAccountId,
                    onSelect = { vm.selectedToAccountId = it }
                )
            } else {
                // Category dropdown
                val categories = when (vm.selectedType) {
                    "Expense" -> expenseCategories
                    "Income" -> incomeCategories
                    else -> emptyList()
                }
                DropdownField(
                    label = "Category",
                    options = categories,
                    selected = vm.selectedCategory,
                    onSelect = { vm.selectedCategory = it }
                )
                if (categories.isEmpty()) {
                    Text(
                        text = "No categories found. Add from More > Manage Categories & Dropdowns.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Description
            OutlinedTextField(
                value = vm.description,
                onValueChange = { vm.description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Amount
            OutlinedTextField(
                value = vm.amount,
                onValueChange = { vm.amount = it },
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Payment Mode
            if (vm.selectedType != "Transfer") {
                DropdownField(
                    label = "Payment Mode",
                    options = paymentModes,
                    selected = vm.selectedPaymentMode,
                    onSelect = { vm.selectedPaymentMode = it }
                )
                if (paymentModes.isEmpty()) {
                    Text(
                        text = "No payment modes found. Add from More > Manage Categories & Dropdowns.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Remarks
            OutlinedTextField(
                value = vm.remarks,
                onValueChange = { vm.remarks = it },
                label = { Text("Remarks (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(Modifier.height(8.dp))
        }
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

@Composable
private fun SyncStatusIndicator(status: SyncStatusUi, onRetry: () -> Unit) {
    val (text, containerColor, contentColor) = when (status) {
        SyncStatusUi.Idle -> Triple(
            "Sync idle",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        SyncStatusUi.Syncing -> Triple(
            "Syncing...",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        SyncStatusUi.Synced -> Triple(
            "Synced",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        SyncStatusUi.Failed -> Triple(
            "Sync failed - tap to retry",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    val isRetryEnabled = status == SyncStatusUi.Failed

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = options.isNotEmpty() && it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            enabled = options.isNotEmpty(),
            placeholder = {
                if (options.isEmpty()) {
                    Text("No options")
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdownField(
    label: String,
    options: List<AccountRecord>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }?.accountName.orEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = options.isNotEmpty() && it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            enabled = options.isNotEmpty(),
            placeholder = {
                if (options.isEmpty()) {
                    Text("No accounts")
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.accountName) },
                    onClick = {
                        onSelect(account.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
