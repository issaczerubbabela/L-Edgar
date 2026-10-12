package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.capture.categorize.ConfidenceBand
import com.issaczerubbabel.ledgar.ui.components.OptionPickerSheet
import com.issaczerubbabel.ledgar.ui.components.PickerOption
import com.issaczerubbabel.ledgar.ui.components.UndoSnackbar
import com.issaczerubbabel.ledgar.ui.theme.ExpenseOrange
import com.issaczerubbabel.ledgar.ui.theme.IncomeBlue
import com.issaczerubbabel.ledgar.util.DescriptionSuggestion
import com.issaczerubbabel.ledgar.util.DescriptionSuggestions
import com.issaczerubbabel.ledgar.util.DescriptionUse
import com.issaczerubbabel.ledgar.util.formatRupeesExact
import com.issaczerubbabel.ledgar.viewmodel.CaptureRowUi
import com.issaczerubbabel.ledgar.viewmodel.ReviewInboxViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private sealed interface Picker {
    data class ForCategory(val row: CaptureRowUi) : Picker
    data class ForAccount(val row: CaptureRowUi) : Picker
}

private val timeFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a")
private val dateFormat = DateTimeFormatter.ofPattern("d MMM")

/** The alert's own date when it differs from the day it arrived, else when it arrived. */
private fun whenText(arrivedAt: Long, txnTime: Long): String {
    val zone = ZoneId.systemDefault()
    val arrived = Instant.ofEpochMilli(arrivedAt).atZone(zone)
    val txn = Instant.ofEpochMilli(txnTime).atZone(zone)
    return if (txn.toLocalDate() == arrived.toLocalDate()) timeFormat.format(arrived)
    else dateFormat.format(txn) + " (date in the alert)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureInboxScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onAddToTrip: (tripId: Long, captureId: Long) -> Unit = { _, _ -> },
    vm: ReviewInboxViewModel = hiltViewModel(),
    activeTripVm: com.issaczerubbabel.ledgar.viewmodel.ActiveTripViewModel = hiltViewModel()
) {
    val activeTrip by activeTripVm.active.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val expenseCategories by vm.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()
    val descriptionUses by vm.descriptionUses.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf<Picker?>(null) }
    var showBulk by remember { mutableStateOf(false) }
    val accountNames = remember(accounts) { accounts.associate { it.id to it.accountName } }
    val bulk = rows.filter { it.isBulkCandidate }

    UndoSnackbar(
        pending = pending,
        snackbarHostState = snackbarHostState,
        onUndo = { action ->
            vm.undo(action)
            // The list keeps its place on the card that was first, so a card coming back above it
            // would stay out of sight: bring it into view.
            val captureId = action.hiddenCaptureIds.firstOrNull()
            if (captureId != null) {
                scope.launch {
                    val index = withTimeoutOrNull(1_000) {
                        snapshotFlow { rows.indexOfFirst { it.capture.id == captureId } }.first { it >= 0 }
                    } ?: return@launch
                    val headerItems = if (rows.any { it.isBulkCandidate }) 1 else 0
                    listState.animateScrollToItem(index + headerItems)
                }
            }
        },
        onCommit = vm::commitPending
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("To review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.padding(bottom = innerPadding.calculateBottomPadding())) },
        containerColor = MaterialTheme.colorScheme.background
    ) { scaffoldPadding ->
        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .padding(bottom = innerPadding.calculateBottomPadding()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nothing to review.\nBank and UPI alerts you receive will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .padding(bottom = innerPadding.calculateBottomPadding()),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (bulk.isNotEmpty()) {
                    item(key = "bulk") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${bulk.size} ready to confirm", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        formatRupeesExact(bulk.sumOf { it.capture.amount }) + " in total",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                TextButton(onClick = { showBulk = true }) { Text("Review all") }
                            }
                        }
                    }
                }
                items(rows, key = { it.capture.id }) { row ->
                    val categories = if (row.isIncome) incomeCategories else expenseCategories
                    SwipeableCaptureCard(
                        row = row,
                        accountName = row.accountId?.let { accountNames[it] },
                        descriptionUses = descriptionUses[row.type].orEmpty(),
                        validCategories = categories.toSet(),
                        validAccountIds = accountNames.keys,
                        onPickCategory = { picker = Picker.ForCategory(row) },
                        onPickAccount = { picker = Picker.ForAccount(row) },
                        onAlwaysUseChange = { vm.setAlwaysUse(row.capture.id, it) },
                        onDescriptionChange = { vm.setDescription(row.capture.id, it) },
                        onConfirm = { vm.confirm(row) },
                        onDismiss = { vm.dismiss(row) },
                        // ADR-0008: a shared trip payment becomes a Trip expense, never a full personal Transaction.
                        onAddToTrip = activeTrip?.takeIf { !row.isIncome }?.let { trip -> { onAddToTrip(trip.tripId, row.capture.id) } },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    when (val p = picker) {
        is Picker.ForCategory -> OptionPickerSheet(
            title = "Category",
            options = (if (p.row.isIncome) incomeCategories else expenseCategories).map { PickerOption(it) },
            selectedKey = p.row.category,
            onSelect = { vm.setCategory(p.row.capture.id, it); picker = null },
            onDismiss = { picker = null }
        )
        is Picker.ForAccount -> OptionPickerSheet(
            title = p.row.capture.accountHint?.let { "Account for XX$it" } ?: "Account",
            options = accounts.map { PickerOption(it.id.toString(), it.accountName) },
            selectedKey = p.row.accountId?.toString(),
            onSelect = { vm.setAccount(p.row.capture.id, it.toLong()); picker = null },
            onDismiss = { picker = null },
            manageHint = p.row.capture.accountHint?.let { "We'll remember XX$it for next time." }
        )
        null -> Unit
    }

    if (showBulk) {
        BulkConfirmDialog(
            candidates = bulk,
            accountNames = accountNames,
            onConfirm = { chosen -> vm.confirmAll(chosen); showBulk = false },
            onDismiss = { showBulk = false }
        )
    }
}

/**
 * A card that confirms when swiped right and dismisses when swiped left. A card still missing its
 * Category or Account springs back instead, with what's missing highlighted: nothing confirms
 * half-filled (ADR-0005).
 */
@Composable
private fun SwipeableCaptureCard(
    row: CaptureRowUi,
    accountName: String?,
    descriptionUses: List<DescriptionUse>,
    validCategories: Set<String>,
    validAccountIds: Set<Long>,
    onPickCategory: () -> Unit,
    onPickAccount: () -> Unit,
    onAlwaysUseChange: (Boolean) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onAddToTrip: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    // Not saved: a card that Undo brings back must start settled. A saved state would come back
    // still swiped away and dismiss the card again.
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { distance -> distance * 0.4f }) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var highlightMissing by remember { mutableStateOf(false) }
    var editingTitle by rememberSaveable { mutableStateOf(false) }

    // A tick as the swipe passes the point where letting go acts.
    LaunchedEffect(state.targetValue) {
        if (state.targetValue != SwipeToDismissBoxValue.Settled) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    LaunchedEffect(highlightMissing) {
        if (highlightMissing) {
            delay(2_500)
            highlightMissing = false
        }
    }

    val confirmOrShowMissing = {
        if (row.canConfirm) onConfirm() else highlightMissing = true
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction("Confirm") { confirmOrShowMissing(); true },
                CustomAccessibilityAction("Dismiss") { onDismiss(); true },
                CustomAccessibilityAction("Edit description") { editingTitle = true; true }
            )
        },
        gesturesEnabled = !editingTitle,
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (row.canConfirm) {
                        onConfirm()
                    } else {
                        highlightMissing = true
                        scope.launch { state.reset() }
                    }
                }
                SwipeToDismissBoxValue.EndToStart -> onDismiss()
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = { SwipeBackground(state.dismissDirection, canConfirm = row.canConfirm) }
    ) {
        CaptureCard(
            row = row,
            accountName = accountName,
            descriptionUses = descriptionUses,
            validCategories = validCategories,
            validAccountIds = validAccountIds,
            highlightMissing = highlightMissing,
            editingTitle = editingTitle,
            onEditingTitleChange = { editingTitle = it },
            onPickCategory = onPickCategory,
            onPickAccount = onPickAccount,
            onAlwaysUseChange = onAlwaysUseChange,
            onDescriptionChange = onDescriptionChange,
            onConfirm = confirmOrShowMissing,
            onDismiss = onDismiss,
            onAddToTrip = onAddToTrip
        )
    }
}

