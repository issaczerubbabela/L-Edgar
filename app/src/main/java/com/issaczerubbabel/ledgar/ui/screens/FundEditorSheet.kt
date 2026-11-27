package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import com.issaczerubbabel.ledgar.ui.components.OptionPickerSheet
import com.issaczerubbabel.ledgar.ui.components.PickerOption
import com.issaczerubbabel.ledgar.ui.components.SectionLabel
import com.issaczerubbabel.ledgar.ui.components.SingleDatePickerDialog
import com.issaczerubbabel.ledgar.ui.theme.ExpenseRed
import com.issaczerubbabel.ledgar.ui.theme.bucketColor
import com.issaczerubbabel.ledgar.util.formatRupees
import com.issaczerubbabel.ledgar.viewmodel.FundDraft
import com.issaczerubbabel.ledgar.viewmodel.FundEditorUiState
import com.issaczerubbabel.ledgar.viewmodel.FundEditorViewModel
import com.issaczerubbabel.ledgar.viewmodel.HoldingDraft
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FUND_COLOR_NAMES = listOf("Cyan", "Red", "Green", "Orange", "Purple", "Yellow", "Blue", "Pink", "Brown")
private val sheetDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** Which date the sheet's date picker is setting. */
private sealed interface FundDateTarget {
    data object Due : FundDateTarget
    data class Start(val accountId: Long) : FundDateTarget
}

