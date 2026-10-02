package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.capture.categorize.ConfidenceBand
import com.issaczerubbabel.ledgar.ui.components.OptionPickerSheet
import com.issaczerubbabel.ledgar.ui.components.PickerOption
import com.issaczerubbabel.ledgar.ui.theme.ExpenseOrange
import com.issaczerubbabel.ledgar.ui.theme.IncomeBlue
import com.issaczerubbabel.ledgar.util.formatRupeesExact
import com.issaczerubbabel.ledgar.viewmodel.CaptureRowUi
import com.issaczerubbabel.ledgar.viewmodel.ReviewInboxViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private sealed interface Picker {
    data class ForCategory(val row: CaptureRowUi) : Picker
    data class ForAccount(val row: CaptureRowUi) : Picker
}

private val timeFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a")
private val dateFormat = DateTimeFormatter.ofPattern("d MMM")

/** The alert's own date when it differs from the day it arrived, else when it arrived. */
private fun whenText(arrivedAt: Long, txnTime: Long): String {
    val zone = ZoneId.systemDefault()
    val arrived = Instant.ofEpochMilli(arrivedAt).atZone(zone)
    val txn = Instant.ofEpochMilli(txnTime).atZone(zone)
    return if (txn.toLocalDate() == arrived.toLocalDate()) timeFormat.format(arrived)
    else dateFormat.format(txn) + " (date in the alert)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureInboxScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onAddToTrip: (tripId: Long, captureId: Long) -> Unit = { _, _ -> },
    vm: ReviewInboxViewModel = hiltViewModel(),
    activeTripVm: com.issaczerubbabel.ledgar.viewmodel.ActiveTripViewModel = hiltViewModel()
) {
    val activeTrip by activeTripVm.active.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val expenseCategories by vm.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<Picker?>(null) }
    var showBulk by remember { mutableStateOf(false) }
    val accountNames = remember(accounts) { accounts.associate { it.id to it.accountName } }
    val bulk = rows.filter { it.isBulkCandidate }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("To review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { scaffoldPadding ->
        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .padding(bottom = innerPadding.calculateBottomPadding()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nothing to review.\nBank and UPI alerts you receive will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .padding(bottom = innerPadding.calculateBottomPadding()),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (bulk.isNotEmpty()) {
                    item(key = "bulk") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${bulk.size} ready to confirm", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        formatRupeesExact(bulk.sumOf { it.capture.amount }) + " in total",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                TextButton(onClick = { showBulk = true }) { Text("Review all") }
                            }
                        }
                    }
                }
                items(rows, key = { it.capture.id }) { row ->
                    CaptureCard(
                        row = row,
                        accountName = row.accountId?.let { accountNames[it] },
                        onPickCategory = { picker = Picker.ForCategory(row) },
                        onPickAccount = { picker = Picker.ForAccount(row) },
                        onAlwaysUseChange = { vm.setAlwaysUse(row.capture.id, it) },
                        onConfirm = { vm.confirm(row) },
                        onDismiss = { vm.dismiss(row.capture.id) },
                        // ADR-0008: a shared trip payment becomes a Trip expense, never a full personal Transaction.
                        onAddToTrip = activeTrip?.takeIf { !row.isIncome }?.let { trip -> { onAddToTrip(trip.tripId, row.capture.id) } }
                    )
                }
            }
        }
    }

    when (val p = picker) {
        is Picker.ForCategory -> OptionPickerSheet(
            title = "Category",
            options = (if (p.row.isIncome) incomeCategories else expenseCategories).map { PickerOption(it) },
            selectedKey = p.row.category,
            onSelect = { vm.setCategory(p.row.capture.id, it); picker = null },
            onDismiss = { picker = null }
        )
        is Picker.ForAccount -> OptionPickerSheet(
            title = p.row.capture.accountHint?.let { "Account for XX$it" } ?: "Account",
            options = accounts.map { PickerOption(it.id.toString(), it.accountName) },
            selectedKey = p.row.accountId?.toString(),
            onSelect = { vm.setAccount(p.row.capture.id, it.toLong()); picker = null },
            onDismiss = { picker = null },
            manageHint = p.row.capture.accountHint?.let { "We'll remember XX$it for next time." }
        )
        null -> Unit
    }

    if (showBulk) {
        BulkConfirmDialog(
            candidates = bulk,
            accountNames = accountNames,
            onConfirm = { chosen -> vm.confirmAll(chosen); showBulk = false },
            onDismiss = { showBulk = false }
        )
    }
}

@Composable
private fun CaptureCard(
    row: CaptureRowUi,
    accountName: String?,
    onPickCategory: () -> Unit,
    onPickAccount: () -> Unit,
    onAlwaysUseChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onAddToTrip: (() -> Unit)? = null
) {
    var showOriginal by remember { mutableStateOf(false) }
    val capture = row.capture
    val amountColor = if (row.isIncome) IncomeBlue else ExpenseOrange

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    (if (row.isIncome) "+" else "−") + formatRupeesExact(capture.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor
                )
            }
            Text(
                listOfNotNull(
                    whenText(capture.capturedAt, capture.txnTime),
                    capture.accountHint?.let { "XX$it" },
                    capture.channel.takeIf { it != "UNKNOWN" }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onPickCategory,
                    label = { Text(row.category ?: "Pick category", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
                AssistChip(
                    onClick = onPickAccount,
                    label = { Text(accountName ?: "Map account", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
            Text(
                text = when {
                    row.categoryPickedByUser -> "Your pick"
                    row.category == null -> "Needs a category"
                    row.band == ConfidenceBand.HIGH -> "High confidence"
                    else -> "Check this guess"
                } + (row.why?.takeUnless { row.categoryPickedByUser }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (row.canOfferAlwaysUse) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlwaysUseChange(!row.alwaysUse) }
                ) {
                    Checkbox(checked = row.alwaysUse, onCheckedChange = onAlwaysUseChange)
                    Text(
                        "Always use ${row.category} for ${row.title}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (showOriginal) {
                Text(
                    capture.rawText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showOriginal = !showOriginal }) {
                    Text(if (showOriginal) "Hide original" else "Original text")
                }
                Box(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                if (onAddToTrip != null) TextButton(onClick = onAddToTrip) { Text("Add to trip") }
                Button(onClick = onConfirm, enabled = row.canConfirm) { Text("Confirm") }
            }
        }
    }
}

/** Read-only preview of what "confirm all" will save; each row can be left out. */
@Composable
private fun BulkConfirmDialog(
    candidates: List<CaptureRowUi>,
    accountNames: Map<Long, String>,
    onConfirm: (List<CaptureRowUi>) -> Unit,
    onDismiss: () -> Unit
) {
    val excluded = remember { emptyList<Long>().toMutableStateList() }
    val chosen = candidates.filter { it.capture.id !in excluded }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm ${chosen.size} of ${candidates.size}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(candidates, key = { it.capture.id }) { row ->
                    val included = row.capture.id !in excluded
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (included) excluded.add(row.capture.id) else excluded.remove(row.capture.id) }
                    ) {
                        Checkbox(
                            checked = included,
                            onCheckedChange = { if (it) excluded.remove(row.capture.id) else excluded.add(row.capture.id) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${row.category} · ${accountNames[row.accountId].orEmpty()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(formatRupeesExact(row.capture.amount), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen) }, enabled = chosen.isNotEmpty()) {
                Text("Confirm ${formatRupeesExact(chosen.sumOf { it.capture.amount })}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
