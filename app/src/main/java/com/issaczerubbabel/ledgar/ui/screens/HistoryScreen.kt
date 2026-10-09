package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.material3.SecondaryTabRow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.ui.components.DropdownField
import com.issaczerubbabel.ledgar.ui.components.SingleDatePickerDialog
import com.issaczerubbabel.ledgar.ui.components.TransactionRow
import com.issaczerubbabel.ledgar.ui.components.TransactionSheet
import com.issaczerubbabel.ledgar.ui.components.TransactionSheetActions
import com.issaczerubbabel.ledgar.ledger.Ledger
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.lazy.rememberLazyListState
import com.issaczerubbabel.ledgar.ui.components.DaySheet
import com.issaczerubbabel.ledgar.ledger.FilterChipUi
import com.issaczerubbabel.ledgar.ledger.FilterSection
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.InputChip
import com.issaczerubbabel.ledgar.ledger.BatchAction
import com.issaczerubbabel.ledgar.ledger.accountPickerNote
import com.issaczerubbabel.ledgar.ledger.categoryPickerNote
import com.issaczerubbabel.ledgar.ledger.sharedDate
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.issaczerubbabel.ledgar.ui.theme.*
import com.issaczerubbabel.ledgar.ledger.LedgerCalendarCell
import com.issaczerubbabel.ledgar.ledger.LedgerDay
import com.issaczerubbabel.ledgar.ledger.LedgerSummary
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.util.formatListMoney
import com.issaczerubbabel.ledgar.viewmodel.HistoryViewModel
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton

private val TABS = listOf("Daily", "Calendar", "Monthly")

/** Which batch-change dialog is open. */
private enum class BatchDialog { DATE, CATEGORY, ACCOUNT, DESCRIPTION }