/**
 * The New / Edit Fund sheet: name, emoji, colour, an optional target and due date, and the
 * Fund's holdings, with Archive and Delete for a saved Fund.
 *
 * Any screen can open it: pass a null [fundId] for "+ New fund" (the Funds list, the Budget tab), or
 * a Fund's id to edit it. [onSaved] gets the Fund's id; [onDeleted] runs after Delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundEditorSheet(
    fundId: Long?,
    onDismiss: () -> Unit,
    onSaved: (Long) -> Unit = {},
    onDeleted: () -> Unit = {},
    vm: FundEditorViewModel = hiltViewModel(key = "fund-editor-${fundId ?: "new"}")
) {
    LaunchedEffect(fundId) { vm.start(fundId) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val close = {
        vm.close()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        val draft = state.draft
        if (draft == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            FundEditorContent(
                state = state,
                draft = draft,
                onEdit = vm::edit,
                onAddHolding = vm::addHolding,
                onRemoveHolding = vm::removeHolding,
                onSave = { vm.save { id -> vm.close(); onSaved(id); onDismiss() } },
                onCancel = close,
                onArchive = { vm.archive { vm.close(); onDismiss() } },
                onUnarchive = { vm.unarchive { vm.close(); onDismiss() } },
                onDelete = { vm.delete { vm.close(); onDismiss(); onDeleted() } }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FundEditorContent(
    state: FundEditorUiState,
    draft: FundDraft,
    onEdit: ((FundDraft) -> FundDraft) -> Unit,
    onAddHolding: (HoldingKind, Long) -> Unit,
    onRemoveHolding: (Long) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit
) {
    var picking by remember { mutableStateOf<HoldingKind?>(null) }
    var dateTarget by remember { mutableStateOf<FundDateTarget?>(null) }
    var confirming by remember { mutableStateOf<String?>(null) }
    val names = state.accountNames

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (draft.isNew) "New fund" else "Edit fund",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = draft.emoji,
                onValueChange = { value -> onEdit { it.copy(emoji = value.take(4)) } },
                label = { Text("Emoji") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = draft.name,
                onValueChange = { value -> onEdit { it.copy(name = value) } },
                label = { Text("Name") },
                placeholder = { Text("Emergency") },
                singleLine = true,
                isError = draft.name.isBlank(),
                modifier = Modifier.weight(3f)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Colour")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(BudgetBucket.COLOR_COUNT) { index ->
                    val isSelected = index == draft.colorIndex
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(bucketColor(index), CircleShape)
                            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onEdit { it.copy(colorIndex = index) } })
                            .semantics { contentDescription = FUND_COLOR_NAMES.getOrElse(index) { "Colour ${index + 1}" } },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.background)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Target (optional)")
            OutlinedTextField(
                value = draft.targetInput,
                onValueChange = { value -> onEdit { it.copy(targetInput = value.filter { c -> c.isDigit() || c == '.' || c == ',' }) } },
                label = { Text("Target amount") },
                prefix = { Text("₹") },
                singleLine = true,
                isError = draft.targetError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { dateTarget = FundDateTarget.Due },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text(draft.dueDate?.let { "Due ${it.format(sheetDate)}" } ?: "Add a due date") }
                if (draft.dueDate != null) {
                    IconButton(onClick = { onEdit { it.copy(dueDate = null) } }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear due date")
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Holdings")
            Text(
                text = "Where this Fund's money sits: a whole account, or a fixed amount in a shared one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            draft.holdings.forEach { holding ->
                HoldingEditor(
                    holding = holding,
                    accountName = names[holding.accountId] ?: "Unknown account",
                    onChange = { changed -> onEdit { d -> d.copy(holdings = d.holdings.map { if (it.accountId == changed.accountId) changed else it }) } },
                    onPickStart = { dateTarget = FundDateTarget.Start(holding.accountId) },
                    onRemove = { onRemoveHolding(holding.accountId) }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { picking = HoldingKind.WHOLE },
                    enabled = state.choices(HoldingKind.WHOLE).isNotEmpty(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text("Whole account") }
                OutlinedButton(
                    onClick = { picking = HoldingKind.FIXED },
                    enabled = state.choices(HoldingKind.FIXED).isNotEmpty(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text("Fixed amount in") }
            }
            Text(
                text = "An account another Fund holds whole isn't offered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        state.error?.let { Text(text = it, color = ExpenseRed, style = MaterialTheme.typography.bodyMedium) }

        Button(
            onClick = onSave,
            enabled = draft.canSave && !state.isSaving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text(if (draft.isNew) "Create fund" else "Save") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel") }
        if (!draft.isNew) {
            if (state.isArchived) {
                TextButton(onClick = onUnarchive, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Unarchive fund") }
            } else {
                TextButton(onClick = { confirming = "archive" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Archive fund") }
            }
            TextButton(onClick = { confirming = "delete" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Delete fund", color = ExpenseRed)
            }
        }
        Box(modifier = Modifier.size(16.dp))
    }

    picking?.let { kind ->
        OptionPickerSheet(
            title = if (kind == HoldingKind.WHOLE) "Whole account" else "Fixed amount in",
            options = state.choices(kind).map { PickerOption(it.id.toString(), it.accountName) },
            selectedKey = null,
            onSelect = { key ->
                key.toLongOrNull()?.let { onAddHolding(kind, it) }
                picking = null
            },
            onDismiss = { picking = null }
        )
    }

    dateTarget?.let { target ->
        val initial = when (target) {
            FundDateTarget.Due -> draft.dueDate ?: LocalDate.now()
            is FundDateTarget.Start -> draft.holdings.firstOrNull { it.accountId == target.accountId }?.startDate ?: LocalDate.now()
        }
        SingleDatePickerDialog(
            initialDate = initial,
            onDismiss = { dateTarget = null },
            onConfirm = { date ->
                onEdit { d ->
                    when (target) {
                        FundDateTarget.Due -> d.copy(dueDate = date)
                        is FundDateTarget.Start -> d.copy(holdings = d.holdings.map { if (it.accountId == target.accountId) it.copy(startDate = date) else it })
                    }
                }
                dateTarget = null
            }
        )
    }

    confirming?.let { action ->
        val released = formatRupees(state.fundBalance)
        val name = draft.name.ifBlank { "this fund" }
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(if (action == "delete") "Delete $name?" else "Archive $name?") },
            text = {
                Text(
                    if (action == "delete") {
                        "Its holdings and planned contributions are removed, and $released goes back to Free. Your transactions are not touched."
                    } else {
                        "It keeps its history, and $released goes back to Free. You can unarchive it later."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    if (action == "delete") onDelete() else onArchive()
                }) { Text(if (action == "delete") "Delete" else "Archive", color = if (action == "delete") ExpenseRed else MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HoldingEditor(
    holding: HoldingDraft,
    accountName: String,
    onChange: (HoldingDraft) -> Unit,
    onPickStart: () -> Unit,
    onRemove: () -> Unit
) {
    val isWhole = holding.kind == HoldingKind.WHOLE
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(accountName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (isWhole) "Whole account: its balance is the holding" else "Fixed amount in this account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove holding in $accountName")
            }
        }
        if (!isWhole) {
            OutlinedTextField(
                value = holding.amountInput,
                onValueChange = { value -> onChange(holding.copy(amountInput = value.filter { it.isDigit() || it == '.' || it == ',' })) },
                label = { Text("Amount") },
                prefix = { Text("₹") },
                singleLine = true,
                isError = holding.amountError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = holding.openingInput,
            onValueChange = { value -> onChange(holding.copy(openingInput = value.filter { it.isDigit() || it == '.' || it == ',' })) },
            label = { Text("Opening amount") },
            prefix = { Text("₹") },
            placeholder = { if (!isWhole) Text("Same as the amount") },
            supportingText = { Text("Money already there before you started tracking") },
            singleLine = true,
            isError = holding.openingError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = onPickStart, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Starts ${holding.startDate.format(sheetDate)}")
        }
    }
}
