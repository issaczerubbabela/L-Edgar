package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.issaczerubbabel.ledgar.util.formatAsOfDateTime
import com.issaczerubbabel.ledgar.util.nowAsOfDateTime
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import com.issaczerubbabel.ledgar.viewmodel.AddEditAccountUiState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAccountSheet(
    state: AddEditAccountUiState,
    accountGroups: List<String>,
    onDismiss: () -> Unit,
    onGroupChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onInitialBalanceDateChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onIncludeInTotalsChange: (Boolean) -> Unit,
    onHiddenChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (state.isEditMode) "Edit Account" else "Add Account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            DropdownField(
                label = "Group",
                options = accountGroups,
                selected = state.selectedGroup,
                onSelect = onGroupChange
            )

            OutlinedTextField(
                value = state.accountName,
                onValueChange = onNameChange,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.amountInput,
                onValueChange = onAmountChange,
                label = { Text("Initial Balance") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.initialBalanceDate,
                onValueChange = {},
                label = { Text("Balance As-Of Date") },
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "Select date")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
            )

            TextButton(onClick = { onInitialBalanceDateChange(nowAsOfDateTime()) }) {
                Text("Set to Now")
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = { Text("Description (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Include in Totals", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = state.includeInTotals,
                    onCheckedChange = onIncludeInTotalsChange
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Show/Hide Account", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = state.isHidden,
                    onCheckedChange = onHiddenChange
                )
            }

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }

            if (state.isEditMode) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Account", color = MaterialTheme.colorScheme.error)
                }

                TextButton(
                    onClick = onDeletePermanently,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Permanently", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (showDatePicker) {
            val initialMillis = remember(state.initialBalanceDate) {
                runCatching {
                    (parseFlexibleDate(state.initialBalanceDate) ?: LocalDate.now())
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                }.getOrDefault(System.currentTimeMillis())
            }
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val selectedDateTime = Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                                .atStartOfDay()
                            onInitialBalanceDateChange(formatAsOfDateTime(selectedDateTime))
                        }
                        showDatePicker = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            onInitialBalanceDateChange(formatAsOfDateTime(LocalDateTime.now().toLocalDate().atStartOfDay()))
                            showDatePicker = false
                        }) {
                            Text("Today")
                        }
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}
