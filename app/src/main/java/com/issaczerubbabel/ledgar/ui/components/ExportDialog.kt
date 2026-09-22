package com.issaczerubbabel.ledgar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.issaczerubbabel.ledgar.viewmodel.ExportInterval
import java.time.LocalDate

@Composable
fun ExportDialog(
    selected: ExportInterval,
    customStart: String,
    customEnd: String,
    onSelect: (ExportInterval) -> Unit,
    onStartChanged: (String) -> Unit,
    onEndChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val isCustom = selected == ExportInterval.CUSTOM
    val startDate = remember(customStart) { parseDateOrNull(customStart) }
    val endDate = remember(customEnd) { parseDateOrNull(customEnd) }
    val rangeIsValid = startDate != null && endDate != null && !endDate.isBefore(startDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.TableChart, contentDescription = null) },
        title = { Text("Export to CSV") },
        text = {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ExportInterval.entries.forEach { interval ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = selected == interval,
                                role = Role.RadioButton,
                                onClick = { onSelect(interval) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == interval, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(interval.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                if (isCustom) {
                    OutlinedTextField(
                        value = customStart,
                        onValueChange = onStartChanged,
                        label = { Text("Start date") },
                        placeholder = { Text("yyyy-MM-dd") },
                        isError = customStart.isNotBlank() && startDate == null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customEnd,
                        onValueChange = onEndChanged,
                        label = { Text("End date") },
                        placeholder = { Text("yyyy-MM-dd") },
                        isError = customEnd.isNotBlank() && endDate == null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = when {
                            startDate == null || endDate == null -> "Use the format yyyy-MM-dd, for example 2026-04-18."
                            endDate.isBefore(startDate) -> "The end date must fall on or after the start date."
                            else -> "Exporting ${startDate} to ${endDate}."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (rangeIsValid) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isCustom || rangeIsValid) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun parseDateOrNull(value: String): LocalDate? =
    runCatching { LocalDate.parse(value.trim()) }.getOrNull()
