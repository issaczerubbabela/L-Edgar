package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.ledger.DatePreset
import com.issaczerubbabel.ledgar.ledger.Ledger
import com.issaczerubbabel.ledgar.ledger.RecentCategory
import com.issaczerubbabel.ledgar.ledger.SearchChip
import com.issaczerubbabel.ledgar.ledger.SearchFilter
import com.issaczerubbabel.ledgar.ledger.SearchFilterKind
import com.issaczerubbabel.ledgar.ledger.SearchTotals
import com.issaczerubbabel.ledgar.ledger.dateRangeLabel
import com.issaczerubbabel.ledgar.ledger.searchDays
import com.issaczerubbabel.ledgar.ui.components.DateRangePickerDialog
import com.issaczerubbabel.ledgar.ui.components.TransactionRow
import com.issaczerubbabel.ledgar.ui.components.TransactionSheetHost
import com.issaczerubbabel.ledgar.ui.components.UndoSnackbar
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.util.amountToInput
import com.issaczerubbabel.ledgar.util.formatRupees
import com.issaczerubbabel.ledgar.util.parseAmountInput
import com.issaczerubbabel.ledgar.viewmodel.SearchViewModel
import com.issaczerubbabel.ledgar.viewmodel.TransactionActionsViewModel
import java.time.LocalDate

