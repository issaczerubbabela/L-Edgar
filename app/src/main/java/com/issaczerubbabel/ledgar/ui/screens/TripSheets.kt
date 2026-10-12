package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.issaczerubbabel.ledgar.trip.CheckRow
import com.issaczerubbabel.ledgar.trip.LedgerKind
import com.issaczerubbabel.ledgar.trip.MemberLedger
import com.issaczerubbabel.ledgar.trip.MemberPalette
import com.issaczerubbabel.ledgar.trip.PlannedPayment
import com.issaczerubbabel.ledgar.trip.TripMath
import com.issaczerubbabel.ledgar.trip.TripMember
import com.issaczerubbabel.ledgar.viewmodel.TripScreenState

/** Which sheet the Trip screen is showing. */
sealed interface TripSheet {
    data object Check : TripSheet
    data class Explain(val payment: PlannedPayment) : TripSheet
    data class Ledger(val memberId: Long) : TripSheet
    data object People : TripSheet
    data object Export : TripSheet
    data object PickPerson : TripSheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripBottomSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

// ------------------------------------------------------------------ the settle-up check

/** The proof that the Suggested payments settle everyone: each person ends at ₹0. */
@Composable
fun SettlementCheckList(state: TripScreenState, highlight: Set<Long> = emptySet()) {
    val members = state.detail.members.associateBy { it.id }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.check.forEach { row -> CheckRowItem(row, members[row.memberId] ?: return@forEach, row.memberId in highlight) }
    }
}