// ── Main Screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    navInsets: PaddingValues,
    onNavigateToLog: () -> Unit,
    onAddOnDate: (LocalDate) -> Unit,
    onNavigateToEditTransaction: (Long) -> Unit,
    onNavigateToCopyTransaction: (Long, Boolean) -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToCaptureInbox: () -> Unit,
    onOpenTrip: (Long) -> Unit = {},
    onAddTripExpense: (Long) -> Unit = {},
    vm: HistoryViewModel = hiltViewModel(),
    captureBadgeVm: com.issaczerubbabel.ledgar.viewmodel.CaptureBadgeViewModel = hiltViewModel(),
) {
    val pendingCaptures by captureBadgeVm.pendingCount.collectAsStateWithLifecycle()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val expenseCategories by vm.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()
    val monthly by vm.year.collectAsStateWithLifecycle()
    val expandedMonths by vm.expandedMonths.collectAsStateWithLifecycle()
    val scrollTo by vm.scrollTo.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { TABS.size })
    var showMonthPicker by remember { mutableStateOf(false) }
    // The id, not a copy: the sheet follows the live Transaction and closes if it goes.
    var sheetTransactionId by remember { mutableStateOf<Long?>(null) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var batchDialog by remember { mutableStateOf<BatchDialog?>(null) }
    val filter by vm.filter.collectAsStateWithLifecycle()
    val filterChips by vm.filterChips.collectAsStateWithLifecycle()
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    val allVisibleRecords = remember(state.days) { state.days.flatMap { day -> day.rows.map { it.record } } }
    val selectedCount = vm.selectedTxIds.size
    val selectedNet = Ledger.net(vm.selectedTransactions())
    val selectedIdSet = vm.selectedTxIds.toSet()
    val pendingDelete by vm.pendingDelete.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // One snackbar per pending delete. A newer delete, or the delete becoming final, restarts this
    // effect, which cancels the snackbar showing; Undo brings the row back.
    LaunchedEffect(pendingDelete) {
        val pending = pendingDelete ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(pending.message, actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) vm.undoDelete(pending)
    }
    // Back leaves selection before it leaves the tab.
    BackHandler(enabled = selectedCount > 0) { vm.clearSelection() }
    LaunchedEffect(Unit) {
        vm.batchMessages.collect { snackbarHostState.showSnackbar(it) }
    }
    // A pending delete becomes final when the Ledger is left or the app goes to the background.
    // Rotating the phone isn't leaving: the ViewModel survives and the Undo stays open.
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current as? Activity
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true) vm.commitPendingDelete()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (activity?.isChangingConfigurations != true) vm.commitPendingDelete()
        }
    }

    // Follow settledPage, not currentPage. currentPage changes on every page an animation passes,
    // so tapping a distant tab (Daily -> Total) wrote the intermediate pages back into selectedTab,
    // which cancelled the scroll below mid-flight and left the pager stuck between pages.
    LaunchedEffect(pagerState.settledPage) {
        if (selectedTab != pagerState.settledPage) {
            selectedTab = pagerState.settledPage
        }
    }

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
        if (selectedTab != 0 && vm.isSelectionMode()) {
            vm.clearSelection()
        }
    }

    val periodLabel = when (selectedTab) {
        2 -> monthly.year.toString()
        else -> state.monthLabel
    }
    val onPrevPeriod = when (selectedTab) {
        2 -> vm::prevYear
        else -> vm::prevMonth
    }
    val onNextPeriod = when (selectedTab) {
        2 -> vm::nextYear
        else -> vm::nextMonth
    }
    val canOpenMonthPicker = selectedTab == 0 || selectedTab == 1

    Scaffold(
        topBar = {
            if (selectedCount > 0) {
                ContextualSelectionAppBar(
                    selectedCount = selectedCount,
                    selectedNet = selectedNet,
                    monthLabel = state.monthLabel,
                    onClose = vm::clearSelection,
                    onDeleteClick = { showDeleteSelectedDialog = true },
                    onSelectAll = vm::selectAllInMonth,
                    onOpenDialog = { batchDialog = it }
                )
            } else {
                LedgerTopBar(
                    periodLabel = periodLabel,
                    stepsYears = selectedTab == 2,
                    onPrevPeriod = onPrevPeriod,
                    onNextPeriod = onNextPeriod,
                    onPeriodClick = { if (canOpenMonthPicker) showMonthPicker = true },
                    periodClickable = canOpenMonthPicker,
                    onBookmarksClick = onNavigateToBookmarks,
                    onSearchClick = onNavigateToSearch,
                    isFilterActive = !filter.isEmpty,
                    filterCount = filter.activeCount,
                    onFilterClick = {
                        vm.startFilterDraft()
                        showFilterSheet = true
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = scaffoldPadding.calculateTopPadding())
                .padding(bottom = navInsets.calculateBottomPadding())
        ) {
            PeriodTabRow(selectedTab) { tabIndex ->
                selectedTab = tabIndex
            }
            if (filterChips.isNotEmpty()) {
                FilterChipsRow(chips = filterChips, onRemove = vm::removeFilter, onClear = vm::clearFilter)
            }

            ActiveTripBanner(onOpenTrip = onOpenTrip, onAddExpense = onAddTripExpense)

            // Single pinned summary row below tabs
            val pinnedSummary = when (selectedTab) {
                2 -> monthly.summary
                else -> state.summary
            }
            SummaryBar(pinnedSummary)
            // Waiting Captured transactions: a compact chip, not a full-width banner.
            Ledger.captureChipLabel(pendingCaptures)?.let { label ->
                AssistChip(
                    onClick = onNavigateToCaptureInbox,
                    label = { Text(label) },
                    leadingIcon = { Icon(Icons.Filled.Inbox, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    // Announced when the count changes, without moving focus.
                    modifier = Modifier.padding(horizontal = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

            Box(modifier = Modifier.weight(1f)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        1 -> Column {
                            // A filtered month with nothing in it says so, here as on Daily.
                            if (!filter.isEmpty && state.days.isEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "No transactions match",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = vm::clearFilter) { Text("Clear") }
                                }
                            }
                            CalendarContent(
                            cells        = state.calendar,
                            selectedDate = vm.selectedDate,
                            onDaySelect  = vm::openDay
                        )
                        }
                        2 -> MonthlyTabScreen(
                            year = monthly,
                            expanded = expandedMonths,
                            onOpenMonth = { month ->
                                vm.openMonth(month)
                                selectedTab = 0
                            },
                            onOpenWeek = { month, week ->
                                vm.openWeek(month, week)
                                selectedTab = 0
                            },
                            onToggleExpand = vm::toggleMonthExpanded,
                            modifier = Modifier.fillMaxSize()
                        )
                        else -> DailyContent(
                            days = state.days,
                            monthLabel = state.monthLabel,
                            // From an empty month: start logging in it (today in the current month, else its 1st).
                            onAdd = {
                                val today = LocalDate.now()
                                onAddOnDate(if (YearMonth.from(today) == state.month) today else state.month.atDay(1))
                            },
                            isFiltered = !filter.isEmpty,
                            onClearFilter = vm::clearFilter,
                            scrollTo = scrollTo,
                            onScrolled = vm::onScrolled,
                            selectedIds = selectedIdSet,
                            onTransactionClick = { record ->
                                if (vm.isSelectionMode()) {
                                    vm.toggleTransactionSelection(record.id)
                                } else {
                                    sheetTransactionId = record.id
                                }
                            },
                            onTransactionLongClick = { record ->
                                vm.onTransactionLongPress(record.id)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // The add FAB, lifted clear of the Undo snackbar while one shows.
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    if (!vm.isSelectionMode()) {
                        FloatingActionButton(
                            onClick         = onNavigateToLog,
                            shape           = CircleShape,
                            containerColor  = MaterialTheme.colorScheme.primary,
                            contentColor    = MaterialTheme.colorScheme.onPrimary,
                            elevation       = FloatingActionButtonDefaults.elevation(6.dp),
                            modifier        = Modifier.padding(end = 16.dp, bottom = 16.dp).size(58.dp)
                        ) { Icon(Icons.Filled.Add, contentDescription = "Add transaction", modifier = Modifier.size(28.dp)) }
                    }
                    SnackbarHost(snackbarHostState)
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthGridDialog(
            selected = state.month,
            onPick = { month ->
                vm.setMonthYear(year = month.year, month = month.monthValue)
                showMonthPicker = false
            },
            onDismiss = { showMonthPicker = false }
        )
    }

    allVisibleRecords.firstOrNull { it.id == sheetTransactionId }?.let { record ->
        TransactionSheet(
            details = Ledger.details(record, accounts),
            actions = TransactionSheetActions(
                onEdit = { onNavigateToEditTransaction(record.id) },
                onToggleBookmark = { vm.toggleBookmark(record) },
                onCopy = { useToday -> onNavigateToCopyTransaction(record.id, useToday) },
                onDelete = { vm.delete(record) }
            ),
            onDismiss = { sheetTransactionId = null }
        )
    }

    if (showFilterSheet) {
        val draft by vm.filterDraft.collectAsStateWithLifecycle()
        val options by vm.filterOptions.collectAsStateWithLifecycle()
        options?.let {
            LedgerFilterSheet(
                monthLabel = state.monthLabel,
                options = it,
                draft = draft,
                onToggleExpense = vm::toggleExpenseCategory,
                onToggleIncome = vm::toggleIncomeCategory,
                onToggleAccount = vm::toggleAccount,
                onAnyCategory = vm::clearDraftCategories,
                onAnyAccount = vm::clearDraftAccounts,
                onClear = vm::clearDraft,
                onShow = {
                    vm.applyFilterDraft()
                    showFilterSheet = false
                },
                onDismiss = { showFilterSheet = false }
            )
        }
    }

    vm.selectedDate?.let { date ->
        DaySheet(
            day = Ledger.day(state, date),
            detailsOf = { Ledger.details(it, accounts) },
            actionsFor = { record ->
                TransactionSheetActions(
                    onEdit = { onNavigateToEditTransaction(record.id) },
                    onToggleBookmark = { vm.toggleBookmark(record) },
                    onCopy = { useToday -> onNavigateToCopyTransaction(record.id, useToday) },
                    onDelete = { vm.delete(record) }
                )
            },
            onAdd = { onAddOnDate(date) },
            onDismiss = vm::closeDay
        )
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text(Ledger.deleteConfirm(vm.selectedTransactions())) },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSelectedDialog = false
                    vm.deleteSelectedTransactions()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (batchDialog == BatchDialog.DATE) {
        SingleDatePickerDialog(
            // Starts where the selection is when it all shares one date.
            initialDate = Ledger.sharedDate(vm.selectedTransactions()) ?: LocalDate.now(),
            confirmText = "Change",
            onDismiss = { batchDialog = null },
            onConfirm = {
                vm.applyBatch(BatchAction.ChangeDate(it.toString()))
                batchDialog = null
            }
        )
    }

    if (batchDialog == BatchDialog.CATEGORY) {
        val selected = vm.selectedTransactions()
        val hasExpenses = selected.any { it.type == TransactionType.EXPENSE }
        val hasIncomes = selected.any { it.type == TransactionType.INCOME }
        AlertDialog(
            onDismissRequest = { batchDialog = null },
            title = { Text("Change category") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        Ledger.categoryPickerNote(selected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Only the kinds the selection has; picking one changes the rows of that type.
                    if (hasExpenses) {
                        CategorySection("Expense categories", expenseCategories) { category ->
                            vm.applyBatch(BatchAction.ChangeCategory(TransactionType.EXPENSE, category))
                            batchDialog = null
                        }
                    }
                    if (hasIncomes) {
                        CategorySection("Income categories", incomeCategories) { category ->
                            vm.applyBatch(BatchAction.ChangeCategory(TransactionType.INCOME, category))
                            batchDialog = null
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { batchDialog = null }) { Text("Cancel") }
            }
        )
    }

    if (batchDialog == BatchDialog.ACCOUNT) {
        var chosenAccountId by remember { mutableStateOf<Long?>(null) }
        AlertDialog(
            onDismissRequest = { batchDialog = null },
            title = { Text("Change account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        Ledger.accountPickerNote(vm.selectedTransactions()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DropdownField(
                        label = "Account",
                        options = accounts.map { it.accountName },
                        selected = accounts.firstOrNull { it.id == chosenAccountId }?.accountName.orEmpty(),
                        onSelect = { name -> chosenAccountId = accounts.firstOrNull { it.accountName == name }?.id }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = chosenAccountId != null,
                    onClick = {
                        chosenAccountId?.let { vm.applyBatch(BatchAction.ChangeAccount(it)) }
                        batchDialog = null
                    }
                ) { Text("Change") }
            },
            dismissButton = {
                TextButton(onClick = { batchDialog = null }) { Text("Cancel") }
            }
        )
    }

    if (batchDialog == BatchDialog.DESCRIPTION) {
        var description by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { batchDialog = null },
            title = { Text("Change description") },
            text = {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            },
            confirmButton = {
                TextButton(
                    enabled = description.isNotBlank(),
                    onClick = {
                        vm.applyBatch(BatchAction.ChangeDescription(description.trim()))
                        batchDialog = null
                    }
                ) { Text("Change") }
            },
            dismissButton = {
                TextButton(onClick = { batchDialog = null }) { Text("Cancel") }
            }
        )
    }
}

/** One kind of Category in the Change category picker; tapping one applies it. */
@Composable
private fun CategorySection(title: String, categories: List<String>, onPick: (String) -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
    categories.forEach { category ->
        Text(
            category,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onPick(category) }
                .heightIn(min = 48.dp)
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = 8.dp)
        )
    }
}

// ── Calendar Content ──────────────────────────────────────────────────────────

@Composable
private fun CalendarContent(
    cells: List<LedgerCalendarCell>,
    selectedDate: LocalDate?,
    onDaySelect: (LocalDate) -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val dayHeaders  = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Column(modifier = Modifier.fillMaxSize()) {
        // Weekday header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            dayHeaders.forEach { day ->
                Text(
                    text      = day,
                    modifier  = Modifier.weight(1f).padding(vertical = 7.dp),
                    textAlign = TextAlign.Center,
                    fontSize  = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    // Neutral for every day: blue only ever means money in.
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = borderColor)

        if (cells.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Week by week, each week as tall as its tallest day, so large text never leaves gaps.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 96.dp)
            ) {
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        week.forEach { cell ->
                            CalendarCellView(
                                cell        = cell,
                                isSelected  = cell.date == selectedDate,
                                borderColor = borderColor,
                                onTap       = { if (cell.isInMonth) onDaySelect(cell.date) },
                                modifier    = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Calendar Cell (95dp, stacked amounts) ─────────────────────────────────────

@Composable
private fun CalendarCellView(
    cell: LedgerCalendarCell,
    isSelected: Boolean,
    borderColor: Color,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The chosen day takes the theme's container colour; weekends look like any other day.
    val selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val cellBg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent

    val dateNumColor = when {
        isSelected      -> selectedTextColor
        !cell.isInMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        else            -> MaterialTheme.colorScheme.onBackground
    }

    val normalTextColor = if (isSelected) selectedTextColor else MaterialTheme.colorScheme.onBackground
    val incomeColor     = if (isSelected) selectedTextColor else IncomeBlue
    val expenseColor    = if (isSelected) selectedTextColor else ExpenseOrange

    // The month is in the title, so the 1st is just "1".
    val dateLabel = cell.date.dayOfMonth.toString()

    val hasBoth = cell.incomeLabel != null && cell.expenseLabel != null

    // A column, not layers: with large text the date, dots and amounts stack and the cell grows
    // instead of overlapping or clipping.
    Column(
        modifier = modifier
            .heightIn(min = 95.dp)
            .border(0.5.dp, borderColor)
            .background(cellBg)
            // Days outside the month only fill the week: faded, not tappable, and skipped by TalkBack.
            .clickable(enabled = cell.isInMonth, onClick = onTap)
            .clearAndSetSemantics {
                if (cell.isInMonth) {
                    contentDescription = cell.spokenLabel
                    if (isSelected) stateDescription = "Selected"
                    role = Role.Button
                    onClick(label = "Show this day") { onTap(); true }
                }
            }
            .padding(2.dp)
    ) {
        // Date badge: a circle that grows to fit "30" at any text size.
        Box(
            modifier = Modifier
                .padding(start = 3.dp, top = 3.dp)
                .sizeIn(minWidth = 22.dp, minHeight = 22.dp)
                .then(
                    when {
                        isSelected -> Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                        cell.isToday -> Modifier
                            .clip(CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        else -> Modifier
                    }
                )
                .padding(horizontal = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dateLabel,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else dateNumColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }

        // ── Category dots ─────────────────────────────────────────────────────
        if (cell.isInMonth && cell.categories.isNotEmpty()) {
            val visibleCats = cell.categories.take(4)
            val extra       = cell.categories.size - visibleCats.size
            Row(
                modifier = Modifier.padding(start = 5.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                visibleCats.forEach { cat ->
                    Canvas(modifier = Modifier.size(6.dp)) { drawCircle(categoryDotColor(cat)) }
                }
                if (extra > 0) {
                    Text("+$extra", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.weight(1f, fill = true).heightIn(min = 2.dp))

        // ── Stacked amounts, bottom end ───────────────────────────────────────
        if (cell.isInMonth && cell.transactionCount > 0) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 3.dp, bottom = 3.dp)
            ) {
                cell.incomeLabel?.let { CalendarAmount(it, incomeColor, FontWeight.SemiBold) }
                cell.expenseLabel?.let { CalendarAmount(it, expenseColor, FontWeight.SemiBold) }
                // The Net only adds something when both sides moved.
                if (hasBoth) cell.netLabel?.let { CalendarAmount(it, normalTextColor, FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun CalendarAmount(text: String, color: Color, weight: FontWeight) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = weight,
        textAlign = TextAlign.End,
        maxLines = 1,
        softWrap = false
    )
}

/** The active filter under the tabs: one removable chip per section, and Clear. */
@Composable
private fun FilterChipsRow(chips: List<FilterChipUi>, onRemove: (FilterSection) -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Says what the numbers are narrowed to, and announces it as one phrase when it changes.
        Text(
            "Filtered by",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "Filtered by " + chips.joinToString(" and ") { it.label }
            }
        )
        chips.forEach { chip ->
            InputChip(
                selected = true,
                onClick = { onRemove(chip.section) },
                label = { Text(chip.label) },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove filter ${chip.label}", modifier = Modifier.size(18.dp)) }
            )
        }
        TextButton(onClick = onClear) { Text("Clear") }
    }
}

// ── Daily List Content ────────────────────────────────────────────────────────

@Composable
private fun DailyContent(
    days: List<LedgerDay>,
    monthLabel: String,
    onAdd: () -> Unit,
    isFiltered: Boolean,
    onClearFilter: () -> Unit,
    scrollTo: LocalDate?,
    onScrolled: () -> Unit,
    selectedIds: Set<Long>,
    onTransactionClick: (ExpenseRecord) -> Unit,
    onTransactionLongClick: (ExpenseRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    if (days.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (isFiltered) "No transactions match" else "No transactions in $monthLabel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
                if (isFiltered) {
                    TextButton(onClick = onClearFilter) { Text("Clear") }
                } else {
                    // Start logging from an empty month.
                    FilledTonalButton(onClick = onAdd, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add")
                    }
                }
            }
        }
    } else {
        val listState = rememberLazyListState()
        // A week tapped on Monthly: once its month has loaded, bring its day to the top.
        LaunchedEffect(scrollTo, days) {
            val target = scrollTo ?: return@LaunchedEffect
            val dayIndex = days.indexOfFirst { it.date == target }
            if (dayIndex < 0) return@LaunchedEffect
            listState.scrollToItem(days.take(dayIndex).sumOf { 1 + it.rows.size })
            onScrolled()
        }
        LazyColumn(state = listState, modifier = modifier, contentPadding = PaddingValues(bottom = 96.dp)) {
            days.forEach { day ->
                item(key = day.date.toString()) { DayHeader(day) }
                itemsIndexed(day.rows, key = { _, row -> row.id }) { index, row ->
                    TransactionRow(
                        row = row,
                        isSelected = selectedIds.contains(row.id),
                        selectionMode = selectedIds.isNotEmpty(),
                        onClick = { onTransactionClick(row.record) },
                        onLongClick = { onTransactionLongClick(row.record) }
                    )
                    if (index < day.rows.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                            modifier  = Modifier.padding(start = 80.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── App Bar ───────────────────────────────────────────────────────────────────

/**
 * The Ledger's top bar: ‹ month › as the title (tap it for the month grid), Search and Filter, and an
 * overflow menu with Bookmarks. Theme surface colours in both modes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerTopBar(
    periodLabel: String,
    stepsYears: Boolean,
    onPrevPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onPeriodClick: () -> Unit,
    periodClickable: Boolean,
    onBookmarksClick: () -> Unit,
    onSearchClick: () -> Unit,
    isFilterActive: Boolean,
    filterCount: Int,
    onFilterClick: () -> Unit
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    val unit = if (stepsYears) "year" else "month"
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevPeriod) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous $unit")
                }
                Text(
                    text = periodLabel,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = periodClickable, onClickLabel = "Choose month", onClick = onPeriodClick)
                        .heightIn(min = 48.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .padding(horizontal = 6.dp),
                    maxLines = 1
                )
                IconButton(onClick = onNextPeriod) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next $unit")
                }
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) { Icon(Icons.Filled.Search, contentDescription = "Search") }
            IconButton(onClick = onFilterClick) {
                // Marked while a filter is on, so filtered numbers are never mistaken for the month's.
                BadgedBox(badge = { if (isFilterActive) Badge { Text(filterCount.toString()) } }) {
                    Icon(Icons.Filled.Tune, contentDescription = if (isFilterActive) "Filter, $filterCount active" else "Filter")
                }
            }
            Box {
                IconButton(onClick = { isMenuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Bookmarks") },
                        leadingIcon = { Icon(Icons.Filled.StarBorder, contentDescription = null) },
                        onClick = { isMenuExpanded = false; onBookmarksClick() }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

/** Tap the month title: 12 months with a year stepper. A tap opens the month; later ones are greyed but allowed. */
@Composable
private fun MonthGridDialog(selected: YearMonth, onPick: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    var year by rememberSaveable { mutableIntStateOf(selected.year) }
    val months = Ledger.monthGrid(year, selected, LocalDate.now())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { year -= 1 }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous year") }
                Text(year.toString(), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { year += 1 }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next year") }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                months.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { choice ->
                            val container = if (choice.isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            val content = when {
                                choice.isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                choice.isFuture -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(container)
                                    .clickable { onPick(choice.month) }
                            ) {
                                Text(
                                    choice.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = content,
                                    fontWeight = if (choice.isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextualSelectionAppBar(
    selectedCount: Int,
    selectedNet: Double,
    monthLabel: String,
    onClose: () -> Unit,
    onDeleteClick: () -> Unit,
    onSelectAll: () -> Unit,
    onOpenDialog: (BatchDialog) -> Unit
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Leave selection")
            }
        },
        title = {
            Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(text = "$selectedCount selected", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Net ${formatListMoney(selectedNet, signed = true)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
            }
            Box {
                IconButton(onClick = { isMenuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More for the selection")
                }
                DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Select all in $monthLabel") },
                        onClick = { isMenuExpanded = false; onSelectAll() }
                    )
                    HorizontalDivider()
                    listOf(
                        "Change date…" to BatchDialog.DATE,
                        "Change category…" to BatchDialog.CATEGORY,
                        "Change account…" to BatchDialog.ACCOUNT,
                        "Change description…" to BatchDialog.DESCRIPTION
                    ).forEach { (label, dialog) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { isMenuExpanded = false; onOpenDialog(dialog) }
                        )
                    }
                }
            }
        }
    )
}

// ── Period Tabs ───────────────────────────────────────────────────────────────

@Composable
private fun PeriodTabRow(selected: Int, onSelect: (Int) -> Unit) {
    SecondaryTabRow(
        selectedTabIndex = selected,
        containerColor   = MaterialTheme.colorScheme.surface,
        contentColor     = MaterialTheme.colorScheme.onSurface,
        indicator = {
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selected),
                color    = MaterialTheme.colorScheme.primary,
                height   = 2.5.dp
            )
        },
        divider = {}
    ) {
        TABS.forEachIndexed { idx, label ->
            Tab(
                selected = selected == idx,
                onClick  = { onSelect(idx) },
                text = {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected == idx) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected == idx) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}

// ── Summary Bar ───────────────────────────────────────────────────────────────

@Composable
private fun SummaryBar(summary: LedgerSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        SummaryColumn("Income",   summary.income,   IncomeBlue,    Modifier.weight(1f))
        SummaryColumn("Expenses", summary.expenses, ExpenseOrange, Modifier.weight(1f))
        SummaryColumn("Net",      summary.net,      MaterialTheme.colorScheme.onBackground, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryColumn(label: String, amount: String, color: Color, modifier: Modifier) {
    // One stop per column: "Income ₹12,000".
    Column(modifier = modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Never cut short: a big amount wraps rather than losing digits.
        Text(
            amount,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            textAlign = TextAlign.Center
        )
    }
}

// ── Day Header ──────────────────────────────────────────────────────────────

@Composable
private fun DayHeader(day: LedgerDay) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            // A heading TalkBack can jump between: "Friday 9 October, Expenses ₹212".
            .clearAndSetSemantics {
                heading()
                contentDescription = day.spokenLabel
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = day.dayNumber,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = day.weekday,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        // Only the sides that moved, each in full.
        day.income?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = IncomeBlue,
                textAlign = TextAlign.End
            )
        }
        if (day.income != null && day.expense != null) Spacer(Modifier.width(8.dp))
        day.expense?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = ExpenseOrange,
                textAlign = TextAlign.End
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp
    )
}
