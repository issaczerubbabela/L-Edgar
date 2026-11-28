package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import com.issaczerubbabel.ledgar.viewmodel.BulkItem
import com.issaczerubbabel.ledgar.viewmodel.CaptureDay
import com.issaczerubbabel.ledgar.viewmodel.CaptureFilter
import com.issaczerubbabel.ledgar.viewmodel.CaptureInbox
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.ledger.Ledger
import com.issaczerubbabel.ledgar.ui.components.OptionPickerSheet
import com.issaczerubbabel.ledgar.ui.components.PickerOption
import com.issaczerubbabel.ledgar.ui.components.TransactionRow
import com.issaczerubbabel.ledgar.ui.components.UndoSnackbar
import com.issaczerubbabel.ledgar.ui.theme.ExpenseOrange
import com.issaczerubbabel.ledgar.ui.theme.IncomeBlue
import com.issaczerubbabel.ledgar.util.DescriptionSuggestion
import com.issaczerubbabel.ledgar.util.DescriptionSuggestions
import com.issaczerubbabel.ledgar.util.DescriptionUse
import com.issaczerubbabel.ledgar.util.TransactionType
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

    /** A Transfer's other side: the To-account of a debit, the From-account of a credit. */
    data class ForOtherAccount(val row: CaptureRowUi) : Picker
}

private val CAPTURE_TYPES = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)

/** "To CUB Savings" for a debit Transfer, "From HDFC Savings" for a credit one. */
private fun otherSideLabel(row: CaptureRowUi, name: String?): String =
    (if (row.isDebit) "To " else "From ") + (name ?: "account")

private val timeFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a")
private val dateFormat = DateTimeFormatter.ofPattern("d MMM")
private val spokenDateFormat = DateTimeFormatter.ofPattern("d MMMM")

/** The alert's own date when it differs from the day it arrived, else when it arrived. */
private fun whenText(arrivedAt: Long, txnTime: Long): String {
    val zone = ZoneId.systemDefault()
    val arrived = Instant.ofEpochMilli(arrivedAt).atZone(zone)
    val txn = Instant.ofEpochMilli(txnTime).atZone(zone)
    return if (txn.toLocalDate() == arrived.toLocalDate()) timeFormat.format(arrived)
    else dateFormat.format(txn) + " (date in the alert)"
}

