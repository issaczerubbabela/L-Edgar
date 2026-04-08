package com.sheetsync.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sheetsync.ui.theme.ExpenseOrange
import com.sheetsync.ui.theme.IncomeBlue
import com.sheetsync.viewmodel.AccountFilterOptionItem
import com.sheetsync.viewmodel.FilterOptionItem
import com.sheetsync.viewmodel.FilterSelectionTab
import com.sheetsync.viewmodel.FilterSelectionUiState
import com.sheetsync.viewmodel.FilterSelectionViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FilterSelectionScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onApplyFilters: (Int, Int, Set<Long>, Set<Long>, Set<Long>) -> Unit,
    vm: FilterSelectionViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Back")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.monthLabel, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = vm::prevMonth, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
                        }
                        IconButton(onClick = vm::nextMonth, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = scaffoldPadding.calculateTopPadding())
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            Text(
                text = "Select items that you want to filter.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircleProgressCard(
                    label = "Income",
                    progress = progress(state.selectedIncomeSum, state.incomeTotal),
                    amount = state.selectedIncomeSum,
                    color = IncomeBlue,
                    modifier = Modifier.weight(1f)
                )
                CircleProgressCard(
                    label = "Expenses",
                    progress = progress(state.selectedExpenseSum, state.expenseTotal),
                    amount = state.selectedExpenseSum,
                    color = ExpenseOrange,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total\n₹ %,.2f".format(state.selectedIncomeSum - state.selectedExpenseSum),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        onApplyFilters(
                            state.selectedYear,
                            state.selectedMonth,
                            state.selectedIncomeIds,
                            state.selectedExpenseIds,
                            state.selectedAccountIds
                        )
                    }
                ) {
                    Text("Filter")
                }
            }

            TabRow(selectedTabIndex = state.activeTab.ordinal) {
                Tab(
                    selected = state.activeTab == FilterSelectionTab.INCOME,
                    onClick = { vm.setActiveTab(FilterSelectionTab.INCOME) },
                    text = { Text("INCOME") }
                )
                Tab(
                    selected = state.activeTab == FilterSelectionTab.EXPENSES,
                    onClick = { vm.setActiveTab(FilterSelectionTab.EXPENSES) },
                    text = { Text("EXPENSES") }
                )
                Tab(
                    selected = state.activeTab == FilterSelectionTab.ACCOUNT,
                    onClick = { vm.setActiveTab(FilterSelectionTab.ACCOUNT) },
                    text = { Text("ACCOUNT") }
                )
            }

            when (state.activeTab) {
                FilterSelectionTab.INCOME -> {
                    OptionsList(
                        options = state.incomeOptions,
                        selectedIds = state.selectedIncomeIds,
                        onToggle = vm::toggleIncomeCategory,
                        onClear = vm::clearIncomeCategories
                    )
                }
                FilterSelectionTab.EXPENSES -> {
                    OptionsList(
                        options = state.expenseOptions,
                        selectedIds = state.selectedExpenseIds,
                        onToggle = vm::toggleExpenseCategory,
                        onClear = vm::clearExpenseCategories
                    )
                }
                FilterSelectionTab.ACCOUNT -> {
                    AccountOptionsList(
                        options = state.accountOptions,
                        selectedIds = state.selectedAccountIds,
                        onToggle = vm::toggleAccount,
                        onClear = vm::clearAccounts
                    )
                }
            }
        }
    }
}

@Composable
private fun CircleProgressCard(
    label: String,
    progress: Float,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(130.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "₹ %,.2f".format(amount),
            style = MaterialTheme.typography.titleLarge,
            color = color
        )
    }
}

@Composable
private fun OptionsList(
    options: List<FilterOptionItem>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onClear: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            OptionRow(
                label = "All",
                checked = selectedIds.isEmpty(),
                amountText = null,
                onClick = onClear
            )
            HorizontalDivider()
        }
        items(options, key = { it.id }) { option ->
            OptionRow(
                label = option.label,
                checked = selectedIds.contains(option.id),
                amountText = "₹ %,.2f".format(option.amount),
                onClick = { onToggle(option.id) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun AccountOptionsList(
    options: List<AccountFilterOptionItem>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onClear: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            OptionRow(
                label = "All",
                checked = selectedIds.isEmpty(),
                amountText = null,
                onClick = onClear
            )
            HorizontalDivider()
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Account", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Income", modifier = Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Expense", modifier = Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(options, key = { it.id }) { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = selectedIds.contains(option.id), onCheckedChange = { onToggle(option.id) })
                Text(text = option.label, modifier = Modifier.weight(1f))
                Text("₹ %,.2f".format(option.incomeAmount), modifier = Modifier.width(110.dp), color = IncomeBlue)
                Text("₹ %,.2f".format(option.expenseAmount), modifier = Modifier.width(110.dp), color = ExpenseOrange)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    checked: Boolean,
    amountText: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onClick() })
        Text(text = label, modifier = Modifier.weight(1f))
        if (amountText != null) {
            Text(text = amountText)
        }
    }
}

private fun progress(selected: Double, total: Double): Float {
    if (total <= 0.0) return 0f
    return (selected / total).toFloat().coerceIn(0f, 1f)
}
