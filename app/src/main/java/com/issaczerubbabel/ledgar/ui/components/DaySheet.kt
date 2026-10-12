package com.issaczerubbabel.ledgar.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.ledger.LedgerDay
import com.issaczerubbabel.ledgar.ledger.TransactionDetails
import com.issaczerubbabel.ledgar.ui.theme.ExpenseOrange
import com.issaczerubbabel.ledgar.ui.theme.IncomeBlue
import kotlinx.coroutines.launch

/**
 * The Calendar's day sheet: "Fri, 9 Oct", the day's totals and Transactions, and "Add on 9 Oct".
 * Tapping a Transaction shows its details in this same sheet, with a way back to the day, so sheets
 * never pile up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(
    day: LedgerDay,
    detailsOf: (ExpenseRecord) -> TransactionDetails,
    actionsFor: (ExpenseRecord) -> TransactionSheetActions,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Slide the sheet away, then close it and act.
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }
    val open = day.rows.firstOrNull { it.id == openId }
    // A Transaction that leaves the day (deleted by Sync, moved by an edit) is forgotten, not reopened later.
    LaunchedEffect(openId, open) { if (openId != null && open == null) openId = null }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        if (open != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = { openId = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to ${day.title}")
                }
                Text(day.title, style = MaterialTheme.typography.titleSmall)
            }
            val actions = actionsFor(open.record)
            TransactionSheetContent(
                details = detailsOf(open.record),
                actions = TransactionSheetActions(
                    onEdit = { closeThen(actions.onEdit) },
                    onToggleBookmark = { closeThen(actions.onToggleBookmark) },
                    onCopy = { useToday -> closeThen { actions.onCopy(useToday) } },
                    // Closes the sheet so the Undo snackbar shows.
                    onDelete = { closeThen(actions.onDelete) }
                )
            )
        } else {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(day.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    day.income?.let { Text(it, color = IncomeBlue, fontWeight = FontWeight.SemiBold) }
                    if (day.income != null && day.expense != null) Spacer(Modifier.width(12.dp))
                    day.expense?.let { Text(it, color = ExpenseOrange, fontWeight = FontWeight.SemiBold) }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (day.rows.isEmpty()) {
                Text(
                    "Nothing logged on ${day.shortDate}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(day.rows, key = { it.id }) { row ->
                        TransactionRow(row = row, onClick = { openId = row.id })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    }
                }
            }
            Button(
                onClick = { closeThen(onAdd) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add on ${day.shortDate}")
            }
        }
    }
}