/** Which captures the bulk sheet offers: every day's (Review all) or one day's (Confirm day). */
private sealed interface BulkScope {
    data object AllDays : BulkScope
    data class Day(val date: LocalDate) : BulkScope
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureInboxScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onAddToTrip: (tripId: Long, captureId: Long) -> Unit = { _, _ -> },
    /** Edit: the Add Transaction form, filled in from the capture. Saving there is Confirm. */
    onEdit: (captureId: Long) -> Unit = {},
    vm: ReviewInboxViewModel = hiltViewModel(),
    activeTripVm: com.issaczerubbabel.ledgar.viewmodel.ActiveTripViewModel = hiltViewModel()
) {
    val activeTrip by activeTripVm.active.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val days by vm.days.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val expandedId by vm.expandedId.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val expenseCategories by vm.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()
    val descriptionUses by vm.descriptionUses.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf<Picker?>(null) }
    var bulkScope by remember { mutableStateOf<BulkScope?>(null) }
    val accountNames = remember(accounts) { accounts.associate { it.id to it.accountName } }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val newestFirst = remember(rows) { rows.sortedByDescending { it.capture.txnTime } }
    val allDaysSheet = remember(newestFirst) { CaptureInbox.bulkSheet(newestFirst) }
    // The list's items in order, to find where a card that Undo brings back now sits.
    val itemKeys: List<Any> = buildList {
        if (allDaysSheet.isNotEmpty()) add(REVIEW_ALL_KEY)
        add(FILTERS_KEY)
        days.forEach { day ->
            add(dayKey(day.date))
            day.rows.forEach { add(it.capture.id) }
        }
    }
    val currentItemKeys by rememberUpdatedState(itemKeys)

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
                        snapshotFlow { currentItemKeys.indexOf(captureId) }.first { it >= 0 }
                    } ?: return@launch
                    listState.animateScrollToItem(index)
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (allDaysSheet.isNotEmpty()) {
                    item(key = REVIEW_ALL_KEY) {
                        ReviewAllCard(allDaysSheet, onReview = { bulkScope = BulkScope.AllDays }, modifier = Modifier.animateItem())
                    }
                }
                item(key = FILTERS_KEY) {
                    FilterRow(
                        selected = filter,
                        counts = remember(rows) { CaptureFilter.entries.associateWith { CaptureInbox.count(rows, it) } },
                        onSelect = vm::setFilter
                    )
                }
                if (days.isEmpty()) {
                    item(key = "none") {
                        Text(
                            "Nothing under ${filter.label} right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }
                days.forEach { day ->
                    item(key = dayKey(day.date)) {
                        DayHeader(
                            day = day,
                            today = today,
                            onConfirmDay = { bulkScope = BulkScope.Day(day.date) },
                            modifier = Modifier.animateItem()
                        )
                    }
                    items(day.rows, key = { it.capture.id }) { row ->
                        val credit = row.transferPartner
                        if (credit != null) {
                            TransferSuggestionCard(
                                row = row,
                                fromName = row.accountId?.let { accountNames[it] },
                                toName = credit.accountId?.let { accountNames[it] },
                                zone = zone,
                                onPickFrom = { picker = Picker.ForAccount(row) },
                                onPickTo = { picker = Picker.ForAccount(credit) },
                                onConfirm = { vm.confirm(row) },
                                onSplit = { vm.split(row) },
                                modifier = Modifier.animateItem()
                            )
                            return@items
                        }
                        val categories = if (row.isIncome) incomeCategories else expenseCategories
                        SwipeableCaptureCard(
                            row = row,
                            expanded = row.capture.id == expandedId,
                            onToggleExpanded = { vm.toggleExpanded(row.capture.id) },
                            onExpand = { vm.expand(row.capture.id) },
                            zone = zone,
                            accountName = row.accountId?.let { accountNames[it] },
                            otherAccountName = row.otherAccountId?.let { accountNames[it] },
                            descriptionUses = descriptionUses[row.type].orEmpty(),
                            validCategories = categories.toSet(),
                            validAccountIds = accountNames.keys,
                            onPickCategory = { picker = Picker.ForCategory(row) },
                            onPickAccount = { picker = Picker.ForAccount(row) },
                            onPickOtherAccount = { picker = Picker.ForOtherAccount(row) },
                            onTypeChange = { vm.setType(row.capture.id, it) },
                            onEdit = {
                                vm.prepareEdit(row)
                                onEdit(row.capture.id)
                            },
                            onAlwaysUseChange = { vm.setAlwaysUse(row.capture.id, it) },
                            onDescriptionChange = { vm.setDescription(row.capture.id, it) },
                            accounts = accounts,
                            onConfirm = { vm.confirm(row) },
                            onLink = { vm.link(row) },
                            onDismiss = { vm.dismiss(row) },
                            onNotSamePayment = if (row.isMerged) ({ vm.notSamePayment(row) }) else null,
                            // ADR-0008: a shared trip payment becomes a Trip expense, never a full personal Transaction.
                            onAddToTrip = activeTrip?.takeIf { row.type == TransactionType.EXPENSE }?.let { trip -> { onAddToTrip(trip.tripId, row.capture.id) } },
                            modifier = Modifier.animateItem()
                        )
                    }
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
        is Picker.ForOtherAccount -> OptionPickerSheet(
            title = if (p.row.isDebit) "Transfer to" else "Transfer from",
            // The capture's own Account is the other side already.
            options = accounts.filter { it.id != p.row.accountId }.map { PickerOption(it.id.toString(), it.accountName) },
            selectedKey = p.row.otherAccountId?.toString(),
            onSelect = { vm.setOtherAccount(p.row.capture.id, it.toLong()); picker = null },
            onDismiss = { picker = null }
        )
        null -> Unit
    }

    bulkScope?.let { shown ->
        // Worked out live, so a card confirmed or dismissed meanwhile drops out of the sheet.
        val sheet = when (shown) {
            BulkScope.AllDays -> allDaysSheet
            is BulkScope.Day -> CaptureInbox.bulkSheet(newestFirst.filter { CaptureInbox.dayOf(it, zone) == shown.date })
        }
        BulkConfirmSheet(
            scopeKey = shown,
            title = when (shown) {
                BulkScope.AllDays -> "All days"
                is BulkScope.Day -> CaptureInbox.dayTitle(shown.date, today)
            },
            sheet = sheet,
            accountNames = accountNames,
            onConfirm = { chosen -> vm.confirmAll(chosen); bulkScope = null },
            onDismiss = { bulkScope = null }
        )
    }
}

private const val REVIEW_ALL_KEY = "review-all"
private const val FILTERS_KEY = "filters"
private fun dayKey(date: LocalDate) = "day-$date"

/** "2 ready to confirm, 3 to check": opens the bulk sheet over every day. */
@Composable
private fun ReviewAllCard(sheet: List<BulkItem>, onReview: () -> Unit, modifier: Modifier = Modifier) {
    val ready = sheet.filter { it.tickedAtStart }
    val toCheck = sheet.size - ready.size
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(
                    listOfNotNull(
                        "${ready.size} ready to confirm".takeIf { ready.isNotEmpty() },
                        "$toCheck to check".takeIf { toCheck > 0 }
                    ).joinToString(", "),
                    style = MaterialTheme.typography.titleSmall
                )
                if (ready.isNotEmpty()) {
                    Text(
                        formatRupeesExact(ready.sumOf { it.row.capture.amount }) + " in total",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            TextButton(onClick = onReview) { Text("Review all") }
        }
    }
}

/** All / Needs a pick / Ready, each with how many waiting captures it shows. */
@Composable
private fun FilterRow(selected: CaptureFilter, counts: Map<CaptureFilter, Int>, onSelect: (CaptureFilter) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        CaptureFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text("${filter.label} ${counts[filter] ?: 0}") }
            )
        }
    }
}

