package com.sheetsync.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.sheetsync.ui.theme.*
import com.sheetsync.viewmodel.CalendarCell
import com.sheetsync.viewmodel.DayGroup
import com.sheetsync.viewmodel.HistoryViewModel
import com.sheetsync.viewmodel.MonthlyViewModel
import com.sheetsync.viewmodel.PeriodSummary
import com.sheetsync.viewmodel.TotalViewModel
import java.time.DayOfWeek
import java.time.LocalDate

private val TABS = listOf("Daily", "Calendar", "Monthly", "Total")
private fun formatMoney(amount: Double)   = "₹ %,.2f".format(amount)
private fun formatCompact(amount: Double) = "%,.2f".format(kotlin.math.abs(amount))

// ── Main Screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navInsets: PaddingValues,
    onNavigateToLog: () -> Unit,
    onNavigateToBudgetSetting: () -> Unit,
    vm: HistoryViewModel = hiltViewModel(),
    monthlyVm: MonthlyViewModel = hiltViewModel(),
    totalVm: TotalViewModel = hiltViewModel()
) {
    val state      by vm.uiState.collectAsState()
    val monthlyState by monthlyVm.uiState.collectAsState()
    val totalState by totalVm.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(3) }

    // ── Theme detection ────────────────────────────────────────────────────────
    // luminance() > 0.5 → light theme (white background)
    val isLight    = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val headerBg   = if (isLight) HeaderGreen else MaterialTheme.colorScheme.background
    val headerText = Color.White   // always white on header (green or black)

    Scaffold(
        topBar = { MoneyManagerAppBar(headerBg, headerText) },
        containerColor = MaterialTheme.colorScheme.background
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = scaffoldPadding.calculateTopPadding())
                .padding(bottom = navInsets.calculateBottomPadding())
        ) {
            // Date nav — year mode for Monthly tab, month mode for other tabs
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
            DateNavigatorRow(periodLabel, headerBg, headerText, onPrevPeriod, onNextPeriod)

            // Tabs — red indicator to match app reference style
            val indicatorColor = FabRed
            PeriodTabRow(selectedTab, headerBg, headerText, indicatorColor) { selectedTab = it }

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
                        onExportConfirm = totalVm::exportData,
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> DailyContent(state.groups, Modifier.fillMaxSize())
                }

                // Dual FABs
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = {},
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor   = MaterialTheme.colorScheme.onSurfaceVariant,
                        shape          = CircleShape,
                        elevation      = FloatingActionButtonDefaults.elevation(4.dp)
                    ) { Icon(Icons.Filled.Receipt, null, modifier = Modifier.size(20.dp)) }

                    FloatingActionButton(
                        onClick         = onNavigateToLog,
                        shape           = CircleShape,
                        containerColor  = FabRed,
                        contentColor    = Color.White,
                        elevation       = FloatingActionButtonDefaults.elevation(6.dp),
                        modifier        = Modifier.size(58.dp)
                    ) { Icon(Icons.Filled.Add, null, modifier = Modifier.size(28.dp)) }
                }
            }
        }
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
        // Date number — top start
        Text(
            text       = dateLabel,
            color      = dateNumColor,
            fontSize   = if (cell.date.dayOfMonth == 1) 9.sp else 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines   = 1,
            modifier   = Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 3.dp)
        )

        // Today ring — behind the date number
        if (cell.isToday && !isSelected) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .align(Alignment.TopStart)
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
private fun DailyContent(groups: List<DayGroup>, modifier: Modifier = Modifier) {
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
                items(group.records, key = { it.id }) { record ->
                    TransactionRow(record)
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        thickness = 0.5.dp,
                        modifier  = Modifier.padding(start = 80.dp)
                    )
                }
            }
        }
    }
}

// ── App Bar ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoneyManagerAppBar(bg: Color, contentColor: Color) {
    CenterAlignedTopAppBar(
        title = { Text("SheetSync", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = contentColor) },
        navigationIcon = {
            IconButton(onClick = {}) { Icon(Icons.Filled.ArrowBack, null, tint = contentColor) }
        },
        actions = {
            IconButton(onClick = {}) { Icon(Icons.Filled.StarBorder, null, tint = contentColor) }
            IconButton(onClick = {}) { Icon(Icons.Filled.Search, null, tint = contentColor) }
            IconButton(onClick = {}) { Icon(Icons.Filled.Tune, null, tint = contentColor) }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = bg)
    )
}

// ── Date Navigator ────────────────────────────────────────────────────────────

@Composable
private fun DateNavigatorRow(
    label: String, bg: Color, textColor: Color,
    onPrev: () -> Unit, onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, null, tint = textColor) }
        Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = textColor)
        IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, null, tint = textColor) }
    }
}

// ── Period Tabs ───────────────────────────────────────────────────────────────

@Composable
private fun PeriodTabRow(
    selected: Int, bg: Color, textColor: Color, indicatorColor: Color,
    onSelect: (Int) -> Unit
) {
    TabRow(
        selectedTabIndex = selected,
        containerColor   = bg,
        contentColor     = textColor,
        indicator = { positions ->
            TabRowDefaults.Indicator(
                modifier = Modifier.tabIndicatorOffset(positions[selected]),
                color    = indicatorColor,
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
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
}

// ── Transaction Row ───────────────────────────────────────────────────────────

@Composable
private fun TransactionRow(record: ExpenseRecord) {
    val isIncome  = record.type == "Income"
    val isExpense = record.type == "Expense"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
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
