package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.bucket.BucketSummary
import com.issaczerubbabel.ledgar.data.bucket.CycleSummary
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.ui.components.BucketColorDot
import com.issaczerubbabel.ledgar.ui.components.OverBudgetFlag
import com.issaczerubbabel.ledgar.ui.components.PaceBar
import com.issaczerubbabel.ledgar.ui.components.SectionLabel
import com.issaczerubbabel.ledgar.ui.components.tabularNumbers
import com.issaczerubbabel.ledgar.ui.theme.ExpenseRed
import com.issaczerubbabel.ledgar.ui.theme.bucketColor
import com.issaczerubbabel.ledgar.ui.theme.categoryDotColor
import com.issaczerubbabel.ledgar.util.amountToInput
import com.issaczerubbabel.ledgar.util.formatRupees
import com.issaczerubbabel.ledgar.util.parseAmountInput
import com.issaczerubbabel.ledgar.viewmodel.BucketDetailUiState
import com.issaczerubbabel.ledgar.viewmodel.BucketDetailViewModel
import com.issaczerubbabel.ledgar.viewmodel.CategoryChoice
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun BucketDetailScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    vm: BucketDetailViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    // A deleted bucket has nothing left to show.
    LaunchedEffect(state.notFound) { if (state.notFound) onBack() }

    val bucket = state.bucket
    val cycle = state.cycle
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(
                text = bucket?.bucket?.name ?: "Bucket",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (bucket != null && cycle?.cycle?.closedAt == null) {
                TextButton(onClick = { editing = true }) { Text("Edit") }
            }
        }

        if (bucket == null || cycle == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            DetailContent(bucket = bucket, cycle = cycle)
        }
    }

    if (editing && bucket != null) {
        BucketEditorSheet(
            bucket = bucket.bucket,
            choices = state.choices,
            onDismiss = { editing = false },
            onSave = { name, note, emoji, colorIndex, amount, categories ->
                vm.save(name, note, emoji, colorIndex, amount, categories)
                editing = false
            },
            onDelete = {
                editing = false
                vm.delete()
            }
        )
    }
}

@Composable
private fun DetailContent(bucket: BucketSummary, cycle: CycleSummary) {
    val running = cycle.cycle.closedAt == null
    val allocated = bucket.bucket.allocatedAmount
    val percent = if (bucket.spentFraction.isInfinite()) null else (bucket.spentFraction * 100).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionLabel("Spent")
            Text(
                text = formatRupees(bucket.spent),
                style = tabularNumbers(MaterialTheme.typography.displaySmall),
                color = if (bucket.isOver) ExpenseRed else MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "of ${formatRupees(allocated)}",
                    style = tabularNumbers(MaterialTheme.typography.bodyMedium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (bucket.isOver) {
                    OverBudgetFlag(overBy = abs(bucket.remaining))
                } else {
                    Text(
                        text = "· ${formatRupees(bucket.remaining)} left",
                        style = tabularNumbers(MaterialTheme.typography.bodyMedium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PaceBar(
                spentFraction = bucket.spentFraction,
                paceFraction = cycle.elapsedFraction.takeIf { running },
                color = bucketColor(bucket.bucket.colorIndex),
                isOver = bucket.isOver,
                modifier = Modifier.semantics {
                    contentDescription = "${percent?.let { "$it%" } ?: "Nothing allocated"} of ${bucket.bucket.name} spent"
                }
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (running && allocated > 0.0) {
                        "Even pace would be ${formatRupees(allocated * cycle.elapsedFraction)} by today"
                    } else {
                        ""
                    },
                    style = tabularNumbers(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = percent?.let { "$it%" } ?: "—",
                    style = tabularNumbers(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (bucket.bucket.note.isNotBlank()) {
            Text(
                text = bucket.bucket.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionLabel("Categories in this bucket")
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "No sub-limits",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        if (bucket.categories.isEmpty()) {
            Text(
                text = if (running) {
                    "Nothing spent here yet. Use Edit to choose which categories go into this bucket."
                } else {
                    "Nothing was spent in this bucket."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column {
                bucket.categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(categoryDotColor(category.category), CircleShape)
                        )
                        Text(
                            text = category.category,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatRupees(category.amount),
                            style = tabularNumbers(MaterialTheme.typography.bodyLarge)
                        )
                        Text(
                            text = if (bucket.spent > 0) "${(category.amount / bucket.spent * 100).roundToInt()}%" else "",
                            style = tabularNumbers(MaterialTheme.typography.bodySmall),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        if (running) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text = "Nothing rolls over", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "This resets when you start the next cycle. An overspend does not follow you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val COLOR_NAMES = listOf("Cyan", "Red", "Green", "Orange", "Purple", "Yellow", "Blue", "Pink", "Brown")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BucketEditorSheet(
    bucket: BudgetBucket,
    choices: List<CategoryChoice>,
    onDismiss: () -> Unit,
    onSave: (name: String, note: String, emoji: String, colorIndex: Int, amount: Double, categories: Set<String>) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(bucket.name) }
    var note by remember { mutableStateOf(bucket.note) }
    var emoji by remember { mutableStateOf(bucket.emoji) }
    var colorIndex by remember { mutableStateOf(bucket.colorIndex) }
    var amountInput by remember { mutableStateOf(amountToInput(bucket.allocatedAmount)) }
    val selected = remember {
        choices.filter { it.bucketId == bucket.id }.map { it.name }.toMutableStateList()
    }
    var confirmingDelete by remember { mutableStateOf(false) }

    val amount = if (amountInput.isBlank()) 0.0 else parseAmountInput(amountInput)
    val canSave = name.isNotBlank() && amount != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Edit bucket", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                isError = name.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(4) },
                    label = { Text("Emoji") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Allocated") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    isError = amount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(2f)
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note") },
                placeholder = { Text("What this bucket is for") },
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Colour")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(BudgetBucket.COLOR_COUNT) { index ->
                        val isSelected = index == colorIndex
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(bucketColor(index), CircleShape)
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { colorIndex = index }
                                )
                                .semantics { contentDescription = COLOR_NAMES.getOrElse(index) { "Colour ${index + 1}" } },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.background)
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("Categories")
                Text(
                    text = "A category can only be in one bucket. Choosing one from another bucket moves it here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                choices.forEach { choice ->
                    val checked = choice.name in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { on -> if (on) selected.add(choice.name) else selected.remove(choice.name) }
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = choice.name, style = MaterialTheme.typography.bodyLarge)
                            val elsewhere = choice.bucketId != null && choice.bucketId != bucket.id
                            if (elsewhere) {
                                Text(
                                    text = if (checked) "Will move here from ${choice.bucketName}" else "Currently in ${choice.bucketName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { amount?.let { onSave(name, note, emoji, colorIndex, it, selected.toSet()) } },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) { Text("Save") }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel") }
            TextButton(
                onClick = { confirmingDelete = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("Delete bucket", color = ExpenseRed) }
            Box(modifier = Modifier.size(16.dp))
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete ${bucket.name}?") },
            text = {
                Text("Its categories go back to Unbucketed. Your transactions are not touched, only this bucket.")
            },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) { Text("Delete", color = ExpenseRed) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } }
        )
    }
}