/**
 * A day's header: its date, how many captures wait and their total, and Confirm day when the day has
 * something the bulk sheet can offer. TalkBack can jump between headers.
 */
@Composable
private fun DayHeader(day: CaptureDay, today: LocalDate, onConfirmDay: () -> Unit, modifier: Modifier = Modifier) {
    val title = CaptureInbox.dayTitle(day.date, today)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics {
                    heading()
                    contentDescription = CaptureInbox.spokenDay(day, today)
                }
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                CaptureInbox.daySummary(day),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (day.bulk.isNotEmpty()) {
            TextButton(
                onClick = onConfirmDay,
                modifier = Modifier.semantics { contentDescription = "Confirm day, $title" }
            ) { Text("Confirm day") }
        }
    }
}

/**
 * A capture as a compact row, or in full while [expanded]. Either way it confirms when swiped right
 * and dismisses when swiped left. A row still missing its Category or Account springs back instead
 * and opens in full, with what's missing highlighted: nothing confirms half-filled (ADR-0005).
 */
@Composable
private fun SwipeableCaptureCard(
    row: CaptureRowUi,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onExpand: () -> Unit,
    zone: ZoneId,
    accountName: String?,
    otherAccountName: String?,
    descriptionUses: List<DescriptionUse>,
    validCategories: Set<String>,
    validAccountIds: Set<Long>,
    onPickCategory: () -> Unit,
    onPickAccount: () -> Unit,
    onPickOtherAccount: () -> Unit,
    onTypeChange: (String) -> Unit,
    onEdit: () -> Unit,
    onAlwaysUseChange: (Boolean) -> Unit,
    onDescriptionChange: (String) -> Unit,
    accounts: List<AccountRecord>,
    onConfirm: () -> Unit,
    /** Same one: offered instead of Confirm when the capture may already be in the Ledger. */
    onLink: () -> Unit,
    onDismiss: () -> Unit,
    /** Splits a capture merged from several Alerts back into one per Alert; null for a single Alert. */
    onNotSamePayment: (() -> Unit)?,
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
    // A collapsed row has no title field to be editing.
    LaunchedEffect(expanded) { if (!expanded) editingTitle = false }

    val showMissing = {
        highlightMissing = true
        onExpand()
    }
    val confirmOrShowMissing = {
        if (row.canConfirm) onConfirm() else showMissing()
    }
    // What the swipes do, and the card's other choices, for TalkBack without swiping.
    val actions = buildList {
        if (row.match != null) {
            add(CustomAccessibilityAction("Same one, link to the logged transaction") { onLink(); true })
            add(CustomAccessibilityAction("Add anyway") { confirmOrShowMissing(); true })
        } else {
            add(CustomAccessibilityAction("Confirm") { confirmOrShowMissing(); true })
        }
        add(CustomAccessibilityAction("Dismiss") { onDismiss(); true })
        add(CustomAccessibilityAction("Edit description") { onExpand(); editingTitle = true; true })
        add(CustomAccessibilityAction("Edit in full") { onEdit(); true })
        onNotSamePayment?.let { split -> add(CustomAccessibilityAction("Not the same payment, split into separate captures") { split(); true }) }
    }

    SwipeToDismissBox(
        state = state,
        // The compact row carries the actions itself, as the one stop TalkBack makes on it.
        modifier = modifier.semantics { if (expanded) customActions = actions },
        gesturesEnabled = !editingTitle,
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // On a card that may already be logged, the swipe is the safe choice: Same one.
                    if (row.match != null) {
                        onLink()
                    } else if (row.canConfirm) {
                        onConfirm()
                    } else {
                        showMissing()
                        scope.launch { state.reset() }
                    }
                }
                SwipeToDismissBoxValue.EndToStart -> onDismiss()
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = { SwipeBackground(state.dismissDirection, canConfirm = row.canConfirm, links = row.match != null) }
    ) {
        if (expanded) {
            CaptureCard(
                row = row,
                accountName = accountName,
                otherAccountName = otherAccountName,
                descriptionUses = descriptionUses,
                validCategories = validCategories,
                validAccountIds = validAccountIds,
                highlightMissing = highlightMissing,
                editingTitle = editingTitle,
                onEditingTitleChange = { editingTitle = it },
                onCollapse = onToggleExpanded,
                onPickCategory = onPickCategory,
                onPickAccount = onPickAccount,
                onPickOtherAccount = onPickOtherAccount,
                onTypeChange = onTypeChange,
                onEdit = onEdit,
                onAlwaysUseChange = onAlwaysUseChange,
                onDescriptionChange = onDescriptionChange,
                accounts = accounts,
                onConfirm = confirmOrShowMissing,
                onLink = onLink,
                onDismiss = onDismiss,
                onNotSamePayment = onNotSamePayment,
                onAddToTrip = onAddToTrip
            )
        } else {
            CompactCaptureRow(
                row = row,
                accountName = accountName,
                otherAccountName = otherAccountName,
                spoken = CaptureInbox.spokenLabel(row, accountName, otherAccountName, zone),
                actions = actions,
                onExpand = onToggleExpanded
            )
        }
    }
}

