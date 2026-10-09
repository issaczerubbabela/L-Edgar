package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.viewmodel.SearchUiState
import com.issaczerubbabel.ledgar.ledger.Ledger
import com.issaczerubbabel.ledgar.ledger.dateRangeLabel
import com.issaczerubbabel.ledgar.ledger.searchDays
import com.issaczerubbabel.ledgar.ui.components.DateRangePickerDialog
import com.issaczerubbabel.ledgar.util.formatListMoney
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import java.time.LocalDate
import com.issaczerubbabel.ledgar.ui.components.TransactionRow
import com.issaczerubbabel.ledgar.ui.components.TransactionSheetHost
import com.issaczerubbabel.ledgar.viewmodel.SearchViewModel
import com.issaczerubbabel.ledgar.viewmodel.TransactionActionsViewModel

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
    val accounts by actionsVm.accounts.collectAsStateWithLifecycle()
    val days = remember(state.results, accounts) { Ledger.searchDays(state.results, accounts) }
    var sheetTransactionId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = vm::onQueryChange,
                        singleLine = true,
                        placeholder = { Text("Search description or remarks") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                actions = {
                    IconButton(onClick = vm::toggleFilters) {
                        Icon(Icons.Filled.FilterList, contentDescription = "Filter")
                    }
                }
            )
        }
    ) { scaffoldPadding ->
        val contentPadding = PaddingValues(
            top = scaffoldPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            AnimatedVisibility(
                visible = state.filtersExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SearchFilters(
                    state = state,
                    onDateRangeChange = vm::onDateRangeChange,
                    onAccountSelected = vm::onAccountSelected,
                    onCategorySelected = vm::onCategorySelected,
                    onMinAmountChange = vm::onMinAmountChange,
                    onMaxAmountChange = vm::onMaxAmountChange
                )
            }

            SummaryRow(
                income = state.incomeTotal,
                expense = state.expenseTotal,
                transfer = state.transferTotal
            )

            if (state.results.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.hasActiveSearch) {
                            "No matching transactions"
                        } else {
                            "Start typing or apply a filter"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    days.forEach { day ->
                        item(key = "day-${day.header}") { SearchDayHeader(day.header) }
                        items(day.rows, key = { it.id }) { row ->
                            TransactionRow(
                                row = row,
                                onClick = { sheetTransactionId = row.id },
                                highlight = state.query
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
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

@Composable
private fun SearchFilters(
    state: SearchUiState,
    onDateRangeChange: (LocalDate?, LocalDate?) -> Unit,
    onAccountSelected: (Long?) -> Unit,
    onCategorySelected: (String) -> Unit,
    onMinAmountChange: (String) -> Unit,
    onMaxAmountChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Filters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

        // A picker, not typed dates, so a date can't be mistyped.
        var showRangePicker by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { showRangePicker = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(Ledger.dateRangeLabel(state.rangeStart, state.rangeEnd))
            }
            if (state.rangeStart != null || state.rangeEnd != null) {
                IconButton(onClick = { onDateRangeChange(null, null) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear date range")
                }
            }
        }
        if (showRangePicker) {
            val today = LocalDate.now()
            DateRangePickerDialog(
                initialStart = state.rangeStart ?: today.withDayOfMonth(1),
                initialEnd = state.rangeEnd ?: today,
                onDismiss = { showRangePicker = false },
                onConfirm = { start, end ->
                    onDateRangeChange(start, end)
                    showRangePicker = false
                }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccountFilterDropdown(
                options = state.accounts,
                selectedId = state.selectedAccountId,
                onSelect = onAccountSelected,
                modifier = Modifier.weight(1f)
            )
            CategoryFilterDropdown(
                options = state.categories,
                selected = state.selectedCategory,
                onSelect = onCategorySelected,
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.minAmount,
                onValueChange = onMinAmountChange,
                label = { Text("Min Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.maxAmount,
                onValueChange = onMaxAmountChange,
                label = { Text("Max Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
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

@Composable
private fun SummaryRow(income: Double, expense: Double, transfer: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        SummaryCell(label = "Income", value = income, modifier = Modifier.weight(1f))
        SummaryCell(label = "Expense", value = expense, modifier = Modifier.weight(1f))
        SummaryCell(label = "Transfer", value = transfer, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCell(label: String, value: Double, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = formatListMoney(value),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountFilterDropdown(
    options: List<AccountRecord>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = options.firstOrNull { it.id == selectedId }?.accountName ?: "All Accounts"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = true },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Account") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All Accounts") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            options.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.accountName) },
                    onClick = {
                        onSelect(account.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterDropdown(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = selected.ifBlank { "All Categories" }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = true },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All Categories") },
                onClick = {
                    onSelect("")
                    expanded = false
                }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
