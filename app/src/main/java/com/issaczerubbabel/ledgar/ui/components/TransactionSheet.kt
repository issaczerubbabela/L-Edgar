package com.issaczerubbabel.ledgar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.ledger.Ledger
import com.issaczerubbabel.ledgar.ledger.TransactionDetails
import com.issaczerubbabel.ledgar.viewmodel.TransactionActionsViewModel

/** What a transaction sheet can do with its Transaction. The sheet slides away before each one runs. */
class TransactionSheetActions(
    val onEdit: () -> Unit,
    val onToggleBookmark: () -> Unit,
    /** True to copy it for today, false to keep its own date. */
    val onCopy: (useToday: Boolean) -> Unit,
    val onDelete: () -> Unit
)

/** The transaction sheet: tapping a Transaction anywhere opens this. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSheet(
    details: TransactionDetails,
    actions: TransactionSheetActions,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Slide the sheet away, then close it and act.
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        TransactionSheetContent(
            details,
            TransactionSheetActions(
                onEdit = { closeThen(actions.onEdit) },
                onToggleBookmark = { closeThen(actions.onToggleBookmark) },
                onCopy = { useToday -> closeThen { actions.onCopy(useToday) } },
                onDelete = { closeThen(actions.onDelete) }
            )
        )
    }
}

/**
 * The sheet's details and actions without the sheet around them, for a sheet that shows other
 * things too.
 */
@Composable
fun TransactionSheetContent(
    details: TransactionDetails,
    actions: TransactionSheetActions,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
    ) {
        Text(
            details.typeLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            details.amount,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            fontStyle = if (details.isAdjustment) FontStyle.Italic else null,
            color = transactionAmountColor(details.type)
        )
        Spacer(Modifier.height(4.dp))
        Text(details.description, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DetailLine("Date", details.date)
            if (details.accounts.isNotBlank()) {
                DetailLine(if (details.isTransfer) "Accounts" else "Account", details.accounts)
            }
            details.category?.let { DetailLine("Category", it) }
            details.note?.let { DetailLine("Note", it) }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(4.dp))

        SheetAction(Icons.Filled.Edit, "Edit", onClick = actions.onEdit)
        SheetAction(
            if (details.isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
            if (details.isBookmarked) "Remove bookmark" else "Bookmark",
            onClick = actions.onToggleBookmark
        )
        SheetAction(Icons.Filled.ContentCopy, "Copy for today", onClick = { actions.onCopy(true) })
        SheetAction(Icons.Filled.ContentCopy, "Copy for ${details.shortDate}", onClick = { actions.onCopy(false) })

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(4.dp))
        // Apart from the rest and in the error colour, so it isn't hit by mistake.
        SheetAction(Icons.Filled.Delete, "Delete", color = MaterialTheme.colorScheme.error, onClick = actions.onDelete)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (color == Color.Unspecified) ButtonDefaults.textButtonColors()
        else ButtonDefaults.textButtonColors(contentColor = color)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * The transaction sheet for screens outside the Ledger (Search, Bookmarks, an Account's page):
 * bookmarking and deleting go straight to the repository.
 */
@Composable
fun TransactionSheetHost(
    transaction: ExpenseRecord,
    onDismiss: () -> Unit,
    onEdit: (Long) -> Unit,
    onCopy: (id: Long, useToday: Boolean) -> Unit,
    vm: TransactionActionsViewModel = hiltViewModel()
) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    TransactionSheet(
        details = Ledger.details(transaction, accounts),
        actions = TransactionSheetActions(
            onEdit = { onEdit(transaction.id) },
            onToggleBookmark = { vm.toggleBookmark(transaction) },
            onCopy = { useToday -> onCopy(transaction.id, useToday) },
            onDelete = { vm.delete(transaction) }
        ),
        onDismiss = onDismiss
    )
}
