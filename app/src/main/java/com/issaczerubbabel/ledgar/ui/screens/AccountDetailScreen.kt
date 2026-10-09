package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.ui.theme.ExpenseOrange
import com.issaczerubbabel.ledgar.ui.theme.IncomeBlue
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.viewmodel.AccountDetailViewModel
import com.issaczerubbabel.ledgar.viewmodel.ReconcileViewModel
import com.issaczerubbabel.ledgar.viewmodel.StatementMonthUi
import com.issaczerubbabel.ledgar.ui.components.TransactionRow
import com.issaczerubbabel.ledgar.ui.components.TransactionSheetHost

/**
 * An Account's page: its balance today, quick Transfer and Add, and one continuous statement,
 * newest first, where each month's header adds up (Opening + In − Out = Closing).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountDetailScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    onCopyTransaction: (id: Long, useToday: Boolean) -> Unit,
    onAddTransaction: (type: String, accountId: Long) -> Unit,
    onEditAccount: (Long) -> Unit,
    vm: AccountDetailViewModel = hiltViewModel(),
    reconcileVm: ReconcileViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showReconcile by remember { mutableStateOf(false) }
    var sheetTransactionId by remember { mutableStateOf<Long?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        reconcileVm.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.accountName,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.groupName.isNotBlank()) {
                            Text(
                                text = state.groupName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { onEditAccount(state.accountId) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit account")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { topPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(topPadding),
            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 16.dp)
        ) {
            item(key = "header") {
                BalanceHeader(
                    balance = state.balanceToday,
                    isNegative = state.isBalanceNegative,
                    note = listOf(state.lastChecked, state.initialBalanceNote).filter { it.isNotBlank() }.joinToString("\n")
                )
            }
            item(key = "actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButton(
                        label = "Reconcile",
                        icon = Icons.Filled.DoneAll,
                        modifier = Modifier.weight(1f),
                        onClick = { showReconcile = true }
                    )
                    ActionButton(
                        label = "Transfer",
                        icon = Icons.Filled.SwapHoriz,
                        modifier = Modifier.weight(1f),
                        onClick = { onAddTransaction(TransactionType.TRANSFER, state.accountId) }
                    )
                    ActionButton(
                        label = "Add",
                        icon = Icons.Filled.Add,
                        modifier = Modifier.weight(1f),
                        onClick = { onAddTransaction(TransactionType.EXPENSE, state.accountId) }
                    )
                }
            }
            item(key = "statement-title") {
                Text(
                    text = "Statement",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
                        .semantics { heading() }
                )
            }
            if (state.isLoaded && state.months.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No transactions on this account yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                    )
                }
            }
            state.months.forEach { month ->
                stickyHeader(key = "month-${month.key}") {
                    MonthHeader(month)
                }
                items(month.rows, key = { "row-${it.transactionId}" }) { row ->
                    TransactionRow(
                        row = row.row,
                        onClick = { sheetTransactionId = row.transactionId },
                        // A row before the As-of date is listed but doesn't count.
                        modifier = Modifier.alpha(if (row.counts) 1f else 0.6f),
                        trailingLine = row.trailingLine
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 80.dp)
                    )
                }
            }
        }
    }

    if (showReconcile) {
        ReconcileSheet(vm = reconcileVm, onDismiss = { showReconcile = false })
    }

    val sheetRow = state.months.firstNotNullOfOrNull { month -> month.rows.firstOrNull { it.transactionId == sheetTransactionId } }
    sheetRow?.let { row ->
        TransactionSheetHost(
            transaction = row.transaction,
            onDismiss = { sheetTransactionId = null },
            onEdit = onEditTransaction,
            onCopy = onCopyTransaction
        )
    }
}

@Composable
private fun BalanceHeader(balance: String, isNegative: Boolean, note: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Balance today",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = balance,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (isNegative) ExpenseOrange else MaterialTheme.colorScheme.onBackground
        )
        if (note.isNotBlank()) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun MonthHeader(month: StatementMonthUi) {
    // Opaque, so rows scroll underneath it while it sticks.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = month.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.semantics { heading() }
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MonthFigure("Opening", month.opening, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                MonthFigure("In", month.moneyIn, IncomeBlue, Modifier.weight(1f))
                MonthFigure("Out", month.moneyOut, ExpenseOrange, Modifier.weight(1f))
                MonthFigure("Closing", month.closing, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
            val split = listOf("In: ${month.inSplit}".takeIf { month.inSplit.isNotBlank() },
                "Out: ${month.outSplit}".takeIf { month.outSplit.isNotBlank() })
                .filterNotNull()
            if (split.isNotEmpty()) {
                Text(
                    text = split.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MonthFigure(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
