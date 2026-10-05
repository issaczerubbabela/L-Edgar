package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.trip.SplitMode
import com.issaczerubbabel.ledgar.ui.components.SingleDatePickerDialog
import com.issaczerubbabel.ledgar.viewmodel.TripExpenseScreenState
import com.issaczerubbabel.ledgar.viewmodel.TripExpenseViewModel
import com.issaczerubbabel.ledgar.viewmodel.paiseToInput
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripExpenseScreen(
    innerPadding: PaddingValues,
    onDone: () -> Unit,
    vm: TripExpenseViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    var pickingDate by remember { mutableStateOf(false) }
    LaunchedEffect(done) { if (done) onDone() }
    val detail = s.detail

    Scaffold(
        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (s.isEditing) "Edit expense" else "New expense", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(detail?.trip?.name.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Close") } },
                actions = {
                    if (s.isEditing) TextButton(onClick = vm::delete, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Delete", color = tripNegative())
                    }
                }
            )
        }
    ) { padding ->
        if (detail == null) return@Scaffold
        TripContentWidth {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = s.form.amount,
                        onValueChange = vm::setAmount,
                        label = { Text("Amount") },
                        prefix = { Text("₹", style = MaterialTheme.typography.headlineSmall) },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = s.form.purpose,
                        onValueChange = vm::setPurpose,
                        label = { Text("Purpose") },
                        placeholder = { Text("Dinner at Britto's") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Column {
                        Text("Paid by", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            detail.members.forEach { m ->
                                MemberChip(m, selected = s.form.payerId == m.id, onClick = { vm.setPayer(m.id) }, radio = true)
                            }
                        }
                    }
                }
                item {
                    TripCard {
                        Text("Split", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf(SplitMode.EQUAL to "Equal", SplitMode.CUSTOM to "Custom").forEachIndexed { i, (mode, label) ->
                                SegmentedButton(
                                    selected = s.form.mode == mode,
                                    onClick = { vm.setMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(i, 2)
                                ) { Text(label) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        detail.members.forEach { m ->
                            val included = m.id in s.form.memberIds
                            val locked = m.id in s.form.locked
                            val share = s.shares[m.id] ?: 0L
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 64.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(Modifier.weight(1f)) {
                                    MemberChip(m, selected = included, onClick = { vm.toggleMember(m.id) })
                                }
                                if (!included) {
                                    Text("not in it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else if (s.form.mode == SplitMode.CUSTOM) {
                                    OutlinedTextField(
                                        value = if (locked) s.form.locked.getValue(m.id) else paiseToInput(share),
                                        onValueChange = { vm.setLocked(m.id, it) },
                                        prefix = { Text("₹") },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (locked) FontWeight.Bold else FontWeight.Normal,
                                            color = if (locked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.End
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.width(120.dp)
                                    )
                                    IconButton(onClick = { vm.unlock(m.id) }, enabled = locked) {
                                        Icon(
                                            if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                            contentDescription = if (locked) "Unlock ${m.name}'s amount" else "${m.name}'s amount follows the rest"
                                        )
                                    }
                                } else {
                                    Text(rupees(share), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        if (s.form.mode == SplitMode.CUSTOM) {
                            Spacer(Modifier.height(8.dp))
                            RemainderNote(s)
                        }
                    }
                }
                item {
                    TripDropdown(
                        label = "Category (optional now, needed to post)",
                        selectedText = s.form.category.ifEmpty { "No Category yet" },
                        options = listOf("" to "No Category yet") + s.categories.map { it to it },
                        onSelect = vm::setCategory
                    )
                }
                item {
                    Column {
                        OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text("Date: ${dayLabel(s.form.date)}")
                        }
                        if (s.outsideTrip) {
                            Text(
                                "This date is outside the trip. Save anyway if that's right.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                            )
                        }
                        if (s.fromCapture) {
                            Text(
                                "From an auto-capture alert. It leaves the inbox when you save.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                            )
                        }
                    }
                }
                item {
                    Column {
                        val problem = s.problem
                        Text(
                            problem ?: "Your Share: ${rupees(detail.self?.let { s.shares[it.id] } ?: 0L)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = vm::save, enabled = problem == null, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Save") }
                    }
                }
            }
        }
    }

    if (pickingDate) {
        SingleDatePickerDialog(
            initialDate = runCatching { LocalDate.parse(s.form.date) }.getOrDefault(LocalDate.now()),
            onDismiss = { pickingDate = false },
            onConfirm = { vm.setDate(it.toString()); pickingDate = false }
        )
    }
}

/** Says in words what a Custom split is doing, so locking an amount never feels like magic. */
@Composable
private fun RemainderNote(s: TripExpenseScreenState) {
    val detail = s.detail ?: return
    val names = detail.members.associateBy { it.id }
    val free = s.freeMemberIds
    val left = s.amountPaise - s.lockedTotalPaise
    val problem = s.problem
    val text = when {
        s.input.locked.isEmpty() -> "Type anyone's amount to lock it. Everyone else splits the rest equally."
        left < 0 -> "${rupees(-left)} more than the total. Lower a locked amount."
        free.isEmpty() -> if (left == 0L) "Everyone is locked and it adds up." else (problem ?: "")
        else -> "${rupees(left)} left, split equally between ${free.joinToString(", ") { names[it]?.name.orEmpty() }}"
    }
    val bad = left < 0 || (free.isEmpty() && left != 0L)
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
    )
}
