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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.issaczerubbabel.ledgar.viewmodel.ExportInterval
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ExportDialog(
    selected: ExportInterval,
    anchorMonth: YearMonth,
    canPickLaterMonth: Boolean,
    customStart: String,
    customEnd: String,
    onSelect: (ExportInterval) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isCustom) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPreviousMonth) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
                        }
                        Text(
                            text = anchorMonth.format(MONTH_YEAR),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onNextMonth, enabled = canPickLaterMonth) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
                        }
                    }
                }

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
                            Column {
                                Text(
                                    text = interval.rangeLabel(anchorMonth),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                interval.caption()?.let { caption ->
                                    Text(
                                        text = caption,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
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
                            startDate == null || endDate == null ->
                                "Use the format yyyy-MM-dd, for example 2026-04-18."
                            endDate.isBefore(startDate) ->
                                "The end date must fall on or after the start date."
                            else -> "Exporting $startDate to $endDate."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        // Only alarm-coloured once the user has typed something wrong; on a
                        // pristine form this is just a format hint.
                        color = if (rangeIsValid || (customStart.isBlank() && customEnd.isBlank())) {
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

private val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val MONTH_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
private val MONTH_ONLY: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

private fun ExportInterval.rangeLabel(anchor: YearMonth): String = when (this) {
    ExportInterval.CURRENT_MONTH -> anchor.format(MONTH_SHORT)
    ExportInterval.LAST_3_MONTHS -> {
        val start = anchor.minusMonths(2)
        val startText = if (start.year == anchor.year) start.format(MONTH_ONLY) else start.format(MONTH_SHORT)
        "$startText – ${anchor.format(MONTH_SHORT)}"
    }
    ExportInterval.CURRENT_YEAR -> anchor.year.toString()
    ExportInterval.LAST_YEAR -> (anchor.year - 1).toString()
    ExportInterval.CUSTOM -> "Custom date range"
}

private fun ExportInterval.caption(): String? = when (this) {
    ExportInterval.CURRENT_MONTH -> "Selected month"
    ExportInterval.LAST_3_MONTHS -> "Three months ending with the selected month"
    ExportInterval.CURRENT_YEAR -> "Whole year"
    ExportInterval.LAST_YEAR -> "Year before"
    ExportInterval.CUSTOM -> null
}

private fun parseDateOrNull(value: String): LocalDate? =
    runCatching { LocalDate.parse(value.trim()) }.getOrNull()
