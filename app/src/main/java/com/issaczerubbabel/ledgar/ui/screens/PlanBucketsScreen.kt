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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.bucket.CycleSummary
import com.issaczerubbabel.ledgar.ui.components.BucketColorDot
import com.issaczerubbabel.ledgar.ui.components.SectionLabel
import com.issaczerubbabel.ledgar.ui.components.tabularNumbers
import com.issaczerubbabel.ledgar.ui.theme.ExpenseRed
import com.issaczerubbabel.ledgar.ui.theme.IncomeGreen
import com.issaczerubbabel.ledgar.util.amountToInput
import com.issaczerubbabel.ledgar.util.formatRupees
import com.issaczerubbabel.ledgar.util.parseAmountInput
import com.issaczerubbabel.ledgar.viewmodel.PlanBucketsUiState
import com.issaczerubbabel.ledgar.viewmodel.PlanBucketsViewModel

private const val ALLOCATION_STEP = 500.0

@Composable
fun PlanBucketsScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenBucket: (Long) -> Unit,
    onStartCycle: () -> Unit,
    vm: PlanBucketsViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var editingSpendable by remember { mutableStateOf(false) }
    var editingBucketId by remember { mutableStateOf<Long?>(null) }
    var addingBucket by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(text = "Plan your buckets", style = MaterialTheme.typography.titleLarge)
        }

        val summary = state.summary
        when {
            state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            summary == null -> NoCycleYet(onStartCycle = onStartCycle)
            else -> PlanContent(
                state = state,
                summary = summary,
                onStep = { id, delta -> vm.adjustAllocation(id, delta) },
                onEditAmount = { editingBucketId = it },
                onEditSpendable = { editingSpendable = true },
                onOpenBucket = onOpenBucket,
                onAddBucket = { addingBucket = true }
            )
        }

        if (editingSpendable && summary != null) {
            AmountDialog(
                title = "Spendable this cycle",
                initial = summary.cycle.spendableAmount,
                onDismiss = { editingSpendable = false },
                onConfirm = { vm.setSpendable(it); editingSpendable = false }
            )
        }
        editingBucketId?.let { id ->
            val bucket = summary?.buckets?.firstOrNull { it.bucket.id == id }?.bucket
            if (bucket != null) {
                AmountDialog(
                    title = bucket.name,
                    initial = bucket.allocatedAmount,
                    onDismiss = { editingBucketId = null },
                    onConfirm = { vm.setAllocation(id, it); editingBucketId = null }
                )
            }
        }
        if (addingBucket) {
            NameDialog(
                onDismiss = { addingBucket = false },
                onConfirm = { vm.addBucket(it); addingBucket = false }
            )
        }
    }
}

@Composable
private fun PlanContent(
    state: PlanBucketsUiState,
    summary: CycleSummary,
    onStep: (Long, Double) -> Unit,
    onEditAmount: (Long) -> Unit,
    onEditSpendable: () -> Unit,
    onOpenBucket: (Long) -> Unit,
    onAddBucket: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { AllocationSummary(summary = summary, onEditSpendable = onEditSpendable) }

        item { SectionLabel("Buckets") }

        items(summary.buckets, key = { it.bucket.id }) { row ->
            val bucket = row.bucket
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClickLabel = "Open ${bucket.name}") { onOpenBucket(bucket.id) }
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BucketColorDot(bucket.colorIndex, modifier = Modifier.padding(top = 7.dp))
                            if (bucket.emoji.isNotBlank()) {
                                Text(bucket.emoji, modifier = Modifier.clearAndSetSemantics { })
                            }
                            Text(
                                text = bucket.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        val count = state.categoryCountByBucket[bucket.id] ?: 0
                        Text(
                            text = "$count ${if (count == 1) "category" else "categories"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 18.dp)
                        )
                    }
                    AllocationStepper(
                        name = bucket.name,
                        amount = bucket.allocatedAmount,
                        onDecrease = { onStep(bucket.id, -ALLOCATION_STEP) },
                        onIncrease = { onStep(bucket.id, ALLOCATION_STEP) },
                        onEditAmount = { onEditAmount(bucket.id) }
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        item { UnbucketedRow(summary = summary) }

        item {
            OutlinedButton(onClick = onAddBucket, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(text = "New bucket", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/**
 * The figure that matters while planning. Allocation is soft: over-allocating turns it red and says
 * so, but never stops you saving, because planning usually means moving money between two buckets.
 */
@Composable
private fun AllocationSummary(summary: CycleSummary, onEditSpendable: () -> Unit) {
    val unallocated = summary.unallocated
    val tone = when {
        unallocated < 0 -> ExpenseRed
        unallocated > 0 -> IncomeGreen
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionLabel(if (unallocated < 0) "Over-allocated" else "Unallocated")
                Text(
                    text = formatRupees(unallocated),
                    style = tabularNumbers(MaterialTheme.typography.headlineMedium),
                    color = tone
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.clickable(onClickLabel = "Change spendable amount", onClick = onEditSpendable)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLabel("Spendable")
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatRupees(summary.cycle.spendableAmount),
                    style = tabularNumbers(MaterialTheme.typography.titleMedium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = if (unallocated < 0) {
                "You have given out ${formatRupees(-unallocated)} more than you have. You can still save."
            } else {
                "Leave some spare or give it a job. Nothing here is blocked."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AllocationStepper(
    name: String,
    amount: Double,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onEditAmount: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onDecrease, enabled = amount > 0.0) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease $name by ${formatRupees(ALLOCATION_STEP)}")
        }
        Box(
            modifier = Modifier
                .widthIn(min = 92.dp)
                .heightIn(min = 48.dp)
                .clickable(onClickLabel = "Type an amount for $name", onClick = onEditAmount)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formatRupees(amount),
                style = tabularNumbers(MaterialTheme.typography.bodyLarge),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        FilledTonalIconButton(onClick = onIncrease) {
            Icon(Icons.Filled.Add, contentDescription = "Increase $name by ${formatRupees(ALLOCATION_STEP)}")
        }
    }
}

@Composable
private fun UnbucketedRow(summary: CycleSummary) {
    val count = summary.unbucketed.size
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(text = "Unbucketed", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (count == 0) {
                    "Every category with spending is in a bucket"
                } else {
                    summary.unbucketed.take(3).joinToString(", ") { it.category } +
                        if (count > 3) " and ${count - 3} more" else ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "System",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun NoCycleYet(onStartCycle: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "There is no cycle to plan yet", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            text = "Start a cycle with the amount you have to spend, then split it into buckets here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(onClick = onStartCycle) { Text("Start a cycle") }
    }
}

@Composable
private fun AmountDialog(title: String, initial: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    // A zero allocation starts blank, and any existing figure is pre-selected, so typing replaces it.
    val startText = if (initial == 0.0) "" else amountToInput(initial)
    var field by remember { mutableStateOf(TextFieldValue(startText, TextRange(0, startText.length))) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val input = field.text
    val parsed = parseAmountInput(input)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it.copy(text = it.text.filter { c -> c.isDigit() || c == '.' }) },
                label = { Text("Amount") },
                prefix = { Text("₹") },
                singleLine = true,
                isError = input.isNotBlank() && parsed == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
        },
        confirmButton = { TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NameDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New bucket") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("Groceries, Fun, Travel") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
