package com.issaczerubbabel.ledgar.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.preferences.CashFlowChartStyle
import com.issaczerubbabel.ledgar.viewmodel.CashFlowGranularity
import com.issaczerubbabel.ledgar.viewmodel.StatsBreakdownTab
import com.issaczerubbabel.ledgar.viewmodel.StatsDateRange
import com.issaczerubbabel.ledgar.viewmodel.StatsScope
import com.issaczerubbabel.ledgar.viewmodel.StatsViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun responsiveTextSize(baseSp: Float, minSp: Float = 12f, maxSp: Float = 28f) =
    (
        baseSp * (LocalConfiguration.current.screenWidthDp / 411f).coerceIn(0.9f, 1.08f)
    ).coerceIn(minSp, maxSp).sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(innerPadding: PaddingValues, vm: StatsViewModel = hiltViewModel()) {
    val filterState by vm.filterState.collectAsStateWithLifecycle()
    val resolvedDateRange by vm.resolvedDateRange.collectAsStateWithLifecycle()
    val filteredTransactions by vm.filteredTransactions.collectAsStateWithLifecycle()
    val breakdownTotals by vm.breakdownCategoryTotals.collectAsStateWithLifecycle()
    val cashFlowCategoryOptions by vm.cashFlowCategoryOptions.collectAsStateWithLifecycle()
    val cashFlowXAxisLabels by vm.cashFlowXAxisLabels.collectAsStateWithLifecycle()
    val cashFlowChartStyle by vm.cashFlowChartStyle.collectAsStateWithLifecycle()
    val useCompressedScale by vm.useCompressedScale.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()

    var showAnchorDatePicker by remember { mutableStateOf(false) }
    var showCustomStartPicker by remember { mutableStateOf(false) }
    var showCustomEndPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Stats",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = responsiveTextSize(baseSp = 28f, minSp = 24f, maxSp = 30f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PeriodNavigator(
                        label = filterState.scope.periodLabel(
                            range = resolvedDateRange,
                            anchorDate = filterState.anchorDate
                        ),
                        modifier = Modifier.weight(1.5f),
                        onPrevious = vm::moveToPreviousPeriod,
                        onNext = vm::moveToNextPeriod,
                        onLabelClick = { showAnchorDatePicker = true }
                    )

                    TopRightFilterDropdown(
                        selectedLabel = filterState.scope.label(),
                        options = StatsScope.entries.map { it.label() },
                        modifier = Modifier.weight(1f),
                        onSelect = { selected ->
                            StatsScope.entries
                                .firstOrNull { it.label() == selected }
                                ?.let(vm::updateScope)
                        }
                    )
                }

                if (filterState.scope == StatsScope.SELECT_PERIOD) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { showCustomStartPicker = true }
                        ) {
                            Text(
                                text = "Start: ${formatIsoDate(filterState.customStartDate ?: resolvedDateRange.start)}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { showCustomEndPicker = true }
                        ) {
                            Text(
                                text = "End: ${formatIsoDate(filterState.customEndDate ?: resolvedDateRange.end)}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (filteredTransactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No Data for this period",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        } else {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TabRow(
                            selectedTabIndex = filterState.breakdownTab.ordinal,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            StatsBreakdownTab.entries.forEach { tab ->
                                Tab(
                                    selected = tab == filterState.breakdownTab,
                                    onClick = { vm.updateBreakdownTab(tab) },
                                    text = {
                                        Text(
                                            text = tab.label(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }

                        ExpenseDonutChart(
                            categoryTotals = breakdownTotals,
                            centerLabel = if (filterState.breakdownTab == StatsBreakdownTab.EXPENSE) {
                                "Total Spent"
                            } else {
                                "Total Income"
                            },
                            emptyLabel = if (filterState.breakdownTab == StatsBreakdownTab.EXPENSE) {
                                "No expense data"
                            } else {
                                "No income data"
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TopRightFilterDropdown(
                                selectedLabel = filterState.cashFlowCategory
                                    ?: StatsViewModel.ALL_CATEGORIES_OPTION,
                                options = cashFlowCategoryOptions,
                                modifier = Modifier.weight(1f),
                                onSelect = { selected ->
                                    vm.updateCashFlowCategory(
                                        if (selected == StatsViewModel.ALL_CATEGORIES_OPTION) {
                                            null
                                        } else {
                                            selected
                                        }
                                    )
                                }
                            )

                            TopRightFilterDropdown(
                                selectedLabel = filterState.cashFlowGranularity.label(),
                                options = CashFlowGranularity.entries.map { it.label() },
                                modifier = Modifier.weight(1f),
                                onSelect = { selected ->
                                    CashFlowGranularity.entries
                                        .firstOrNull { it.label() == selected }
                                        ?.let(vm::updateCashFlowGranularity)
                                }
                            )
                        }

                        Text(
                            text = "Graph mode: ${cashFlowChartStyle.hintLabel()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        CashFlowBarChart(
                            modelProducer = vm.cashFlowChartModelProducer,
                            xAxisLabels = cashFlowXAxisLabels,
                            markerValueFormatter = vm.cashFlowMarkerValueFormatter,
                            formatRupee = vm::formatRupee,
                            chartValueToAmount = vm::chartValueToAmount,
                            chartStyle = cashFlowChartStyle,
                            useCompressedScale = useCompressedScale,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showAnchorDatePicker) {
        SingleDatePickerDialog(
            initialDate = filterState.anchorDate,
            onDismiss = { showAnchorDatePicker = false },
            onConfirm = { selectedDate ->
                vm.updateAnchorDate(selectedDate)
                showAnchorDatePicker = false
            }
        )
    }

    if (showCustomStartPicker) {
        SingleDatePickerDialog(
            initialDate = filterState.customStartDate ?: resolvedDateRange.start,
            onDismiss = { showCustomStartPicker = false },
            onConfirm = { selectedDate ->
                vm.updateCustomPeriodStart(selectedDate)
                showCustomStartPicker = false
            }
        )
    }

    if (showCustomEndPicker) {
        SingleDatePickerDialog(
            initialDate = filterState.customEndDate ?: resolvedDateRange.end,
            onDismiss = { showCustomEndPicker = false },
            onConfirm = { selectedDate ->
                vm.updateCustomPeriodEnd(selectedDate)
                showCustomEndPicker = false
            }
        )
    }
}

@Composable
private fun PeriodNavigator(
    label: String,
    modifier: Modifier = Modifier,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLabelClick: () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous")
        }
        OutlinedButton(
            onClick = onLabelClick,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next")
        }
    }
}

@Composable
private fun TopRightFilterDropdown(
    selectedLabel: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selectedLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = responsiveTextSize(baseSp = 14f, minSp = 14f, maxSp = 16f)
                )
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val initialMillis = remember(initialDate) {
        initialDate
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis
                    if (selectedMillis != null) {
                        onConfirm(selectedMillis.toLocalDate())
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

private fun StatsScope.label(): String {
    return when (this) {
        StatsScope.WEEKLY -> "Weekly"
        StatsScope.MONTHLY -> "Monthly"
        StatsScope.YEARLY -> "Yearly"
        StatsScope.SELECT_PERIOD -> "Select Period"
    }
}

private fun StatsScope.periodLabel(range: StatsDateRange, anchorDate: LocalDate): String {
    val weeklyFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    val monthlyFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    return when (this) {
        StatsScope.WEEKLY -> "${range.start.format(weeklyFormatter)} - ${range.end.format(weeklyFormatter)}"
        StatsScope.MONTHLY -> YearMonth.from(anchorDate).format(monthlyFormatter)
        StatsScope.YEARLY -> anchorDate.year.toString()
        StatsScope.SELECT_PERIOD -> "${formatIsoDate(range.start)} - ${formatIsoDate(range.end)}"
    }
}

private fun StatsBreakdownTab.label(): String {
    return when (this) {
        StatsBreakdownTab.EXPENSE -> "Expense"
        StatsBreakdownTab.INCOME -> "Income"
    }
}

private fun CashFlowGranularity.label(): String {
    return when (this) {
        CashFlowGranularity.DAILY -> "Daily"
        CashFlowGranularity.WEEKLY -> "Weekly"
        CashFlowGranularity.MONTHLY -> "Monthly"
        CashFlowGranularity.YEARLY -> "Yearly"
    }
}

private fun CashFlowChartStyle.hintLabel(): String {
    return when (this) {
        CashFlowChartStyle.BAR -> "Bars"
        CashFlowChartStyle.LINE -> "Lines"
    }
}

private fun Long.toLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

private fun formatIsoDate(date: LocalDate): String = date.toString()