@Composable
private fun CheckRowItem(row: CheckRow, member: TripMember, highlighted: Boolean) {
    val settled = row.afterPaise == 0L
    val story = buildString {
        append(
            when {
                row.balancePaise > 0 -> "Is owed ${rupees(row.balancePaise)}"
                row.balancePaise < 0 -> "Owes ${rupees(-row.balancePaise)}"
                else -> "Even"
            }
        )
        if (row.paysPaise > 0) append(" · pays ${rupees(row.paysPaise)}")
        if (row.receivesPaise > 0) append(" · receives ${rupees(row.receivesPaise)}")
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else softFill())
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MemberAvatar(member, 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(member.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(story, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (settled) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = tripPositive(), modifier = Modifier.size(18.dp))
                Text(rupees(kotlin.math.abs(row.afterPaise)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (settled) tripPositive() else tripNegative())
            }
            Text(if (settled) "left" else "still open", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CheckSheet(state: TripScreenState, onDismiss: () -> Unit) {
    val everyoneSettled = state.check.all { it.afterPaise == 0L }
    TripBottomSheet(if (everyoneSettled) "These payments settle everyone" else "Some balances are still open", onDismiss) {
        Text(
            "Each person's balance is what they paid, minus their Shares, plus any payments already recorded. " +
                "Whoever owes pays; whoever is owed receives. If every row ends at ₹0, nobody is left out of pocket.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SettlementCheckList(state)
        if (state.plan.isNotEmpty()) {
            HorizontalDivider(color = hairline())
            Text("The ${state.plan.size} payment${if (state.plan.size == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val members = state.detail.members.associateBy { it.id }
            state.plan.forEach { p -> PaymentLine(members[p.fromId], members[p.toId], p.amountPaise) }
        }
    }
}

@Composable
private fun PaymentLine(from: TripMember?, to: TripMember?, paise: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        from?.let { MemberAvatar(it, 28.dp) }
        Text("${from?.name.orEmpty()} pays ${to?.name.orEmpty()}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(rupees(paise), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

// ------------------------------------------------------------------ one payment, explained

@Composable
fun ExplainSheet(state: TripScreenState, payment: PlannedPayment, onDismiss: () -> Unit) {
    val members = state.detail.members.associateBy { it.id }
    val from = members[payment.fromId]
    val to = members[payment.toId]
    TripBottomSheet("${from?.name.orEmpty()} pays ${to?.name.orEmpty()} ${rupees(payment.amountPaise)}", onDismiss) {
        Text(
            "The plan pairs whoever owes the most with whoever is owed the most. Here is where each balance comes from, then how this payment fits with everyone else's.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        listOfNotNull(from, to).forEach { member ->
            LedgerSection(member, state)
        }
        Text("Everyone after the payments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        SettlementCheckList(state, highlight = setOf(payment.fromId, payment.toId))
    }
}

@Composable
private fun LedgerSection(member: TripMember, state: TripScreenState) {
    val ledger = TripMath.memberLedger(member.id, state.detail.members, state.detail.expenses, state.settlementInputs)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MemberAvatar(member, 32.dp)
            Text(member.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        LedgerLines(ledger)
    }
}

/** How each expense and payment moved one balance, ending in the balance itself. */
@Composable
fun LedgerLines(ledger: MemberLedger) {
    Column {
        if (ledger.lines.isEmpty()) {
            Text("Nothing recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ledger.lines.forEach { line ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(line.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(line.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    (if (line.deltaPaise >= 0) "+" else "−") + rupees(kotlin.math.abs(line.deltaPaise)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (line.deltaPaise >= 0) tripPositive() else tripNegative()
                )
            }
            HorizontalDivider(color = hairline())
        }
        val net = ledger.netPaise
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Balance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                (if (net > 0) "+" else if (net < 0) "−" else "") + rupees(kotlin.math.abs(net)) + if (net > 0) " · gets back" else if (net < 0) " · owes" else " · settled",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (net > 0) tripPositive() else if (net < 0) tripNegative() else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun LedgerSheet(state: TripScreenState, memberId: Long, onDismiss: () -> Unit) {
    val member = state.detail.members.firstOrNull { it.id == memberId } ?: return
    TripBottomSheet("${member.name}'s balance", onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MemberAvatar(member, 44.dp)
            Text("Paid minus Share for each expense, plus payments between friends.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LedgerLines(TripMath.memberLedger(memberId, state.detail.members, state.detail.expenses, state.settlementInputs))
    }
}

// ------------------------------------------------------------------ people

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeopleSheet(
    members: List<TripMember>,
    readOnly: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onUpdate: (memberId: Long, name: String, upiId: String?, colorIndex: Int) -> Unit,
    onRemove: (Long) -> Unit
) {
    var newName by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    TripBottomSheet("People", onDismiss) {
        members.forEach { m ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !readOnly, role = Role.Button) { editingId = m.id }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MemberAvatar(m, 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(m.upiId ?: "No UPI ID", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!readOnly) Text("Edit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (!readOnly) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Add someone") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onAdd(newName); newName = "" }, enabled = newName.isNotBlank()) {
                    Icon(Icons.Filled.Add, contentDescription = "Add person")
                }
            }
        }
    }
    val editing = members.firstOrNull { it.id == editingId }
    if (editing != null) {
        EditPersonDialog(
            member = editing,
            onDismiss = { editingId = null },
            onSave = { name, upi, colour -> onUpdate(editing.id, name, upi, colour); editingId = null },
            onRemove = { onRemove(editing.id); editingId = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditPersonDialog(
    member: TripMember,
    onDismiss: () -> Unit,
    onSave: (String, String?, Int) -> Unit,
    onRemove: () -> Unit
) {
    var name by remember(member.id) { mutableStateOf(member.name) }
    var upi by remember(member.id) { mutableStateOf(member.upiId.orEmpty()) }
    var colour by remember(member.id) { mutableIntStateOf(member.colorIndex) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${member.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (!member.isSelf) OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    upi, { upi = it },
                    label = { Text("UPI ID") },
                    placeholder = { Text("name@okaxis") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MemberPalette.colors.indices.forEach { i ->
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .clickable(role = Role.RadioButton) { colour = i },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(memberColorAt(i))
                                    .let { if (colour == i) it.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else it }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, upi, colour) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (!member.isSelf) TextButton(onClick = onRemove) { Text("Remove", color = tripNegative()) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

// ------------------------------------------------------------------ export

@Composable
fun ExportSheet(
    onDismiss: () -> Unit,
    onSummary: () -> Unit,
    onStatement: () -> Unit,
    onExpensesCsv: () -> Unit,
    onBalancesCsv: () -> Unit,
    onPdf: () -> Unit
) {
    TripBottomSheet("Export", onDismiss) {
        Column {
            ExportOption(Icons.Filled.Description, "Summary text", "Totals, each Share and who pays whom, for the group chat", onSummary)
            ExportOption(Icons.Filled.Person, "Statement for one person", "Only their lines and what they pay or get back", onStatement)
            ExportOption(Icons.Filled.TableChart, "Expenses CSV", "Every expense with its Category and a Share column per person", onExpensesCsv)
            ExportOption(Icons.Filled.TableChart, "Balances and Settlements CSV", "One row per person, then the recorded Settlements", onBalancesCsv)
            ExportOption(Icons.Filled.PictureAsPdf, "PDF trip report", "People, expenses and the settle-up plan on a few pages", onPdf)
        }
    }
}

@Composable
private fun ExportOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(softFill()), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PickPersonSheet(members: List<TripMember>, onPick: (TripMember) -> Unit, onDismiss: () -> Unit) {
    TripBottomSheet("Statement for…", onDismiss) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            members.forEach { m -> MemberChip(m, selected = false, onClick = { onPick(m) }) }
        }
    }
}