/** What letting go will do: Confirm in the primary colour to the right, Dismiss in the error colour to the left. */
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, canConfirm: Boolean) {
    if (direction == SwipeToDismissBoxValue.Settled) return
    val confirming = direction == SwipeToDismissBoxValue.StartToEnd
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                when {
                    !confirming -> colors.error
                    canConfirm -> colors.primary
                    else -> colors.surfaceVariant
                },
                CardDefaults.shape
            )
            .padding(horizontal = 24.dp),
        contentAlignment = if (confirming) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Text(
            when {
                !confirming -> "Dismiss"
                canConfirm -> "Confirm"
                else -> "Pick what's missing first"
            },
            color = when {
                !confirming -> colors.onError
                canConfirm -> colors.onPrimary
                else -> colors.onSurfaceVariant
            },
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
private fun CaptureCard(
    row: CaptureRowUi,
    accountName: String?,
    descriptionUses: List<DescriptionUse>,
    validCategories: Set<String>,
    validAccountIds: Set<Long>,
    highlightMissing: Boolean,
    editingTitle: Boolean,
    onEditingTitleChange: (Boolean) -> Unit,
    onPickCategory: () -> Unit,
    onPickAccount: () -> Unit,
    onAlwaysUseChange: (Boolean) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onAddToTrip: (() -> Unit)? = null
) {
    var showOriginal by remember { mutableStateOf(false) }
    val capture = row.capture
    val amountColor = if (row.isIncome) IncomeBlue else ExpenseOrange
    val missingBorder = BorderStroke(2.dp, MaterialTheme.colorScheme.error)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = if (editingTitle) Alignment.Top else Alignment.CenterVertically) {
                if (editingTitle) {
                    TitleField(
                        row = row,
                        descriptionUses = descriptionUses,
                        validCategories = validCategories,
                        validAccountIds = validAccountIds,
                        onDescriptionChange = onDescriptionChange,
                        onDone = { onEditingTitleChange(false) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        row.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clickable(onClickLabel = "Edit description") { onEditingTitleChange(true) }
                            .padding(vertical = 12.dp)
                    )
                }
                Text(
                    (if (row.isIncome) "+" else "−") + formatRupeesExact(capture.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor,
                    // Level with the text inside the field while editing.
                    modifier = Modifier.padding(start = 8.dp, top = if (editingTitle) 24.dp else 0.dp)
                )
            }
            Text(
                listOfNotNull(
                    whenText(capture.capturedAt, capture.txnTime),
                    capture.accountHint?.let { "XX$it" },
                    capture.channel.takeIf { it != "UNKNOWN" }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onPickCategory,
                    label = { Text(row.category ?: "Pick category", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    border = if (highlightMissing && row.category.isNullOrBlank()) missingBorder else AssistChipDefaults.assistChipBorder(true)
                )
                AssistChip(
                    onClick = onPickAccount,
                    label = { Text(accountName ?: "Map account", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    border = if (highlightMissing && row.accountId == null) missingBorder else AssistChipDefaults.assistChipBorder(true)
                )
            }
            Text(
                text = when {
                    row.categoryPickedByUser -> "Your pick"
                    row.category == null -> "Needs a category"
                    row.band == ConfidenceBand.HIGH -> "High confidence"
                    else -> "Check this guess"
                } + (row.why?.takeUnless { row.categoryPickedByUser }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (row.canOfferAlwaysUse) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlwaysUseChange(!row.alwaysUse) }
                ) {
                    Checkbox(checked = row.alwaysUse, onCheckedChange = onAlwaysUseChange)
                    Text(
                        "Always use ${row.category} for ${row.title}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (showOriginal) {
                Text(
                    capture.rawText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showOriginal = !showOriginal }) {
                    Text(if (showOriginal) "Hide original" else "Original text")
                }
                Box(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                if (onAddToTrip != null) TextButton(onClick = onAddToTrip) { Text("Add to trip") }
                Button(onClick = onConfirm, enabled = row.canConfirm) { Text("Confirm") }
            }
        }
    }
}

/**
 * The card's title as a "Description" field, with Descriptions used before as suggestions. Picking
 * one only fills the title; the card's Category and Account stay as they are. Left empty, the title
 * goes back to the merchant's usual name.
 */
@Composable
private fun TitleField(
    row: CaptureRowUi,
    descriptionUses: List<DescriptionUse>,
    validCategories: Set<String>,
    validAccountIds: Set<Long>,
    onDescriptionChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val text = row.descriptionDraft ?: row.title
    val focusRequester = remember { FocusRequester() }
    val suggestions: List<DescriptionSuggestion> = remember(text, descriptionUses) {
        DescriptionSuggestions.suggest(descriptionUses, text, validCategories, validAccountIds, LocalDate.now())
            .filterNot { it.description.equals(text.trim(), ignoreCase = true) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            supportingText = if (row.bankTitle != text.trim()) {
                { Text("From the bank: ${row.bankTitle}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
        if (suggestions.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suggestions, key = { it.description.lowercase() }) { suggestion ->
                    SuggestionChip(
                        onClick = { onDescriptionChange(suggestion.description) },
                        label = { Text(suggestion.description, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.semantics { contentDescription = "Use ${suggestion.description}" }
                    )
                }
            }
        }
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.End)) { Text("Done") }
    }
}

/** Read-only preview of what "confirm all" will save; each row can be left out. */
@Composable
private fun BulkConfirmDialog(
    candidates: List<CaptureRowUi>,
    accountNames: Map<Long, String>,
    onConfirm: (List<CaptureRowUi>) -> Unit,
    onDismiss: () -> Unit
) {
    val excluded = remember { emptyList<Long>().toMutableStateList() }
    val chosen = candidates.filter { it.capture.id !in excluded }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm ${chosen.size} of ${candidates.size}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(candidates, key = { it.capture.id }) { row ->
                    val included = row.capture.id !in excluded
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (included) excluded.add(row.capture.id) else excluded.remove(row.capture.id) }
                    ) {
                        Checkbox(
                            checked = included,
                            onCheckedChange = { if (it) excluded.remove(row.capture.id) else excluded.add(row.capture.id) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${row.category} · ${accountNames[row.accountId].orEmpty()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(formatRupeesExact(row.capture.amount), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen) }, enabled = chosen.isNotEmpty()) {
                Text("Confirm ${formatRupeesExact(chosen.sumOf { it.capture.amount })}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
