package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.material3.SecondaryTabRow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.ui.components.DropdownField
import com.issaczerubbabel.ledgar.ui.components.SingleDatePickerDialog
import com.issaczerubbabel.ledgar.ui.theme.*
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.ledger.LedgerCalendarCell
import com.issaczerubbabel.ledgar.ledger.LedgerDay
import com.issaczerubbabel.ledgar.ledger.LedgerRow
import com.issaczerubbabel.ledgar.ledger.LedgerSummary
import com.issaczerubbabel.ledgar.util.formatListMoney
import com.issaczerubbabel.ledgar.viewmodel.HistoryViewModel
import com.issaczerubbabel.ledgar.viewmodel.MonthlyViewModel
import com.issaczerubbabel.ledgar.viewmodel.PeriodSummary
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.launch

private val TABS = listOf("Daily", "Calendar", "Monthly")
private val monthNames = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

@Composable
private fun responsiveTextSize(baseSp: Float, minSp: Float = 12f, maxSp: Float = 20f) =
    (
        baseSp * (LocalConfiguration.current.screenWidthDp / 411f).coerceIn(0.9f, 1.08f)
    ).coerceIn(minSp, maxSp).sp

private enum class BatchAction {
    EDIT_DATES,
    EDIT_CATEGORIES,
    EDIT_ASSETS,
    EDIT_DESCRIPTIONS
}

