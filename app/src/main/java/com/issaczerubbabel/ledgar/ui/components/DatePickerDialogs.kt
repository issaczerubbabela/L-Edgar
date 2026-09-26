package com.issaczerubbabel.ledgar.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Material date-range picker in a dialog, converted in UTC for the same reason as [SingleDatePickerDialog]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    initialStart: LocalDate,
    initialEnd: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    fun LocalDate.millis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.date() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val state = androidx.compose.material3.rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart.millis(),
        initialSelectedEndDateMillis = initialEnd.millis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis?.date() ?: return@TextButton
                    onConfirm(start, state.selectedEndDateMillis?.date() ?: start)
                }
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        androidx.compose.material3.DateRangePicker(state = state, modifier = androidx.compose.ui.Modifier.weight(1f))
    }
}

/**
 * Material date picker in a dialog. The picker works in UTC-midnight milliseconds, so the
 * conversion in both directions is done in UTC; using the device time zone shifts the
 * highlighted day by one in zones ahead of UTC.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val initialMillis = remember(initialDate) {
        initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                }
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = datePickerState)
    }
}
