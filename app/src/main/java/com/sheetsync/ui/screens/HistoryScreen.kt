package com.sheetsync.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.local.entity.AccountRecord
import com.sheetsync.ui.theme.*
import com.sheetsync.viewmodel.CalendarCell
import com.sheetsync.viewmodel.DayGroup
import com.sheetsync.viewmodel.HistoryViewModel
import com.sheetsync.viewmodel.MonthlyViewModel
import com.sheetsync.viewmodel.PeriodSummary
import com.sheetsync.viewmodel.TotalViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

private val TABS = listOf("Daily", "Calendar", "Monthly", "Total")
private val monthNames = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)
private fun formatMoney(amount: Double)   = "₹ %,.2f".format(amount)
private fun formatCompact(amount: Double) = "%,.2f".format(kotlin.math.abs(amount))
private fun formatSignedMoney(amount: Double): String {
    val sign = if (amount > 0.0001) "+" else if (amount < -0.0001) "-" else ""
    return "$sign₹ %,.2f".format(kotlin.math.abs(amount))
}

private enum class BatchAction {
    EDIT_DATES,
    EDIT_CATEGORIES,
    EDIT_ASSETS,
    EDIT_DESCRIPTIONS
}

// ── Main Screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navInsets: PaddingValues,
    onNavigateToLog: () -> Unit,
    onNavigateToEditTransaction: (Long) -> Unit,
    onNavigateToCopyTransaction: (Long, Boolean) -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToFilterSelection: () -> Unit,
    onNavigateToBudgetSetting: () -> Unit,
    vm: HistoryViewModel = hiltViewModel(),
    monthlyVm: MonthlyViewModel = hiltViewModel(),
    totalVm: TotalViewModel = hiltViewModel()
) {
    val state      by vm.uiState.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val monthlyState by monthlyVm.uiState.collectAsState()
    val totalState by totalVm.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var pickerMonth by remember(state.selectedMonth, state.selectedYear) { mutableIntStateOf(state.selectedMonth) }
    var pickerYear by remember(state.selectedMonth, state.selectedYear) { mutableIntStateOf(state.selectedYear) }
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

    val allVisibleRecords = remember(state.groups) { state.groups.flatMap { it.records } }
    val selectedCount = vm.selectedTxIds.size
    val selectedSum = vm.selectedSum(allVisibleRecords)
    val selectedIdSet = vm.selectedTxIds.toSet()

    LaunchedEffect(selectedTab) {
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
        3 -> totalState.periodLabel
        else -> state.monthLabel
    }
    val onPrevPeriod = when (selectedTab) {
        2 -> monthlyVm::prevYear
        3 -> totalVm::prevMonth
        else -> vm::prevMonth
    }
    val onNextPeriod = when (selectedTab) {
        2 -> monthlyVm::nextYear
        3 -> totalVm::nextMonth
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
            PeriodTabRow(selectedTab, headerBg, headerText) { selectedTab = it }

            // Single pinned summary row below tabs
            val pinnedSummary = when (selectedTab) {
                2 -> monthlyState.summary
                3 -> totalState.summary
                else -> state.summary
            }
            SummaryBar(pinnedSummary)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    1 -> CalendarContent(
                        cells        = state.calendarCells,
                        selectedDate = vm.selectedDate,
                        isLight      = isLight,
                        onDaySelect  = vm::selectDate
                    )
                    2 -> MonthlyTabScreen(
                        monthGroups = monthlyState.monthGroups,
                        onToggleExpand = monthlyVm::toggleMonthExpanded,
                        modifier = Modifier.fillMaxSize()
                    )
                    3 -> TotalTabScreen(
                        state = totalState,
                        onToggleBudget = totalVm::toggleBudgetSection,
                        onToggleAccounts = totalVm::toggleAccountsSection,
                        onNavigateBudgetSetting = onNavigateToBudgetSetting,
                        onExportClick = totalVm::openExportDialog,
                        onExportDismiss = totalVm::closeExportDialog,
                        onSelectExportInterval = totalVm::selectExportInterval,
                        onCustomStartChanged = totalVm::updateCustomStart,
                        onCustomEndChanged = totalVm::updateCustomEnd,
                        onExportConfirm = totalVm::requestExportDocument,
                        pendingExportFileName = totalState.pendingExportFileName,
                        onConsumeExportRequest = totalVm::consumeExportRequest,
                        onExportUriPicked = totalVm::exportDataToUri,
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> DailyContent(
                        groups = state.groups,
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
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pendingBatchAction = null },
            confirmButton = {
                TextButton(onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis
                    if (selectedMillis != null) {
                        val selectedDate = Instant.ofEpochMilli(selectedMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        vm.updateSelectedDates(selectedDate.toString())
                    }
                    pendingBatchAction = null
                }) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBatchAction = null }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
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
    cells: List<CalendarCell>,
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
                        onTap       = { if (cell.isCurrentMonth) onDaySelect(cell.date) }
                    )
                }
            }
        }
    }
}

// ── Calendar Cell (95dp, stacked amounts) ─────────────────────────────────────

