package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.ui.theme.ExpenseOrange
import com.issaczerubbabel.ledgar.ui.theme.HeaderGreen
import com.issaczerubbabel.ledgar.ui.theme.IncomeBlue
import com.issaczerubbabel.ledgar.viewmodel.CalendarCell
import com.issaczerubbabel.ledgar.viewmodel.DayGroup
import com.issaczerubbabel.ledgar.viewmodel.FilteredTransactionsViewModel
import com.issaczerubbabel.ledgar.viewmodel.PeriodSummary
import java.time.DayOfWeek
import java.time.LocalDate

private val FILTERED_TABS = listOf("Daily", "Calendar", "Monthly")

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FilteredTransactionsScreen(
    navInsets: PaddingValues,
    onBack: () -> Unit,
    onNavigateToEditTransaction: (Long) -> Unit,
    vm: FilteredTransactionsViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val headerBg = if (isLight) HeaderGreen else MaterialTheme.colorScheme.background
    val headerText = Color.White

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = vm::prevMonth, modifier = Modifier.width(30.dp)) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month", tint = headerText)
                        }
                        Text(state.monthLabel, color = headerText, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = vm::nextMonth, modifier = Modifier.width(30.dp)) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month", tint = headerText)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = headerText)
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = headerBg)
            )
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = scaffoldPadding.calculateTopPadding())
                .padding(bottom = navInsets.calculateBottomPadding())
        ) {
            AppliedFiltersBar(filters = state.appliedFilters)

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = headerBg,
                contentColor = headerText,
                indicator = { positions ->
                    TabRowDefaults.Indicator(
                        modifier = if (positions.isNotEmpty()) {
                            Modifier.tabIndicatorOffset(positions[selectedTab])
                        } else {
                            Modifier
                        },
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                FILTERED_TABS.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = label,
                                color = if (selectedTab == index) headerText else headerText.copy(alpha = 0.6f)
                            )
                        }
                    )
                }
            }

            FilteredSummaryBar(state.summary)
            HorizontalDivider(thickness = 0.5.dp)

            when (selectedTab) {
                0 -> FilteredDailyContent(
                    groups = state.groups,
                    onTransactionClick = { onNavigateToEditTransaction(it.id) },
                    modifier = Modifier.fillMaxSize()
                )
                1 -> FilteredCalendarContent(
                    cells = state.calendarCells,
                    selectedDate = vm.selectedDate,
                    onDaySelect = vm::selectDate,
                    isLight = isLight,
                    modifier = Modifier.fillMaxSize()
                )
                else -> MonthlyTabScreen(
                    monthGroups = state.monthGroups,
                    onToggleExpand = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun AppliedFiltersBar(filters: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        filters.forEach { filter ->
            Text(
                text = filter,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FilteredSummaryBar(summary: PeriodSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        SummaryColumn(
            label = "Income",
            amount = "₹ %,.2f".format(summary.income),
            color = IncomeBlue,
            modifier = Modifier.weight(1f)
        )
        SummaryColumn(
            label = "Expenses",
            amount = "₹ %,.2f".format(summary.expense),
            color = ExpenseOrange,
            modifier = Modifier.weight(1f)
        )
        SummaryColumn(
            label = "Total",
            amount = "₹ %,.2f".format(summary.total),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryColumn(label: String, amount: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = amount, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FilteredDailyContent(
    groups: List<DayGroup>,
    onTransactionClick: (ExpenseRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    if (groups.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No filtered transactions", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(modifier = modifier) {
            groups.forEach { group ->
                item(key = group.date.toString()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(group.dayNumber, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
                        Text(group.dateLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("₹ %,.2f".format(group.dayIncome), color = IncomeBlue)
                        Spacer(Modifier.width(10.dp))
                        Text("₹ %,.2f".format(group.dayExpense), color = ExpenseOrange)
                    }
                }
                itemsIndexed(group.records, key = { _, record -> record.id }) { index, record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTransactionClick(record) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = record.category,
                            modifier = Modifier.width(90.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = record.description.ifBlank { record.category },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "₹ %,.2f".format(record.amount),
                            color = if (record.type == "Income") IncomeBlue else if (record.type == "Expense") ExpenseOrange else MaterialTheme.colorScheme.onBackground
                        )
                    }
                    if (index < group.records.lastIndex) {
                        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(start = 110.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilteredCalendarContent(
    cells: List<CalendarCell>,
    selectedDate: LocalDate?,
    onDaySelect: (LocalDate) -> Unit,
    isLight: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isLight) Color(0xFFE0E0E0) else Color(0xFF333333)
    val dayHeaders = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            dayHeaders.forEachIndexed { idx, day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    textAlign = TextAlign.Center,
                    color = when (idx) {
                        0 -> Color(0xFFEF5350)
                        6 -> IncomeBlue
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        HorizontalDivider(color = borderColor)
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxSize()) {
            items(cells, key = { it.date.toString() }) { cell ->
                FilteredCalendarCell(
                    cell = cell,
                    isSelected = selectedDate == cell.date,
                    borderColor = borderColor,
                    onTap = { if (cell.isCurrentMonth) onDaySelect(cell.date) }
                )
            }
        }
    }
}

@Composable
private fun FilteredCalendarCell(
    cell: CalendarCell,
    isSelected: Boolean,
    borderColor: Color,
    onTap: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent
    val isSunday = cell.date.dayOfWeek == DayOfWeek.SUNDAY
    val isSaturday = cell.date.dayOfWeek == DayOfWeek.SATURDAY

    Box(
        modifier = Modifier
            .height(92.dp)
            .border(0.5.dp, borderColor)
            .background(backgroundColor)
            .clickable(onClick = onTap)
            .padding(4.dp)
    ) {
        Text(
            text = cell.date.dayOfMonth.toString(),
            color = when {
                !cell.isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                isSunday -> Color(0xFFEF5350)
                isSaturday -> IncomeBlue
                else -> MaterialTheme.colorScheme.onBackground
            },
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.TopStart)
        )

        if (cell.totalTransactions > 0) {
            Column(
                modifier = Modifier.align(Alignment.BottomEnd),
                horizontalAlignment = Alignment.End
            ) {
                if (cell.dayIncome > 0.0) {
                    Text("%,.0f".format(cell.dayIncome), color = IncomeBlue, fontSize = 10.sp)
                }
                if (cell.dayExpense > 0.0) {
                    Text("%,.0f".format(cell.dayExpense), color = ExpenseOrange, fontSize = 10.sp)
                }
            }
        }
    }
}