// ── Main Screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    navInsets: PaddingValues,
    onNavigateToLog: () -> Unit,
    onNavigateToEditTransaction: (Long) -> Unit,
    onNavigateToCopyTransaction: (Long, Boolean) -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToFilterSelection: () -> Unit,
    onNavigateToCaptureInbox: () -> Unit,
    onOpenTrip: (Long) -> Unit = {},
    onAddTripExpense: (Long) -> Unit = {},
    vm: HistoryViewModel = hiltViewModel(),
    monthlyVm: MonthlyViewModel = hiltViewModel(),
    captureBadgeVm: com.issaczerubbabel.ledgar.viewmodel.CaptureBadgeViewModel = hiltViewModel(),
) {
    val pendingCaptures by captureBadgeVm.pendingCount.collectAsStateWithLifecycle()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val monthlyState by monthlyVm.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { TABS.size })
    var showMonthPicker by remember { mutableStateOf(false) }
    var pickerMonth by remember(state.month) { mutableIntStateOf(state.month.monthValue) }
    var pickerYear by remember(state.month) { mutableIntStateOf(state.month.year) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScope = rememberCoroutineScope()
    var selectedTransaction by remember { mutableStateOf<ExpenseRecord?>(null) }
    var pendingCopyTransaction by remember { mutableStateOf<ExpenseRecord?>(null) }
    var showCopyDateDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var isBatchMenuExpanded by remember { mutableStateOf(false) }
    var pendingBatchAction by remember { mutableStateOf<BatchAction?>(null) }
    var selectedCategory by remember { mutableStateOf("") }
    var selectedAssetId by remember { mutableStateOf<Long?>(null) }
    var updatedDescription by remember { mutableStateOf("") }
    val allVisibleRecords = remember(state.days) { state.days.flatMap { day -> day.rows.map { it.record } } }
    val selectedCount = vm.selectedTxIds.size
    val selectedSum = vm.selectedSum(allVisibleRecords)
    val selectedIdSet = vm.selectedTxIds.toSet()

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

    // ── Theme detection ────────────────────────────────────────────────────────
    // luminance() > 0.5 → light theme (white background)
    val isLight    = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val headerBg   = if (isLight) HeaderGreen else MaterialTheme.colorScheme.background
    val headerText = Color.White   // always white on header (green or black)

    val periodLabel = when (selectedTab) {
        2 -> monthlyState.selectedYear.toString()
        else -> state.monthLabel
    }
    val onPrevPeriod = when (selectedTab) {
        2 -> monthlyVm::prevYear
        else -> vm::prevMonth
    }
    val onNextPeriod = when (selectedTab) {
        2 -> monthlyVm::nextYear
        else -> vm::nextMonth
    }
    val canOpenMonthPicker = selectedTab == 0 || selectedTab == 1

    Scaffold(
        topBar = {
            if (selectedCount > 0) {
                ContextualSelectionAppBar(
                    selectedCount = selectedCount,
                    selectedSum = selectedSum,
                    onDeleteClick = { showDeleteSelectedDialog = true },
                    isMenuExpanded = isBatchMenuExpanded,
                    onMenuExpandedChange = { isBatchMenuExpanded = it },
                    onSelectBatchAction = { action ->
                        pendingBatchAction = action
                        isBatchMenuExpanded = false
                    }
                )
            } else {
                MoneyManagerAppBar(
                    bg = headerBg,
                    contentColor = headerText,
                    periodLabel = periodLabel,
                    onPrevPeriod = onPrevPeriod,
                    onNextPeriod = onNextPeriod,
                    onPeriodClick = { if (canOpenMonthPicker) showMonthPicker = true },
                    periodClickable = canOpenMonthPicker,
                    onBookmarksClick = onNavigateToBookmarks,
                    onSearchClick = onNavigateToSearch,
                    onFilterClick = onNavigateToFilterSelection
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
            PeriodTabRow(selectedTab, headerBg, headerText) { tabIndex ->
                selectedTab = tabIndex
            }

            if (pendingCaptures > 0) {
                CaptureBanner(count = pendingCaptures, onClick = onNavigateToCaptureInbox)
            }
            ActiveTripBanner(onOpenTrip = onOpenTrip, onAddExpense = onAddTripExpense)

            // Single pinned summary row below tabs
            val pinnedSummary = when (selectedTab) {
                2 -> monthlyState.summary.toLedgerSummary()
                else -> state.summary
            }
            SummaryBar(pinnedSummary)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

            Box(modifier = Modifier.weight(1f)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        1 -> CalendarContent(
                            cells        = state.calendar,
                            selectedDate = vm.selectedDate,
                            isLight      = isLight,
                            onDaySelect  = vm::selectDate
                        )
                        2 -> MonthlyTabScreen(
                            monthGroups = monthlyState.monthGroups,
                            onToggleExpand = monthlyVm::toggleMonthExpanded,
                            modifier = Modifier.fillMaxSize()
                        )
                        else -> DailyContent(
                            days = state.days,
                            selectedIds = selectedIdSet,
                            onTransactionClick = { record ->
                                if (vm.isSelectionMode()) {
                                    vm.toggleTransactionSelection(record.id)
                                } else {
                                    selectedTransaction = record
                                }
                            },
                            onTransactionLongClick = { record ->
                                vm.onTransactionLongPress(record.id)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Primary add-transaction FAB
                if (!vm.isSelectionMode()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FloatingActionButton(
                            onClick         = onNavigateToLog,
                            shape           = CircleShape,
                            containerColor  = MaterialTheme.colorScheme.primary,
                            contentColor    = MaterialTheme.colorScheme.onPrimary,
                            elevation       = FloatingActionButtonDefaults.elevation(6.dp),
                            modifier        = Modifier.size(58.dp)
                        ) { Icon(Icons.Filled.Add, null, modifier = Modifier.size(28.dp)) }
                    }
                }
            }
        }
    }

    if (showMonthPicker) {
        AlertDialog(
            onDismissRequest = { showMonthPicker = false },
            title = { Text("Select Month") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DropdownField(
                        label = "Month",
                        options = monthNames,
                        selected = monthNames[pickerMonth - 1],
                        onSelect = { selected ->
                            pickerMonth = monthNames.indexOf(selected) + 1
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { pickerYear -= 1 }) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous year")
                        }
                        Text(pickerYear.toString(), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { pickerYear += 1 }) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Next year")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setMonthYear(year = pickerYear, month = pickerMonth)
                    showMonthPicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMonthPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedTransaction?.let { record ->
        ModalBottomSheet(
            onDismissRequest = { selectedTransaction = null },
            sheetState = bottomSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = record.description.ifBlank { record.category },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                TextButton(
                    onClick = {
                        selectedTransaction = null
                        onNavigateToEditTransaction(record.id)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                }

                TextButton(
                    onClick = {
                        vm.toggleBookmark(record)
                        selectedTransaction = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Toggle Bookmark",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                }

                TextButton(
                    onClick = {
                        selectedTransaction = null
                        pendingCopyTransaction = record
                        showCopyDateDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copy", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                }

                TextButton(
                    onClick = {
                        vm.delete(record)
                        sheetScope.launch {
                            bottomSheetState.hide()
                            selectedTransaction = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    if (showCopyDateDialog) {
        val record = pendingCopyTransaction
        AlertDialog(
            onDismissRequest = {
                showCopyDateDialog = false
                pendingCopyTransaction = null
            },
            title = { Text("Which date to use?") },
            text = { Text("Choose whether the copied transaction keeps its original date or uses today.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        record?.let { onNavigateToCopyTransaction(it.id, false) }
                        showCopyDateDialog = false
                        pendingCopyTransaction = null
                    }
                ) {
                    Text("Original Date")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            record?.let { onNavigateToCopyTransaction(it.id, true) }
                            showCopyDateDialog = false
                            pendingCopyTransaction = null
                        }
                    ) {
                        Text("Today")
                    }
                    TextButton(
                        onClick = {
                            showCopyDateDialog = false
                            pendingCopyTransaction = null
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text("Delete selected transactions?") },
            text = { Text("This action will remove all selected transactions.") },
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

    if (pendingBatchAction == BatchAction.EDIT_DATES) {
        SingleDatePickerDialog(
            initialDate = LocalDate.now(),
            confirmText = "Update",
            onDismiss = { pendingBatchAction = null },
            onConfirm = {
                vm.updateSelectedDates(it.toString())
                pendingBatchAction = null
            }
        )
    }

    if (pendingBatchAction == BatchAction.EDIT_CATEGORIES) {
        AlertDialog(
            onDismissRequest = { pendingBatchAction = null },
            title = { Text("Edit All Categories") },
            text = {
                DropdownField(
                    label = "Category",
                    options = categories,
                    selected = selectedCategory,
                    onSelect = { selectedCategory = it }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateSelectedCategories(selectedCategory)
                    selectedCategory = ""
                    pendingBatchAction = null
                }) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedCategory = ""
                    pendingBatchAction = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (pendingBatchAction == BatchAction.EDIT_ASSETS) {
        val accountNames = accounts.map { it.accountName }
        val selectedAssetName = accounts.firstOrNull { it.id == selectedAssetId }?.accountName.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingBatchAction = null },
            title = { Text("Edit All Assets") },
            text = {
                DropdownField(
                    label = "Asset Account",
                    options = accountNames,
                    selected = selectedAssetName,
                    onSelect = { selectedName ->
                        selectedAssetId = accounts.firstOrNull { it.accountName == selectedName }?.id
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedAssetId?.let(vm::updateSelectedAssets)
                    selectedAssetId = null
                    pendingBatchAction = null
                }) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedAssetId = null
                    pendingBatchAction = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (pendingBatchAction == BatchAction.EDIT_DESCRIPTIONS) {
        AlertDialog(
            onDismissRequest = { pendingBatchAction = null },
            title = { Text("Edit All Descriptions") },
            text = {
                OutlinedTextField(
                    value = updatedDescription,
                    onValueChange = { updatedDescription = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateSelectedDescriptions(updatedDescription)
                    updatedDescription = ""
                    pendingBatchAction = null
                }) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    updatedDescription = ""
                    pendingBatchAction = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── Calendar Content ──────────────────────────────────────────────────────────

@Composable
private fun CalendarContent(
    cells: List<LedgerCalendarCell>,
    selectedDate: LocalDate?,
    isLight: Boolean,
    onDaySelect: (LocalDate) -> Unit
) {
    val borderColor = if (isLight) Color(0xFFE0E0E0) else Color(0xFF333333)
    val dayHeaders  = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Column(modifier = Modifier.fillMaxSize()) {
        // Weekday header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            dayHeaders.forEachIndexed { idx, day ->
                Text(
                    text      = day,
                    modifier  = Modifier.weight(1f).padding(vertical = 7.dp),
                    textAlign = TextAlign.Center,
                    fontSize  = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (idx) {
                        0    -> Color(0xFFEF5350)
                        6    -> IncomeBlue
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        HorizontalDivider(color = borderColor)

        if (cells.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(cells, key = { it.date.toString() }) { cell ->
                    CalendarCellView(
                        cell        = cell,
                        isSelected  = cell.date == selectedDate,
                        isLight     = isLight,
                        borderColor = borderColor,
                        onTap       = { if (cell.isInMonth) onDaySelect(cell.date) }
                    )
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
    isLight: Boolean,
    borderColor: Color,
    onTap: () -> Unit
) {
    // ── Colours based on selection + theme ────────────────────────────────────
    val selectedBg        = if (isLight) SelectedNavy else Color.White
    val selectedTextColor = if (isLight) Color.White   else Color.Black

    val cellBg = if (isSelected) selectedBg else Color.Transparent

    val isSunday   = cell.date.dayOfWeek == DayOfWeek.SUNDAY
    val isSaturday = cell.date.dayOfWeek == DayOfWeek.SATURDAY

    val dateNumColor = when {
        isSelected           -> selectedTextColor
        !cell.isInMonth      -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
        isSunday             -> Color(0xFFEF5350)
        isSaturday           -> IncomeBlue
        else                 -> MaterialTheme.colorScheme.onBackground
    }

    val normalTextColor = if (isSelected) selectedTextColor else MaterialTheme.colorScheme.onBackground
    val incomeColor     = if (isSelected) selectedTextColor else IncomeBlue
    val expenseColor    = if (isSelected) selectedTextColor else ExpenseOrange

    // The month is in the title, so the 1st is just "1".
    val dateLabel = cell.date.dayOfMonth.toString()

    val hasBoth = cell.incomeLabel != null && cell.expenseLabel != null

    Box(
        modifier = Modifier
            .height(95.dp)
            .border(0.5.dp, borderColor)
            .background(cellBg)
            .clickable(onClick = onTap)
            .padding(2.dp)
    ) {
        // Date badge — centered text for selected/today indicators.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 3.dp, top = 3.dp)
                .size(20.dp)
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
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dateLabel,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else dateNumColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        // ── Category dots — centre ────────────────────────────────────────────
        if (cell.isInMonth && cell.categories.isNotEmpty()) {
            val visibleCats = cell.categories.take(4)
            val extra       = cell.categories.size - visibleCats.size
            Row(
                // Under the date, clear of up to three stacked amounts.
                modifier = Modifier.align(Alignment.TopStart).padding(start = 5.dp, top = 28.dp),
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

        // ── Stacked amounts — bottom end ──────────────────────────────────────
        if (cell.isInMonth && cell.transactionCount > 0) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
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

// ── Daily List Content ────────────────────────────────────────────────────────

@Composable
private fun DailyContent(
    days: List<LedgerDay>,
    selectedIds: Set<Long>,
    onTransactionClick: (ExpenseRecord) -> Unit,
    onTransactionLongClick: (ExpenseRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    if (days.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No transactions this month",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 96.dp)) {
            days.forEach { day ->
                item(key = day.date.toString()) { DayHeader(day) }
                itemsIndexed(day.rows, key = { _, row -> row.id }) { index, row ->
                    TransactionRow(
                        row = row,
                        isSelected = selectedIds.contains(row.id),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoneyManagerAppBar(
    bg: Color,
    contentColor: Color,
    periodLabel: String,
    onPrevPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onPeriodClick: () -> Unit,
    periodClickable: Boolean,
    onBookmarksClick: () -> Unit,
    onSearchClick: () -> Unit,
    onFilterClick: () -> Unit
) {
    val compactDevice = LocalConfiguration.current.screenWidthDp < 360
    val appTitle = if (compactDevice) "l.edgar" else "l.edgar's"
    val navButtonSize = if (compactDevice) 28.dp else 30.dp

    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = appTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = onPrevPeriod, modifier = Modifier.size(navButtonSize)) {
                    Icon(Icons.Filled.ChevronLeft, null, tint = contentColor)
                }
                Text(
                    text = periodLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = contentColor,
                    modifier = if (periodClickable) Modifier
                        .widthIn(min = 68.dp, max = 110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onPeriodClick)
                        .padding(horizontal = 6.dp, vertical = 4.dp) else Modifier.widthIn(min = 68.dp, max = 110.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
                IconButton(onClick = onNextPeriod, modifier = Modifier.size(navButtonSize)) {
                    Icon(Icons.Filled.ChevronRight, null, tint = contentColor)
                }
            }
        },
        actions = {
            IconButton(onClick = onBookmarksClick) { Icon(Icons.Filled.StarBorder, null, tint = contentColor) }
            IconButton(onClick = onSearchClick) { Icon(Icons.Filled.Search, null, tint = contentColor) }
            IconButton(onClick = onFilterClick) { Icon(Icons.Filled.Tune, null, tint = contentColor) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = bg)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextualSelectionAppBar(
    selectedCount: Int,
    selectedSum: Double,
    onDeleteClick: () -> Unit,
    isMenuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onSelectBatchAction: (BatchAction) -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "$selectedCount selected",
                fontWeight = FontWeight.SemiBold
            )
        },
        actions = {
            Text(
                text = formatListMoney(selectedSum, signed = true),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 6.dp)
            )
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
            }
            Box {
                IconButton(onClick = { onMenuExpandedChange(true) }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Batch actions")
                }
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit All Dates") },
                        onClick = { onSelectBatchAction(BatchAction.EDIT_DATES) }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit All Categories") },
                        onClick = { onSelectBatchAction(BatchAction.EDIT_CATEGORIES) }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit All Assets") },
                        onClick = { onSelectBatchAction(BatchAction.EDIT_ASSETS) }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit All Descriptions") },
                        onClick = { onSelectBatchAction(BatchAction.EDIT_DESCRIPTIONS) }
                    )
                }
            }
        }
    )
}

// ── Period Tabs ───────────────────────────────────────────────────────────────

@Composable
private fun PeriodTabRow(
    selected: Int, bg: Color, textColor: Color,
    onSelect: (Int) -> Unit
) {
    SecondaryTabRow(
        selectedTabIndex = selected,
        containerColor   = bg,
        contentColor     = textColor,
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
                        fontSize   = responsiveTextSize(baseSp = 14f, minSp = 13f, maxSp = 15f),
                        color      = if (selected == idx) textColor else textColor.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
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

/** Monthly keeps its own sums until Ledger 8/10 (#77) moves it onto the Ledger module. */
private fun PeriodSummary.toLedgerSummary() = LedgerSummary(
    income = formatListMoney(income),
    expenses = formatListMoney(expense),
    net = formatListMoney(total)
)

@Composable
private fun SummaryColumn(label: String, amount: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
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
    val dateMetaSpacing = if (day.dayNumber.length >= 2) 10.dp else 8.dp

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = day.dayNumber,
            fontSize = responsiveTextSize(baseSp = 32f, minSp = 28f, maxSp = 34f),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.widthIn(min = 40.dp, max = 56.dp)
        )
        Spacer(Modifier.width(dateMetaSpacing))
        Column(modifier = Modifier.widthIn(min = 78.dp, max = 112.dp)) {
            Text(
                text = day.date.let { "${it.year}/${it.monthValue.toString().padStart(2,'0')}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
            Box(
                modifier = Modifier.clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = day.weekday,
                    fontSize = responsiveTextSize(baseSp = 10f, minSp = 10f, maxSp = 11f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false
                )
            }
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

// ── Transaction Row ───────────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TransactionRow(
    row: LedgerRow,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val categoryColumnWidth = if (screenWidthDp >= 600) 128.dp else 96.dp
    val isIncome  = row.type == TransactionType.INCOME
    val isExpense = row.type == TransactionType.EXPENSE
    // A Balance adjustment only corrects a balance, so it reads apart from money in and out.
    val isAdjustment = row.isAdjustment
    val amountColor = when {
        isIncome -> IncomeBlue
        isExpense -> ExpenseOrange
        isAdjustment -> MaterialTheme.colorScheme.tertiary
        else -> TransferGray
    }
    val selectedBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) selectedBg else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(row.category,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = if (isAdjustment) FontStyle.Italic else null,
            color = if (isAdjustment) amountColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(categoryColumnWidth),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
            lineHeight = responsiveTextSize(baseSp = 14f, minSp = 13f, maxSp = 15f))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.description,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.accountLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 72.dp)) {
            // In full, never ellipsised: the description gives way instead.
            Text(
                text = row.amount,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                fontStyle = if (isAdjustment) FontStyle.Italic else null,
                color = amountColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun CaptureBanner(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (count == 1) "1 captured transaction to review" else "$count captured transactions to review",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Review",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