@Composable
private fun CalendarCellView(
    cell: CalendarCell,
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
        !cell.isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
        isSunday             -> Color(0xFFEF5350)
        isSaturday           -> IncomeBlue
        else                 -> MaterialTheme.colorScheme.onBackground
    }

    val normalTextColor = if (isSelected) selectedTextColor else MaterialTheme.colorScheme.onBackground
    val incomeColor     = if (isSelected) selectedTextColor else IncomeBlue
    val expenseColor    = if (isSelected) selectedTextColor else ExpenseOrange

    // "07/01" for 1st of a month, plain number otherwise
    val dateLabel = if (cell.date.dayOfMonth == 1)
        "${cell.date.monthValue.toString().padStart(2, '0')}/01"
    else cell.date.dayOfMonth.toString()

    // Pre-compute daily amounts
    val hasIncome  = cell.dayIncome  > 0.005
    val hasExpense = cell.dayExpense > 0.005
    val hasBoth    = hasIncome && hasExpense
    val dayNet     = cell.dayIncome - cell.dayExpense

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
                fontSize = if (cell.date.dayOfMonth == 1) 9.sp else 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        // ── Category dots — centre ────────────────────────────────────────────
        if (cell.isCurrentMonth && cell.categories.isNotEmpty()) {
            val visibleCats = cell.categories.take(4)
            val extra       = cell.categories.size - visibleCats.size
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                visibleCats.forEach { cat ->
                    Canvas(modifier = Modifier.size(6.dp)) { drawCircle(categoryDotColor(cat)) }
                }
                if (extra > 0) {
                    Text("+$extra", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── Stacked amounts — bottom end ──────────────────────────────────────
        if (cell.isCurrentMonth && cell.totalTransactions > 0) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 3.dp, bottom = 3.dp)
            ) {
                when {
                    hasBoth -> {
                        // Income row
                        Text(
                            text       = formatCompact(cell.dayIncome),
                            color      = incomeColor,
                            fontSize   = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign  = TextAlign.End
                        )
                        // Expense row
                        Text(
                            text       = formatCompact(cell.dayExpense),
                            color      = expenseColor,
                            fontSize   = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign  = TextAlign.End
                        )
                        // Net row (bold)
                        Text(
                            text       = formatCompact(dayNet),
                            color      = normalTextColor,
                            fontSize   = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign  = TextAlign.End
                        )
                    }
                    hasIncome -> Text(
                        text       = formatCompact(cell.dayIncome),
                        color      = incomeColor,
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign  = TextAlign.End
                    )
                    hasExpense -> Text(
                        text       = formatCompact(cell.dayExpense),
                        color      = expenseColor,
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign  = TextAlign.End
                    )
                    else -> Text(
                        text       = "0",
                        color      = normalTextColor,
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign  = TextAlign.End
                    )
                }
            }
        }
    }
}

// ── Daily List Content ────────────────────────────────────────────────────────

@Composable
private fun DailyContent(
    groups: List<DayGroup>,
    selectedIds: Set<Long>,
    onTransactionClick: (ExpenseRecord) -> Unit,
    onTransactionLongClick: (ExpenseRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    if (groups.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No transactions this month",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 96.dp)) {
            groups.forEach { group ->
                item(key = group.date.toString()) { DayGroupHeader(group) }
                itemsIndexed(group.records, key = { _, record -> record.id }) { index, record ->
                    TransactionRow(
                        record = record,
                        isSelected = selectedIds.contains(record.id),
                        onClick = { onTransactionClick(record) },
                        onLongClick = { onTransactionLongClick(record) }
                    )
                    if (index < group.records.lastIndex) {
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
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("l.edgar", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = contentColor)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onPrevPeriod, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.ChevronLeft, null, tint = contentColor)
                }
                Text(
                    text = periodLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = contentColor,
                    modifier = if (periodClickable) Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onPeriodClick)
                        .padding(horizontal = 6.dp, vertical = 4.dp) else Modifier
                )
                IconButton(onClick = onNextPeriod, modifier = Modifier.size(30.dp)) {
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
                text = formatSignedMoney(selectedSum),
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
    TabRow(
        selectedTabIndex = selected,
        containerColor   = bg,
        contentColor     = textColor,
        indicator = { positions ->
            TabRowDefaults.Indicator(
                modifier = Modifier.tabIndicatorOffset(positions[selected]),
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
                        fontWeight = if (selected == idx) FontWeight.Bold else FontWeight.Normal,
                        fontSize   = 14.sp,
                        color      = if (selected == idx) textColor else textColor.copy(alpha = 0.55f)
                    )
                }
            )
        }
    }
}

// ── Summary Bar ───────────────────────────────────────────────────────────────

@Composable
private fun SummaryBar(summary: PeriodSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        SummaryColumn("Income",   formatMoney(summary.income),  IncomeBlue,    Modifier.weight(1f))
        SummaryColumn("Expenses", formatMoney(summary.expense), ExpenseOrange, Modifier.weight(1f))
        SummaryColumn("Total",    formatMoney(summary.total),   MaterialTheme.colorScheme.onBackground, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryColumn(label: String, amount: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = color, maxLines = 1)
    }
}

// ── Day Group Header ──────────────────────────────────────────────────────────

@Composable
private fun DayGroupHeader(group: DayGroup) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(group.dayNumber, fontSize = 32.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.width(48.dp))
        Column(modifier = Modifier.width(96.dp)) {
            Text(
                text = group.date.let { "${it.year}/${it.monthValue.toString().padStart(2,'0')}" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier.clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(group.dayOfWeekBadge, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = formatMoney(group.dayIncome),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (group.dayIncome > 0) IncomeBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.width(80.dp), textAlign = TextAlign.End
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatMoney(group.dayExpense),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (group.dayExpense > 0) ExpenseOrange else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.width(80.dp), textAlign = TextAlign.End
        )
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
    record: ExpenseRecord,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isIncome  = record.type == "Income"
    val isExpense = record.type == "Expense"
    val selectedBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) selectedBg else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(record.category,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp), maxLines = 2,
            overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(record.description.ifBlank { record.category },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(record.paymentMode,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(80.dp)) {
            Text(
                text = if (isIncome) formatMoney(record.amount) else formatMoney(0.0),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (isIncome) IncomeBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
            Text(
                text = if (isExpense) formatMoney(record.amount) else formatMoney(0.0),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (isExpense) ExpenseOrange else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
        }
    }
}
