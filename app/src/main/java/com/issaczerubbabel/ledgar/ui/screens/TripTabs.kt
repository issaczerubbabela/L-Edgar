package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.issaczerubbabel.ledgar.trip.PlannedPayment
import com.issaczerubbabel.ledgar.trip.SplitMode
import com.issaczerubbabel.ledgar.trip.TripExpenseInput
import com.issaczerubbabel.ledgar.trip.TripMember
import com.issaczerubbabel.ledgar.viewmodel.TripScreenState

private fun signed(paise: Long) = (if (paise > 0) "+" else if (paise < 0) "−" else "") + rupees(kotlin.math.abs(paise))

// ------------------------------------------------------------------ Summary

@OptIn(ExperimentalMaterial3Api::class)
fun LazyListScope.summaryItems(
    s: TripScreenState,
    justMe: Boolean,
    onJustMe: (Boolean) -> Unit,
    onOpenTab: (Int) -> Unit
) {
    val members = s.detail.members
    val byId = members.associateBy { it.id }

    item(key = "hero") {
        val net = s.myBalance?.netPaise ?: 0L
        TripCard {
            Text("Trip total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(rupees(s.totalPaise), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                "${s.detail.expenses.size} expense${if (s.detail.expenses.size == 1) "" else "s"} · ${rupees(s.averagePerMemberPaise)} per person on average",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            TwoUp(
                first = { m ->
                    SoftTile(m) {
                        Text("Your Share", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(rupees(s.myShareTotalPaise), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                },
                second = { m ->
                    SoftTile(m) {
                        Text(if (net >= 0) "You get back" else "You owe", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            rupees(kotlin.math.abs(net)),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (net > 0) tripPositive() else if (net < 0) tripNegative() else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    }

    if (s.uncategorised > 0 || (s.plan.isNotEmpty() && !s.isArchived)) {
        item(key = "attention") {
            TripCard(contentPadding = PaddingValues(vertical = 8.dp)) {
                Text("Needs attention", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 18.dp, top = 10.dp, bottom = 4.dp))
                if (s.uncategorised > 0) {
                    AttentionRow("${s.uncategorised}", "Expense${if (s.uncategorised == 1) "" else "s"} without a Category", "Needed before you post", warn = true) { onOpenTab(1) }
                }
                if (s.plan.isNotEmpty() && !s.isArchived) {
                    AttentionRow("${s.plan.size}", "Payment${if (s.plan.size == 1) "" else "s"} still open", "Settle up shows who pays whom", warn = false) { onOpenTab(3) }
                }
            }
        }
    }

    item(key = "categories") {
        TripCard {
            Text("Where the money went", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false to "Everyone", true to "Just me").forEachIndexed { i, (me, label) ->
                    SegmentedButton(
                        selected = justMe == me,
                        onClick = { onJustMe(me) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2)
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(20.dp))
            CategoryChart(if (justMe) s.categoriesMine else s.categoriesAll)
            if (justMe) {
                Spacer(Modifier.height(14.dp))
                Text("This is what Post adds to your ledger, by Category.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    item(key = "paid-vs-share") {
        TripCard {
            Text("Paid vs Share", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            PaidVsShareChart(members, s.balances)
        }
    }

    item(key = "by-day") {
        TripCard {
            Text("By day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            DayChart(s.days)
        }
    }

    if (s.biggest.isNotEmpty()) {
        item(key = "biggest") {
            TripCard(contentPadding = PaddingValues(vertical = 8.dp)) {
                Text("Biggest expenses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 18.dp, top = 10.dp, bottom = 4.dp))
                s.biggest.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(color = hairline(), modifier = Modifier.padding(horizontal = 18.dp))
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        byId[e.payerId]?.let { MemberAvatar(it, 32.dp) }
                        Column(Modifier.weight(1f)) {
                            Text(e.purpose, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${byId[e.payerId]?.name.orEmpty()} paid · ${dayLabel(e.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(rupees(e.amountPaise), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttentionRow(count: String, title: String, subtitle: String, warn: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Tag(count, warn = warn)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

// ------------------------------------------------------------------ Expenses

fun LazyListScope.expensesItems(s: TripScreenState, onEdit: (Long) -> Unit) {
    val byId = s.detail.members.associateBy { it.id }
    val days = s.detail.expenses.sortedWith(compareBy({ it.date }, { it.id })).groupBy { it.date }
    if (days.isEmpty()) {
        item(key = "empty") {
            TripCard {
                Text("No expenses yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Tap + Expense to log the first one.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    days.forEach { (date, expenses) ->
        item(key = "day-$date") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(dayLabel(date), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(rupees(expenses.sumOf { it.amountPaise }), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TripCard(contentPadding = PaddingValues(0.dp)) {
                    expenses.forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(color = hairline(), modifier = Modifier.padding(horizontal = 16.dp))
                        ExpenseRow(e, byId[e.payerId], s.myShares[e.id] ?: 0L, s.detail.members.size, byId, !s.isArchived) { onEdit(e.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(
    e: TripExpenseInput,
    payer: TripMember?,
    myShare: Long,
    memberCount: Int,
    byId: Map<Long, TripMember>,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val split = when {
        e.mode == SplitMode.CUSTOM -> "Custom split"
        e.memberIds.size == memberCount -> "Everyone"
        else -> e.memberIds.joinToString(", ") { byId[it]?.name.orEmpty() }
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        payer?.let { MemberAvatar(it, 40.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(e.purpose, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${payer?.name.orEmpty()} paid · $split", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (e.category.isBlank()) Tag("No Category", warn = true, modifier = Modifier.padding(top = 4.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(rupees(e.amountPaise), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(if (myShare > 0) "you ${rupees(myShare)}" else "not in it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------ Balances

fun LazyListScope.balancesItems(s: TripScreenState, onOpenLedger: (Long) -> Unit, onPeople: () -> Unit) {
    item(key = "balances-intro") {
        Text(
            "Net = paid − Share ± payments between friends. Green gets money back, red owes. Tap a person to see why.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
    items(s.balances.size, key = { s.balances[it].memberId }) { index ->
        val b = s.balances[index]
        val member = s.detail.members.firstOrNull { it.id == b.memberId } ?: return@items
        val net = b.netPaise
        TripCard(onClick = { onOpenLedger(member.id) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MemberAvatar(member, 48.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(member.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "paid ${rupees(b.paidPaise)} · Share ${rupees(b.sharePaise)}" + if (b.settledPaise != 0L) " · settled ${rupees(kotlin.math.abs(b.settledPaise))}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        signed(net),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (net > 0) tripPositive() else if (net < 0) tripNegative() else MaterialTheme.colorScheme.onSurface
                    )
                    Text(if (net > 0) "gets back" else if (net < 0) "owes" else "settled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    item(key = "balances-people") {
        OutlinedButton(onClick = onPeople, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (s.isArchived) "People" else "Add or edit people")
        }
    }
}

// ------------------------------------------------------------------ Settle up

fun LazyListScope.settleItems(
    s: TripScreenState,
    onCheck: () -> Unit,
    onExplain: (PlannedPayment) -> Unit,
    onMarkPaid: (PlannedPayment) -> Unit,
    onUndo: (Long) -> Unit,
    onPay: (PlannedPayment) -> Unit,
    onReview: () -> Unit,
    onUnarchive: () -> Unit
) {
    val byId = s.detail.members.associateBy { it.id }
    val selfId = s.detail.self?.id

    item(key = "check") {
        val everyoneSettled = s.check.all { it.afterPaise == 0L }
        TripCard(onClick = onCheck, containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val title = when {
                        s.plan.isEmpty() -> "Everyone is settled"
                        everyoneSettled -> "Does this settle everyone? Yes."
                        else -> "Some balances are still open"
                    }
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        if (s.plan.isEmpty()) "No payments left. Tap to see the balances."
                        else "${s.plan.size} payment${if (s.plan.size == 1) "" else "s"} bring all ${s.detail.members.size} balances to ₹0. Tap to see the check.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }

    items(s.plan.size, key = { "pay-${s.plan[it].fromId}-${s.plan[it].toId}" }) { index ->
        val p = s.plan[index]
        val from = byId[p.fromId]
        val to = byId[p.toId]
        TripCard(onClick = { onExplain(p) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                from?.let { MemberAvatar(it, 36.dp) }
                Column(Modifier.weight(1f)) {
                    Text(from?.name.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("pays", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                to?.let { MemberAvatar(it, 36.dp) }
                Column(Modifier.weight(1f)) {
                    Text(to?.name.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("receives", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(rupees(p.amountPaise), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
            Text("Tap to see how this adds up", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!s.isArchived) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (p.fromId == selfId && !to?.upiId.isNullOrBlank()) {
                        TextButton(onClick = { onPay(p) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Pay via UPI") }
                    }
                    Button(onClick = { onMarkPaid(p) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Mark paid") }
                }
            }
        }
    }

    if (s.detail.settlements.isNotEmpty()) {
        item(key = "settlements-title") {
            Text("Recorded payments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
        }
        item(key = "settlements") {
            TripCard(contentPadding = PaddingValues(0.dp)) {
                s.detail.settlements.forEachIndexed { i, st ->
                    if (i > 0) HorizontalDivider(color = hairline(), modifier = Modifier.padding(horizontal = 16.dp))
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        byId[st.fromMemberId]?.let { MemberAvatar(it, 32.dp) }
                        Column(Modifier.weight(1f)) {
                            Text("${byId[st.fromMemberId]?.name.orEmpty()} paid ${byId[st.toMemberId]?.name.orEmpty()}", style = MaterialTheme.typography.bodyMedium)
                            Text("Doesn't count as spending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(rupees(st.amountPaise), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            if (!s.isArchived) TextButton(onClick = { onUndo(st.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Undo") }
                        }
                    }
                }
            }
        }
    }

    item(key = "post") {
        TripCard {
            if (s.isArchived) {
                Text("Posted ${s.detail.postedCount} transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "This trip is archived and read-only. Un-archiving deletes those transactions so you can fix the trip and post again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                OutlinedButton(onClick = onUnarchive, modifier = Modifier.padding(top = 14.dp).heightIn(min = 48.dp)) { Text("Un-archive") }
            } else {
                Text("Post your Shares", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Adds your ${rupees(s.myShareTotalPaise)} to your ledger as one transaction per expense, then archives the trip.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (s.plan.isNotEmpty()) {
                    Text(
                        "${s.plan.size} payment${if (s.plan.size == 1) "" else "s"} still open. You can post anyway; posting only records your Shares.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                Button(
                    onClick = onReview,
                    enabled = s.detail.expenses.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 52.dp)
                ) { Text("Review & Post") }
            }
        }
    }
}
