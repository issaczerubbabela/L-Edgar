package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.ui.components.BucketColorDot
import com.issaczerubbabel.ledgar.ui.components.PaceBar
import com.issaczerubbabel.ledgar.ui.components.SectionLabel
import com.issaczerubbabel.ledgar.ui.components.TransactionRow
import com.issaczerubbabel.ledgar.ui.components.TransactionSheetHost
import com.issaczerubbabel.ledgar.ui.components.UndoSnackbar
import com.issaczerubbabel.ledgar.ui.components.tabularNumbers
import com.issaczerubbabel.ledgar.ui.theme.ExpenseRed
import com.issaczerubbabel.ledgar.ui.theme.bucketColor
import com.issaczerubbabel.ledgar.viewmodel.FundCycleUi
import com.issaczerubbabel.ledgar.viewmodel.FundDetailUiState
import com.issaczerubbabel.ledgar.viewmodel.FundDetailViewModel
import com.issaczerubbabel.ledgar.viewmodel.FundListItemUi
import com.issaczerubbabel.ledgar.viewmodel.FundsViewModel
import com.issaczerubbabel.ledgar.viewmodel.HoldingRowUi
import com.issaczerubbabel.ledgar.viewmodel.TransactionActionsViewModel

// ---------------------------------------------------------------- Funds list

/** Every Fund with its balance and target, from More. Archived Funds sit at the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundsScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenFund: (Long) -> Unit,
    vm: FundsViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Funds") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New fund") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp)
        ) {
            if (state.isLoaded && state.active.isEmpty() && state.archived.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Give your money a job", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "A Fund labels money for a purpose, like an emergency fund or a ministry account. " +
                                "It holds a whole account, or a fixed amount of a shared one, and keeps its balance across Salary cycles.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(state.active, key = { "fund-${it.fundId}" }) { item ->
                FundRow(item, onClick = { onOpenFund(item.fundId) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (state.archived.isNotEmpty()) {
                item(key = "archived-label") {
                    SectionLabel("Archived", modifier = Modifier.padding(top = 24.dp, bottom = 4.dp).semantics { heading() })
                }
                items(state.archived, key = { "fund-${it.fundId}" }) { item ->
                    FundRow(item, onClick = { onOpenFund(item.fundId) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (creating) {
        FundEditorSheet(fundId = null, onDismiss = { creating = false })
    }
}

@Composable
private fun FundRow(item: FundListItemUi, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = item.description }
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BucketColorDot(item.colorIndex, modifier = Modifier.padding(top = 7.dp))
            if (item.emoji.isNotBlank()) {
                Text(item.emoji, modifier = Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.bodyLarge)
            }
            Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                text = if (item.isArchived) "Archived" else item.balance,
                style = tabularNumbers(MaterialTheme.typography.titleMedium),
                color = if (item.isArchived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false
            )
        }
        item.progressFraction?.let { fraction ->
            PaceBar(spentFraction = fraction, paceFraction = null, color = bucketColor(item.colorIndex), isOver = false)
        }
        if (item.progress != null || item.shortBy != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                item.progress?.let {
                    Text(it, style = tabularNumbers(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item.shortBy?.let { ShortFlag(it) }
            }
        }
    }
}

/** "₹5,000 short": icon and words as well as colour. */
@Composable
private fun ShortFlag(text: String) {
    Row(
        modifier = Modifier
            .background(ExpenseRed.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(14.dp))
        Text(text, style = tabularNumbers(MaterialTheme.typography.labelSmall), color = ExpenseRed, maxLines = 1)
    }
}

// ---------------------------------------------------------------- Fund detail

