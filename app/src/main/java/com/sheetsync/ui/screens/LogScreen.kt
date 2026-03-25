package com.sheetsync.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sheetsync.viewmodel.LogViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val expenseCategories = listOf(
    "Food & Dining", "Transport", "Shopping", "Entertainment",
    "Health", "Education", "Utilities", "Rent", "Personal Care", "Other"
)
private val incomeCategories = listOf(
    "Salary", "Freelance", "Business", "Investment", "Rental", "Gift", "Other"
)
private val paymentModes = listOf(
    "UPI", "Cash", "Credit Card", "Debit Card", "Net Banking", "Wallet", "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(innerPadding: PaddingValues, vm: LogViewModel = hiltViewModel()) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(vm.saveSuccess) {
        if (vm.saveSuccess) {
            snackbarHostState.showSnackbar("Saved ✓")
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.save() },
                icon = { Icon(Icons.Filled.Check, "Save") },
                text = { Text("Save") }
            )
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
                listOf("Expense", "Income").forEachIndexed { index, label ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        selected = vm.selectedType == label,
                        onClick = { vm.selectedType = label; vm.selectedCategory = "" },
                        label = { Text(label) }
                    )
                }
            }

            // Category dropdown
            val categories = if (vm.selectedType == "Expense") expenseCategories else incomeCategories
            DropdownField(
                label = "Category",
                options = categories,
                selected = vm.selectedCategory,
                onSelect = { vm.selectedCategory = it }
            )

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
            DropdownField(
                label = "Payment Mode",
                options = paymentModes,
                selected = vm.selectedPaymentMode,
                onSelect = { vm.selectedPaymentMode = it }
            )

            // Remarks
            OutlinedTextField(
                value = vm.remarks,
                onValueChange = { vm.remarks = it },
                label = { Text("Remarks (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(Modifier.height(72.dp)) // FAB clearance
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
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