/** Which chip's picker is open. */
private enum class SearchPicker { DATE, PICKS, AMOUNT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    onCopyTransaction: (id: Long, useToday: Boolean) -> Unit,
    vm: SearchViewModel = hiltViewModel(),
    actionsVm: TransactionActionsViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val typedQuery by vm.typedQuery.collectAsStateWithLifecycle()
    val accounts by actionsVm.accounts.collectAsStateWithLifecycle()
    val filterOptions by vm.filterOptions.collectAsStateWithLifecycle()
    val picksDraft by vm.picksDraft.collectAsStateWithLifecycle()
    val days = remember(state.results, accounts) { Ledger.searchDays(state.results, accounts) }
    var sheetTransactionId by remember { mutableStateOf<Long?>(null) }
    var picker by rememberSaveable { mutableStateOf<SearchPicker?>(null) }
    // Opening a row drops the search box's focus, or the keyboard comes back over the Undo snackbar.
    val focusManager = LocalFocusManager.current
    val pendingDelete by actionsVm.pendingDelete.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    UndoSnackbar(pendingDelete, snackbarHostState, onUndo = actionsVm::undoDelete, onCommit = actionsVm::commitPendingDelete)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.padding(bottom = innerPadding.calculateBottomPadding())) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    OutlinedTextField(
                        value = typedQuery,
                        onValueChange = vm::onQueryChange,
                        singleLine = true,
                        placeholder = { Text("Notes, categories, accounts, amounts", maxLines = 1) },
                        trailingIcon = if (typedQuery.isNotEmpty()) {
                            { IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") } }
                        } else null,
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp)
                    )
                }
            )
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = scaffoldPadding.calculateTopPadding(), bottom = innerPadding.calculateBottomPadding())
        ) {
            FilterChipRow(
                chips = state.chips,
                activeCount = state.filter.activeCount,
                type = state.filter.type,
                onTypeChange = vm::setType,
                onOpen = { kind ->
                    focusManager.clearFocus()
                    picker = when (kind) {
                        SearchFilterKind.TYPE -> null
                        SearchFilterKind.DATE -> SearchPicker.DATE
                        SearchFilterKind.CATEGORY, SearchFilterKind.ACCOUNT -> {
                            vm.startPicksDraft()
                            SearchPicker.PICKS
                        }
                        SearchFilterKind.AMOUNT -> SearchPicker.AMOUNT
                    }
                },
                onClear = vm::clear,
                onClearAll = vm::clearAll
            )

            state.totals?.takeIf { it.count > 0 }?.let { TotalsLine(it) }

            when {
                !state.isActive -> EmptyHint(
                    recentCategories = state.recentCategories,
                    onCategory = vm::filterByCategory
                )
                state.results.isEmpty() -> NoResults(chips = state.chips.filter { it.isOn }, onClear = vm::clear, onClearAll = vm::clearAll)
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    days.forEach { day ->
                        item(key = "day-${day.header}") { SearchDayHeader(day.header) }
                        items(day.rows, key = { it.id }) { row ->
                            TransactionRow(
                                row = row,
                                onClick = { focusManager.clearFocus(); sheetTransactionId = row.id },
                                highlight = state.query
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    when (picker) {
        SearchPicker.DATE -> DateSheet(
            presets = state.datePresets,
            filter = state.filter,
            onPick = { start, end ->
                vm.setDateRange(start, end)
                picker = null
            },
            onDismiss = { picker = null }
        )
        SearchPicker.PICKS -> filterOptions?.let { options ->
            LedgerFilterSheet(
                title = "Filter search",
                options = options,
                draft = picksDraft,
                onToggleExpense = vm::toggleExpenseCategory,
                onToggleIncome = vm::toggleIncomeCategory,
                onToggleAccount = vm::toggleAccount,
                onAnyCategory = vm::clearDraftCategories,
                onAnyAccount = vm::clearDraftAccounts,
                onClear = vm::clearDraft,
                onShow = {
                    vm.applyPicksDraft()
                    picker = null
                },
                onDismiss = { picker = null },
                unusedLabel = "Not in these results",
                showExpenseCategories = state.filter.type == null || state.filter.type == TransactionType.EXPENSE,
                showIncomeCategories = state.filter.type == null || state.filter.type == TransactionType.INCOME
            )
        }
        SearchPicker.AMOUNT -> AmountSheet(
            filter = state.filter,
            onApply = { min, max ->
                vm.setAmountRange(min, max)
                picker = null
            },
            onDismiss = { picker = null }
        )
        null -> Unit
    }

    state.results.firstOrNull { it.id == sheetTransactionId }?.let { record ->
        TransactionSheetHost(
            transaction = record,
            onDismiss = { sheetTransactionId = null },
            onEdit = onEditTransaction,
            onCopy = onCopyTransaction,
            vm = actionsVm
        )
    }
}

/**
 * Type · Date · Category · Account · Amount, always showing. A chip that's on shows its value and a ✕;
 * "Clear all" appears once two or more are on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipRow(
    chips: List<SearchChip>,
    activeCount: Int,
    type: String?,
    onTypeChange: (String?) -> Unit,
    onOpen: (SearchFilterKind) -> Unit,
    onClear: (SearchFilterKind) -> Unit,
    onClearAll: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(chips, key = { it.kind.name }) { chip ->
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                FilterChip(
                    selected = chip.isOn,
                    onClick = { if (chip.kind == SearchFilterKind.TYPE) menuOpen = true else onOpen(chip.kind) },
                    label = { Text(chip.value ?: chip.kind.label, maxLines = 1) },
                    trailingIcon = {
                        if (chip.isOn) {
                            // Its own tap target: the chip itself still opens the picker to change it.
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear ${chip.kind.label.lowercase()} filter",
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { onClear(chip.kind) }
                            )
                        } else {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = "${chip.kind.label} filter, ${chip.value ?: "any"}"
                        }
                )
                if (chip.kind == SearchFilterKind.TYPE) {
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        listOf(null, TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER).forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option ?: "All types") },
                                onClick = {
                                    menuOpen = false
                                    onTypeChange(option)
                                },
                                trailingIcon = if (option == type) {
                                    { Icon(Icons.Filled.Check, contentDescription = "Selected") }
                                } else null
                            )
                        }
                    }
                }
            }
        }
        if (activeCount >= 2) {
            item(key = "clear-all") { TextButton(onClick = onClearAll) { Text("Clear all") } }
        }
    }
}

/** "4 transactions · Spent ₹2,310 · Earned ₹52,000", read out as it changes. */
@Composable
private fun TotalsLine(totals: SearchTotals) {
    val parts = listOfNotNull(
        if (totals.count == 1) "1 transaction" else "${totals.count} transactions",
        totals.spent.takeIf { it != 0.0 }?.let { "Spent ${formatRupees(it)}" },
        totals.earned.takeIf { it != 0.0 }?.let { "Earned ${formatRupees(it)}" }
    )
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EmptyHint(recentCategories: List<RecentCategory>, onCategory: (RecentCategory) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Type a note, a Category, an Account or an amount like 1,499, or use the chips above.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (recentCategories.isNotEmpty()) {
            Text("Recent categories", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recentCategories.forEach { category ->
                    SuggestionChip(onClick = { onCategory(category) }, label = { Text(category.name) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }
    }
}

/** No results, with each chip that's on listed so a leftover filter is obvious and one tap away. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NoResults(chips: List<SearchChip>, onClear: (SearchFilterKind) -> Unit, onClearAll: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("No transactions match", style = MaterialTheme.typography.titleMedium)
        if (chips.isNotEmpty()) {
            Text("Filters on:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { chip ->
                    InputChip(
                        selected = true,
                        onClick = { onClear(chip.kind) },
                        label = { Text("${chip.kind.label}: ${chip.value}") },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
            if (chips.size >= 2) TextButton(onClick = onClearAll) { Text("Clear all") }
        }
    }
}

/** The Date chip: a preset, or any range from the calendar. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DateSheet(
    presets: List<DatePreset>,
    filter: SearchFilter,
    onPick: (LocalDate?, LocalDate?) -> Unit,
    onDismiss: () -> Unit
) {
    var showCalendar by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Date", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = filter.start == preset.start && filter.end == preset.end,
                        onClick = { onPick(preset.start, preset.end) },
                        label = { Text(preset.label) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
            OutlinedButton(onClick = { showCalendar = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (filter.hasDate) Ledger.dateRangeLabel(filter.start, filter.end) else "Pick dates")
            }
            if (filter.hasDate) TextButton(onClick = { onPick(null, null) }) { Text("Any time") }
        }
    }
    if (showCalendar) {
        val today = LocalDate.now()
        DateRangePickerDialog(
            initialStart = filter.start ?: today.withDayOfMonth(1),
            initialEnd = filter.end ?: today,
            onDismiss = { showCalendar = false },
            onConfirm = { start, end ->
                showCalendar = false
                onPick(start, end)
            }
        )
    }
}

/** The Amount chip: a minimum, a maximum, or both. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountSheet(filter: SearchFilter, onApply: (Double?, Double?) -> Unit, onDismiss: () -> Unit) {
    var min by rememberSaveable { mutableStateOf(filter.minAmount?.let(::amountToInput).orEmpty()) }
    var max by rememberSaveable { mutableStateOf(filter.maxAmount?.let(::amountToInput).orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Amount", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = min,
                    onValueChange = { min = it },
                    label = { Text("At least") },
                    prefix = { Text("₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = max,
                    onValueChange = { max = it },
                    label = { Text("At most") },
                    prefix = { Text("₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Button(onClick = { onApply(parseAmountInput(min), parseAmountInput(max)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Show")
            }
        }
    }
}

/** "Fri, 9 Oct 2026": a date in Search's results, with its month since they span all time. */
@Composable
private fun SearchDayHeader(header: String) {
    Text(
        header,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
