package com.issaczerubbabel.ledgar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import com.issaczerubbabel.ledgar.util.pickerMillisToLocalDate
import com.issaczerubbabel.ledgar.util.toPickerMillis
import java.time.LocalDate

/** Material date-range picker in a dialog, converted in UTC for the same reason as [SingleDatePickerDialog]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    initialStart: LocalDate,
    initialEnd: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    val state = androidx.compose.material3.rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart.toPickerMillis(),
        initialSelectedEndDateMillis = initialEnd.toPickerMillis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis?.pickerMillisToLocalDate() ?: return@TextButton
                    onConfirm(start, state.selectedEndDateMillis?.pickerMillisToLocalDate() ?: start)
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
 *
 * This is the only place `rememberDatePickerState` should be used: every single-date picker in
 * the app goes through here so the UTC conversion can't regress in just one of them again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    confirmText: String = "OK",
    dismissActions: (@Composable () -> Unit)? = null
) {
    val initialMillis = remember(initialDate) { initialDate.toPickerMillis() }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis -> onConfirm(millis.pickerMillisToLocalDate()) }
                }
            ) { Text(confirmText) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                dismissActions?.invoke()
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
