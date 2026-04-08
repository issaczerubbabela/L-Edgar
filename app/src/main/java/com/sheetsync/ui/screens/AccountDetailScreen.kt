package com.sheetsync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sheetsync.ui.theme.ExpenseOrange
import com.sheetsync.ui.theme.IncomeBlue
import com.sheetsync.viewmodel.AddEditAccountViewModel
import com.sheetsync.viewmodel.AccountDetailViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    vm: AccountDetailViewModel = hiltViewModel(),
    formVm: AddEditAccountViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    val formState by formVm.uiState.collectAsState()
    val accountGroups by formVm.accountGroups.collectAsState()
    val allAccounts by formVm.allAccounts.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditSheet by remember { mutableStateOf(false) }
    var showPermanentDeleteDialog by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showChart by remember { mutableStateOf(false) }
    var pickerMonth by remember(state.selectedMonth) { mutableStateOf(state.selectedMonth.monthValue) }
    var pickerYear by remember(state.selectedMonth) { mutableStateOf(state.selectedMonth.year) }

    val monthLabel = remember(state.selectedMonth) {
        state.selectedMonth.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))
    }
    val statementLabel = remember(state.selectedMonth) {
        val start = state.selectedMonth.atDay(1)
        val end = state.selectedMonth.atEndOfMonth()
        "${statementDate(start)} ~ ${statementDate(end)}"
    }
    val groupedEntries = remember(state.entries) {
        state.entries.groupBy { it.date }
            .toSortedMap(compareByDescending { it })
            .entries
            .toList()
    }

    LaunchedEffect(Unit) {
        formVm.saved.collect {
            showEditSheet = false
        }
    }

    LaunchedEffect(Unit) {
        formVm.deleted.collect {
            showEditSheet = false
            onBack()
        }
    }

    LaunchedEffect(Unit) {
        formVm.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = state.accountName,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = vm::prevMonth) { Icon(Icons.Filled.ChevronLeft, null) }
                    Row(
                        modifier = Modifier
                            .clickable { showMonthPicker = true }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(monthLabel, fontSize = 14.sp)
                    }
                    IconButton(onClick = vm::nextMonth) { Icon(Icons.Filled.ChevronRight, null) }
                    IconButton(onClick = { showChart = !showChart }) { Icon(Icons.Filled.BarChart, null) }
                    IconButton(onClick = {
                        formVm.startEdit(state.accountId)
                        showEditSheet = true
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit account")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { topPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(topPadding)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Statement", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(statementLabel, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        text = "As-Of: ${state.asOfDate}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryCell("Deposit", state.deposit, IncomeBlue)
                        SummaryCell("Withdrawal", state.withdrawal, ExpenseOrange)
                        SummaryCell("Total", state.total, MaterialTheme.colorScheme.onBackground)
                        SummaryCell("Balance", state.currentBalance, if (state.currentBalance < 0) ExpenseOrange else MaterialTheme.colorScheme.onBackground)
                    }

                    Text(
                        text = "Showing newest to oldest. Balance label = post-transaction balance.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
            }

            if (showChart) {
                item {
                    AccountStatementChart(
                        modelProducer = vm.statementChartModelProducer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            groupedEntries.forEach { (date, entriesForDay) ->
                item {
                    DayHeader(date)
                }
                itemsIndexed(entriesForDay, key = { _, entry -> entry.id }) { index, entry ->
                    val isIncome = entry.type == "Income"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(76.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = entry.description.ifBlank { entry.category },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entry.paymentMode.isNotBlank()) {
                                Text(
                                    text = entry.paymentMode,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(136.dp)) {
                            Text(
                                "₹ ${money(entry.amount)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isIncome) IncomeBlue else ExpenseOrange
                            )
                            Text(
                                "PostBal ${money(entry.runningBalance)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (index < entriesForDay.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 80.dp)
                        )
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

    if (showEditSheet) {
        AddEditAccountSheet(
            state = formState,
            accountGroups = accountGroups,
            onDismiss = { showEditSheet = false },
            onGroupChange = formVm::updateGroup,
            onNameChange = formVm::updateName,
            onAmountChange = formVm::updateAmount,
            onInitialBalanceDateChange = formVm::updateInitialBalanceDate,
            onDescriptionChange = formVm::updateDescription,
            onIncludeInTotalsChange = formVm::updateIncludeInTotals,
            onHiddenChange = formVm::updateHidden,
            onSave = formVm::save,
            onDelete = formVm::deleteIfAllowed,
            onDeletePermanently = { showPermanentDeleteDialog = true }
        )
    }

    if (showPermanentDeleteDialog) {
        AccountPermanentDeleteDialog(
            accountName = state.accountName,
            reassignOptions = allAccounts
                .filter { it.id != state.accountId }
                .map { ReassignAccountOption(id = it.id, label = "${it.accountName} (${it.groupName})") },
            onDismiss = { showPermanentDeleteDialog = false },
            onConfirm = { reassignToAccountId ->
                showPermanentDeleteDialog = false
                formVm.deletePermanently(reassignToAccountId)
            }
        )
    }
}

@Composable
private fun SummaryCell(label: String, amount: Double, color: androidx.compose.ui.graphics.Color) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Text("₹ ${money(amount)}", color = color, fontSize = 16.sp)
    }
}

@Composable
private fun DayHeader(date: String) {
    val label = runCatching {
        val d = LocalDate.parse(date)
        d.format(DateTimeFormatter.ofPattern("dd EEE", Locale.ENGLISH))
    }.getOrElse { date }

    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )
    }
}

private val monthNames = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

private fun statementDate(date: LocalDate): String {
    val yy = date.year % 100
    return "${date.monthValue}.${date.dayOfMonth}.${yy.toString().padStart(2, '0')}"
}

private fun money(value: Double): String {
    val sign = if (value < 0) "-" else ""
    return sign + "%,.2f".format(kotlin.math.abs(value))
}
