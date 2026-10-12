package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.ui.theme.ExpenseOrange
import com.issaczerubbabel.ledgar.ui.theme.IncomeBlue
import com.issaczerubbabel.ledgar.viewmodel.ReconcileViewModel

/**
 * Reconcile: type what the bank shows; a difference becomes a Balance adjustment dated today
 * (ADR-0009). "Start fresh from today" re-anchors the Initial balance instead, after a confirm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconcileSheet(vm: ReconcileViewModel, onDismiss: () -> Unit) {
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val input by vm.input.collectAsStateWithLifecycle()
    var confirmStartFresh by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.done.collect { onDismiss() } }

    ModalBottomSheet(
        onDismissRequest = {
            vm.clear()
            onDismiss()
        }
    ) {
        val state = sheet ?: return@ModalBottomSheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Reconcile ${state.accountName}", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${state.lastChecked} · type what your bank app shows right now",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("L.Edgar says", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.appFigure, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = vm::setInput,
                    label = { Text(state.bankLabel) },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
                    .padding(14.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val difference = state.difference
                if (difference != null && !state.matches) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Difference", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            text = difference,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (state.isDifferenceNegative) ExpenseOrange else IncomeBlue
                        )
                    }
                }
                Text(state.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(
                onClick = vm::reconcile,
                enabled = state.bankBalance != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Text(if (state.matches) "Mark as checked" else "Add adjustment")
            }
            TextButton(
                onClick = { confirmStartFresh = true },
                enabled = state.bankBalance != null,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Start fresh from today instead")
            }
        }

        if (confirmStartFresh) {
            AlertDialog(
                onDismissRequest = { confirmStartFresh = false },
                title = { Text("Start fresh from today?") },
                text = {
                    Text(
                        "${state.accountName}'s balance becomes what you typed, as of the end of today. " +
                            "Transactions dated today or earlier stop counting towards it. Use this after a long break; " +
                            "to fix a small gap, add an adjustment instead."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmStartFresh = false
                        vm.startFresh()
                    }) { Text("Start fresh") }
                },
                dismissButton = { TextButton(onClick = { confirmStartFresh = false }) { Text("Cancel") } }
            )
        }
    }
}
