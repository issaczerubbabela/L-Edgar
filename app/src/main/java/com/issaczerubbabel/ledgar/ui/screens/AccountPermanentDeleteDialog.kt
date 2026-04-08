package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ReassignAccountOption(
    val id: Long,
    val label: String
)

private enum class PermanentDeleteChoice {
    RemoveLinkedTransactions,
    ReassignLinkedTransactions
}

@Composable
fun AccountPermanentDeleteDialog(
    accountName: String,
    reassignOptions: List<ReassignAccountOption>,
    onDismiss: () -> Unit,
    onConfirm: (reassignToAccountId: Long?) -> Unit
) {
    var choice by remember { mutableStateOf(PermanentDeleteChoice.RemoveLinkedTransactions) }
    var selectedReassignId by remember(reassignOptions) {
        mutableStateOf(reassignOptions.firstOrNull()?.id)
    }

    val requiresReassign = choice == PermanentDeleteChoice.ReassignLinkedTransactions
    val canConfirm = !requiresReassign || selectedReassignId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Permanently") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Delete account \"$accountName\" permanently.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { choice = PermanentDeleteChoice.RemoveLinkedTransactions }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = choice == PermanentDeleteChoice.RemoveLinkedTransactions,
                        onClick = { choice = PermanentDeleteChoice.RemoveLinkedTransactions }
                    )
                    Text("Also remove all linked transactions")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { choice = PermanentDeleteChoice.ReassignLinkedTransactions }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = choice == PermanentDeleteChoice.ReassignLinkedTransactions,
                        onClick = { choice = PermanentDeleteChoice.ReassignLinkedTransactions }
                    )
                    Text("Reassign linked transactions to another account")
                }

                if (requiresReassign) {
                    if (reassignOptions.isEmpty()) {
                        Text(
                            text = "No other accounts available for reassignment.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        reassignOptions.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedReassignId = option.id }
                                    .padding(start = 24.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedReassignId == option.id,
                                    onClick = { selectedReassignId = option.id }
                                )
                                Text(option.label)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val reassignTarget = if (requiresReassign) selectedReassignId else null
                    onConfirm(reassignTarget)
                },
                enabled = canConfirm
            ) {
                Text("Delete Permanently", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