/** High, Check or Low as a dot; TalkBack hears the band in the row's sentence instead. */
@Composable
private fun BandDot(band: ConfidenceBand) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(
                when (band) {
                    ConfidenceBand.HIGH -> colors.primary
                    ConfidenceBand.CHECK -> colors.tertiary
                    ConfidenceBand.LOW -> colors.outline
                },
                CircleShape
            )
    )
}

/** A small label for the Category or Account on a compact row; [missing] draws it in the error colours. */
@Composable
private fun RowPill(text: String, missing: Boolean, guess: Boolean = false) {
    Surface(
        color = if (missing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (missing) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontStyle = if (guess) FontStyle.Italic else FontStyle.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * The collapsed capture: band dot, title (italic while it's the Suggested description), the
 * Category and Account as pills, and the amount. Tapping opens it in full. TalkBack reads it as one
 * sentence and offers the swipe actions as custom actions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactCaptureRow(
    row: CaptureRowUi,
    accountName: String?,
    otherAccountName: String?,
    spoken: String,
    actions: List<CustomAccessibilityAction>,
    onExpand: () -> Unit
) {
    val amountColor = when {
        row.isTransfer -> MaterialTheme.colorScheme.onSurface
        row.isIncome -> IncomeBlue
        else -> ExpenseOrange
    }
    Card(
        onClick = onExpand,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = spoken
                stateDescription = "Collapsed"
                customActions = actions
                onClick(label = "Expand") { onExpand(); true }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            BandDot(row.band)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = if (row.titleIsGuess) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (row.isTransfer) {
                        RowPill(otherSideLabel(row, otherAccountName), missing = row.otherAccountId == null)
                    } else {
                        RowPill(row.category ?: "Pick category", missing = row.category.isNullOrBlank())
                    }
                    RowPill(
                        accountName?.let { if (row.accountIsGuess) "$it?" else it } ?: "Map account",
                        missing = row.accountId == null,
                        guess = row.accountIsGuess
                    )
                    if (row.match != null) RowPill("Already logged?", missing = false)
                }
            }
            Text(
                (if (row.isDebit) "−" else "+") + formatRupeesExact(row.capture.amount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = amountColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * What letting go will do: Confirm (Link, on a card that may already be logged) in the primary
 * colour to the right, Dismiss in the error colour to the left.
 */
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, canConfirm: Boolean, links: Boolean) {
    if (direction == SwipeToDismissBoxValue.Settled) return
    val confirming = direction == SwipeToDismissBoxValue.StartToEnd
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                when {
                    !confirming -> colors.error
                    links || canConfirm -> colors.primary
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
                links -> "Link"
                canConfirm -> "Confirm"
                else -> "Pick what's missing first"
            },
            color = when {
                !confirming -> colors.onError
                links || canConfirm -> colors.onPrimary
                else -> colors.onSurfaceVariant
            },
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptureCard(
    row: CaptureRowUi,
    accountName: String?,
    otherAccountName: String?,
    descriptionUses: List<DescriptionUse>,
    validCategories: Set<String>,
    validAccountIds: Set<Long>,
    highlightMissing: Boolean,
    editingTitle: Boolean,
    onEditingTitleChange: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    onPickCategory: () -> Unit,
    onPickAccount: () -> Unit,
    onPickOtherAccount: () -> Unit,
    onTypeChange: (String) -> Unit,
    onEdit: () -> Unit,
    onAlwaysUseChange: (Boolean) -> Unit,
    onDescriptionChange: (String) -> Unit,
    accounts: List<AccountRecord>,
    onConfirm: () -> Unit,
    onLink: () -> Unit,
    onDismiss: () -> Unit,
    onNotSamePayment: (() -> Unit)? = null,
    onAddToTrip: (() -> Unit)? = null
) {
    var showOriginal by remember { mutableStateOf(false) }
    val capture = row.capture
    val amountColor = when {
        row.isTransfer -> MaterialTheme.colorScheme.onSurface
        row.isIncome -> IncomeBlue
        else -> ExpenseOrange
    }
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
                        // A Suggested description reads as a guess until the user keeps or edits it.
                        fontStyle = if (row.titleIsGuess) FontStyle.Italic else FontStyle.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clickable(onClickLabel = "Edit description") { onEditingTitleChange(true) }
                            .padding(vertical = 12.dp)
                            .semantics { if (row.titleIsGuess) contentDescription = "Suggested description: ${row.title}" }
                    )
                }
                Text(
                    (if (row.isDebit) "−" else "+") + formatRupeesExact(capture.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor,
                    // Level with the text inside the field while editing.
                    modifier = Modifier.padding(start = 8.dp, top = if (editingTitle) 24.dp else 0.dp)
                )
                if (!editingTitle) {
                    IconButton(onClick = onCollapse) {
                        Icon(Icons.Filled.ExpandLess, contentDescription = "Collapse")
                    }
                }
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
            row.match?.let { AlreadyLoggedStrip(it, accounts) }
            if (!editingTitle && (row.titleIsGuess || row.descriptionAlternatives.isNotEmpty())) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        if (row.titleIsGuess) "Suggested title" else "Or",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    row.descriptionAlternatives.forEach { alternative ->
                        SuggestionChip(
                            onClick = { onDescriptionChange(alternative) },
                            label = { Text(alternative, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.semantics { contentDescription = "Use $alternative as the title" }
                        )
                    }
                }
            }
            // The bank's direction stays: a Transfer only decides where the money went or came from.
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                CAPTURE_TYPES.forEachIndexed { index, type ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index, CAPTURE_TYPES.size),
                        selected = row.type == type,
                        onClick = { if (row.type != type) onTypeChange(type) },
                        label = { Text(type, maxLines = 1) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.isTransfer) {
                    AssistChip(
                        onClick = onPickOtherAccount,
                        label = { Text(otherSideLabel(row, otherAccountName), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        border = if (highlightMissing && row.otherAccountId == null) missingBorder else AssistChipDefaults.assistChipBorder(true)
                    )
                } else {
                    AssistChip(
                        onClick = onPickCategory,
                        label = { Text(row.category ?: "Pick category", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        border = if (highlightMissing && row.category.isNullOrBlank()) missingBorder else AssistChipDefaults.assistChipBorder(true)
                    )
                }
                AssistChip(
                    onClick = onPickAccount,
                    label = {
                        Text(
                            accountName?.let { if (row.accountIsGuess) "$it?" else it } ?: "Map account",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.semantics {
                        if (row.accountIsGuess && accountName != null) contentDescription = "Suggested account: $accountName"
                    },
                    border = if (highlightMissing && row.accountId == null) missingBorder else AssistChipDefaults.assistChipBorder(true)
                )
            }
            Text(
                text = when {
                    row.categoryPickedByUser -> "Your pick"
                    row.isTransfer && row.otherAccountId == null -> if (row.isDebit) "Needs the To account" else "Needs the From account"
                    !row.isTransfer && row.category == null -> "Needs a category"
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
                        "Always use ${if (row.isTransfer) "Transfer " + otherSideLabel(row, otherAccountName).replaceFirstChar { it.lowercase() } else row.category} for ${row.merchantTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (showOriginal) {
                // Every Alert the capture was read from, each under its source (ADR-0012).
                row.originals.forEach { original ->
                    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                        Text(original.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(original.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            FlowRow {
                TextButton(onClick = { showOriginal = !showOriginal }) {
                    Text(if (showOriginal) "Hide original" else "Original text")
                }
                TextButton(onClick = onEdit) { Text("Edit…") }
                if (onNotSamePayment != null) TextButton(onClick = onNotSamePayment) { Text("Not the same payment") }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                if (onAddToTrip != null) TextButton(onClick = onAddToTrip) { Text("Add to trip") }
                if (row.match != null) {
                    // Add anyway is today's Confirm; Same one is the safe choice, so it's the filled one.
                    TextButton(onClick = onConfirm, enabled = row.canConfirm) { Text("Add anyway") }
                    Button(onClick = onLink) { Text("Same one") }
                } else {
                    Button(onClick = onConfirm, enabled = row.canConfirm) { Text("Confirm") }
                }
            }
        }
    }
}

/**
 * A Suggested Transfer (#143): one row for a waiting debit and credit that look like money moving
 * between the user's own Accounts, From → To, with a side still unknown to pick. Confirm Transfer
 * saves one Transfer and resolves both captures, with one Undo; Split shows them as two again.
 * TalkBack reads the row as one sentence and offers the same choices as custom actions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransferSuggestionCard(
    row: CaptureRowUi,
    fromName: String?,
    toName: String?,
    zone: ZoneId,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onConfirm: () -> Unit,
    onSplit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val credit = row.transferPartner ?: return
    val spoken = CaptureInbox.spokenTransferSuggestion(row, fromName, toName, zone)
    val actions = buildList {
        if (row.canConfirm) add(CustomAccessibilityAction("Confirm transfer") { onConfirm(); true })
        add(CustomAccessibilityAction("Split into two") { onSplit(); true })
        add(CustomAccessibilityAction(if (fromName == null) "Pick the From account" else "Change the From account") { onPickFrom(); true })
        add(CustomAccessibilityAction(if (toName == null) "Pick the To account" else "Change the To account") { onPickTo(); true })
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clearAndSetSemantics {
                        contentDescription = spoken
                        customActions = actions
                    }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Transfer between your accounts?",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatRupeesExact(row.capture.amount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.clickable(onClick = onPickFrom)) {
                        RowPill(fromName?.let { if (row.accountIsGuess) "$it?" else it } ?: "From account", missing = fromName == null, guess = row.accountIsGuess)
                    }
                    Text("→", style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.clickable(onClick = onPickTo)) {
                        RowPill(toName?.let { if (credit.accountIsGuess) "$it?" else it } ?: "To account", missing = toName == null, guess = credit.accountIsGuess)
                    }
                }
                Text(
                    "${row.bankTitle} · ${credit.bankTitle} · ${dateFormat.format(Instant.ofEpochMilli(row.capture.txnTime).atZone(zone))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onSplit) { Text("Split") }
                Button(onClick = onConfirm, enabled = row.canConfirm) { Text("Confirm Transfer") }
            }
        }
    }
}

/**
 * "Already in your Ledger?" over the Transaction this capture may repeat, drawn as the shared
 * Transaction row with its date. TalkBack reads it as one sentence.
 */
@Composable
private fun AlreadyLoggedStrip(match: ExpenseRecord, accounts: List<AccountRecord>) {
    val ledgerRow = remember(match, accounts) { Ledger.row(match, accounts) }
    val date = remember(match.date) { runCatching { LocalDate.parse(match.date) }.getOrNull() }
    val spoken = "Possibly already logged as " + listOfNotNull(
        match.description.ifBlank { null },
        match.category,
        ledgerRow.accountLabel.ifBlank { null },
        date?.format(spokenDateFormat)
    ).joinToString(", ")
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken }
    ) {
        Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
            Text(
                "Already in your Ledger?",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            // Only shows the Transaction: the card's buttons decide.
            TransactionRow(
                row = ledgerRow,
                onClick = {},
                trailingLine = date?.format(dateFormat),
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

/**
 * The card's title as a "Description" field, with Descriptions used before as suggestions. Picking
 * one only fills the title; the card's Category and Account stay as they are. Left empty, the title
 * is the bank's own text, which also stops renaming that merchant.
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
    val suggestions: List<DescriptionSuggestion> = remember(text, descriptionUses, validCategories, validAccountIds) {
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
                { Text("Leave empty for the bank's text: ${row.bankTitle}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
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

/**
 * The bulk sheet, from Confirm day or Review all: High rows start ticked, Ready rows still worth a
 * check start unticked with their guesses shown, and only the ticked rows are confirmed, as one
 * decision with one Undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkConfirmSheet(
    scopeKey: Any,
    title: String,
    sheet: List<BulkItem>,
    accountNames: Map<Long, String>,
    onConfirm: (List<CaptureRowUi>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // The rows the user changed from how they started, so a row the sheet gains meanwhile starts right.
    val toggled = remember(scopeKey) { mutableStateListOf<Long>() }
    val chosen = CaptureInbox.ticked(sheet, toggled.toSet())
    val chosenIds = chosen.map { it.capture.id }.toSet()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(
                "Confirm ${chosen.size} of ${sheet.size}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                listOfNotNull(
                    title,
                    "Rows to check start unticked: tick the ones that look right".takeIf { sheet.any { !it.tickedAtStart } }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (sheet.isEmpty()) {
                Text(
                    "Nothing left to confirm here.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
            LazyColumn(modifier = Modifier.weight(1f, fill = false).padding(vertical = 8.dp)) {
                items(sheet, key = { it.row.capture.id }) { item ->
                    val row = item.row
                    val ticked = row.capture.id in chosenIds
                    val toggle = { if (!toggled.remove(row.capture.id)) toggled.add(row.capture.id) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .toggleable(value = ticked, role = Role.Checkbox, onValueChange = { toggle() })
                    ) {
                        Checkbox(checked = ticked, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                row.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontStyle = if (row.titleIsGuess) FontStyle.Italic else FontStyle.Normal,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                listOfNotNull(
                                    if (row.isTransfer) "Transfer · ${otherSideLabel(row, accountNames[row.otherAccountId])}" else row.category,
                                    accountNames[row.accountId],
                                    "check".takeIf { !item.tickedAtStart }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            (if (row.isDebit) "−" else "+") + formatRupeesExact(row.capture.amount),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onConfirm(chosen) }, enabled = chosen.isNotEmpty()) {
                    Text("Confirm ${formatRupeesExact(chosen.sumOf { it.capture.amount })}")
                }
            }
        }
    }
}
