package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.ui.components.SingleDatePickerDialog
import com.issaczerubbabel.ledgar.ui.components.tabularNumbers
import com.issaczerubbabel.ledgar.util.formatRupees
import com.issaczerubbabel.ledgar.util.parseAmountInput
import com.issaczerubbabel.ledgar.viewmodel.StartCycleFormError
import com.issaczerubbabel.ledgar.viewmodel.StartCycleUiState
import com.issaczerubbabel.ledgar.viewmodel.StartCycleViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StartCycleScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onDone: () -> Unit,
    vm: StartCycleViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    LaunchedEffect(state.isDone) { if (state.isDone) onDone() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .imePadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(text = "Start new cycle", style = MaterialTheme.typography.titleLarge)
        }

        if (!state.isLoaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            StartCycleForm(
                state = state,
                onPickStart = { pickingStart = true },
                onPickEnd = { pickingEnd = true },
                onAmountChange = vm::setAmountInput,
                onUseSuggestion = vm::useSuggestedSalary,
                onCarryOverChange = vm::setCarryOver,
                onStart = vm::start
            )
        }
    }

    if (pickingStart) {
        SingleDatePickerDialog(
            initialDate = state.startDate,
            onDismiss = { pickingStart = false },
            onConfirm = { vm.setStartDate(it); pickingStart = false }
        )
    }
    if (pickingEnd) {
        SingleDatePickerDialog(
            initialDate = state.endDate,
            onDismiss = { pickingEnd = false },
            onConfirm = { vm.setEndDate(it); pickingEnd = false }
        )
    }
}

@Composable
private fun StartCycleForm(
    state: StartCycleUiState,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onAmountChange: (String) -> Unit,
    onUseSuggestion: () -> Unit,
    onCarryOverChange: (Boolean) -> Unit,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DateField(
            label = "Paid on",
            date = state.startDate,
            supporting = if (state.error == StartCycleFormError.START_NOT_AFTER_CURRENT) {
                "Your current cycle began on ${state.runningCycleStart?.let { FIELD_DATE.format(it) } ?: "an earlier date"}. " +
                    "Choose a later payday date."
            } else {
                "Change it if the money landed on a different day."
            },
            isError = state.error == StartCycleFormError.START_NOT_AFTER_CURRENT,
            onClick = onPickStart
        )
        DateField(
            label = "Runs until",
            date = state.endDate,
            supporting = if (state.error == StartCycleFormError.END_BEFORE_START) {
                "The end date must be on or after the start date."
            } else {
                "${state.cycleDays} days. You set this now so the pace has an end point."
            },
            isError = state.error == StartCycleFormError.END_BEFORE_START,
            onClick = onPickEnd
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.amountInput,
                onValueChange = onAmountChange,
                label = { Text("Spendable this cycle") },
                prefix = { Text("₹") },
                singleLine = true,
                isError = state.error == StartCycleFormError.AMOUNT,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = {
                    Text(
                        when {
                            state.error == StartCycleFormError.AMOUNT -> "Enter the amount you have to spend, for example 68000."
                            state.amountWasPrefilled -> "Prefilled from your last cycle. Nothing is saved until you start."
                            else -> "What you can spend before your next payday."
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            val suggestion = state.suggestedSalary
            if (suggestion != null && parseAmountInput(state.amountInput) != suggestion) {
                SuggestionChip(
                    onClick = onUseSuggestion,
                    label = { Text("Salary of ${formatRupees(suggestion)} detected. Use it") }
                )
            }
        }

        CarryOverRow(
            enabled = state.carryableBucketCount > 0,
            checked = state.carryOver && state.carryableBucketCount > 0,
            bucketCount = state.carryableBucketCount,
            onCheckedChange = onCarryOverChange
        )

        state.runningCycleStart?.let { started ->
            Text(
                text = "Starting this closes your current cycle, which began on ${FIELD_DATE.format(started)}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = onStart,
            enabled = !state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Start cycle")
            }
        }
    }
}

/** A read-only field that opens a date picker. The whole field is the tap target. */
@Composable
private fun DateField(
    label: String,
    date: LocalDate,
    supporting: String,
    isError: Boolean,
    onClick: () -> Unit
) {
    Box {
        OutlinedTextField(
            value = FIELD_DATE.format(date),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
            isError = isError,
            supportingText = { Text(supporting) },
            textStyle = tabularNumbers(MaterialTheme.typography.bodyLarge),
            modifier = Modifier.fillMaxWidth()
        )
        // Text fields swallow taps, so an overlay carries the click for the whole field.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClickLabel = "Change $label", role = Role.Button, onClick = onClick)
        )
    }
}

@Composable
private fun CarryOverRow(enabled: Boolean, checked: Boolean, bucketCount: Int, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = "Carry over my buckets", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (enabled) {
                    "$bucketCount ${if (bucketCount == 1) "bucket" else "buckets"} and their categories. " +
                        "Spending starts again from zero."
                } else {
                    "You have no buckets to carry over yet."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private val FIELD_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
