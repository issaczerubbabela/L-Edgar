package com.sheetsync.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sheetsync.viewmodel.AddEditAccountUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAccountSheet(
    state: AddEditAccountUiState,
    accountGroups: List<String>,
    onDismiss: () -> Unit,
    onGroupChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onIncludeInTotalsChange: (Boolean) -> Unit,
    onHiddenChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
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
            }
        }
    }
}