/** A Fund's page: its balance split by holding, this cycle's Set aside, and its history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundDetailScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    onCopyTransaction: (id: Long, useToday: Boolean) -> Unit,
    vm: FundDetailViewModel = hiltViewModel(),
    actionsVm: TransactionActionsViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var sheetTransactionId by remember { mutableStateOf<Long?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val pendingDelete by actionsVm.pendingDelete.collectAsStateWithLifecycle()
    UndoSnackbar(pendingDelete, snackbarHostState, onUndo = actionsVm::undoDelete, onCommit = actionsVm::commitPendingDelete)

    // A deleted Fund has nothing left to show.
    LaunchedEffect(state.notFound) { if (state.notFound) onBack() }

    Scaffold(
        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = listOf(state.emoji, state.name).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Fund" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (state.fund != null) TextButton(onClick = { editing = true }) { Text("Edit") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (!state.isLoaded) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            FundDetailContent(state, padding, onOpenTransaction = { sheetTransactionId = it })
        }
    }

    if (editing && state.fund != null) {
        FundEditorSheet(fundId = state.fundId, onDismiss = { editing = false })
    }

    val sheetRow = state.history.firstOrNull { it.transactionId == sheetTransactionId }
    sheetRow?.let { row ->
        TransactionSheetHost(
            transaction = row.transaction,
            onDismiss = { sheetTransactionId = null },
            onEdit = onEditTransaction,
            onCopy = onCopyTransaction,
            vm = actionsVm
        )
    }
}

@Composable
private fun FundDetailContent(state: FundDetailUiState, padding: PaddingValues, onOpenTransaction: (Long) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "hero") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BucketColorDot(state.colorIndex)
                    SectionLabel("Balance")
                }
                Text(state.balance, style = tabularNumbers(MaterialTheme.typography.displaySmall))
                state.archivedNote?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.progressFraction?.let { fraction ->
                    PaceBar(
                        spentFraction = fraction,
                        paceFraction = null,
                        color = bucketColor(state.colorIndex),
                        isOver = false,
                        modifier = Modifier.padding(top = 4.dp).semantics { contentDescription = state.progress.orEmpty() }
                    )
                }
                val targetLine = listOfNotNull(state.progress, state.dueDate).joinToString(" · ")
                if (targetLine.isNotBlank()) {
                    Text(targetLine, style = tabularNumbers(MaterialTheme.typography.bodyMedium), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.holdingSummary.isNotBlank()) {
                    Text(state.holdingSummary, style = tabularNumbers(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        state.cycle?.let { cycle -> item(key = "cycle") { CycleCard(cycle) } }

        item(key = "holdings-title") { DetailHeading("Held in") }
        if (state.holdings.isEmpty()) {
            item(key = "holdings-empty") {
                EmptyLine("No holdings yet. Use Edit to say which account holds this money.")
            }
        }
        items(state.holdings, key = { "holding-${it.holdingId}" }) { holding ->
            HoldingRow(holding)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 20.dp))
        }

        item(key = "history-title") { DetailHeading("History") }
        if (state.history.isEmpty()) {
            item(key = "history-empty") {
                EmptyLine("Transfers into this Fund's accounts, and Expenses paid from an account it holds whole, show up here.")
            }
        }
        items(state.history, key = { "history-${it.transactionId}" }) { row ->
            TransactionRow(row = row.row, onClick = { onOpenTransaction(row.transactionId) }, trailingLine = row.trailingLine)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp, modifier = Modifier.padding(start = 80.dp))
        }
    }
}

@Composable
private fun DetailHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp).semantics { heading() }
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun CycleCard(cycle: FundCycleUi) {
    val spoken = buildString {
        append("This cycle: ").append(cycle.transferred).append(" transferred")
        cycle.planned?.let { append(" of ").append(it).append(" planned") }
        append(". ").append(cycle.status)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SectionLabel("This cycle")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Planned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(cycle.planned ?: "—", style = tabularNumbers(MaterialTheme.typography.titleMedium))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Transferred", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(cycle.transferred, style = tabularNumbers(MaterialTheme.typography.titleMedium))
            }
        }
        Text(
            text = cycle.status,
            style = MaterialTheme.typography.bodySmall,
            color = if (cycle.isMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HoldingRow(holding: HoldingRowUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clearAndSetSemantics { contentDescription = holding.sentence }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(holding.accountName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(holding.kindLabel, holding.openingNote, "Not counted in Your money".takeIf { holding.notInTotals }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            holding.shortBy?.let { ShortFlag(it) }
        }
        Text(holding.value, style = tabularNumbers(MaterialTheme.typography.bodyLarge), maxLines = 1, softWrap = false)
    }
}
