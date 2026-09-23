package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.bucket.CycleSummary
import com.issaczerubbabel.ledgar.ui.components.BucketRow
import com.issaczerubbabel.ledgar.ui.components.PaceBar
import com.issaczerubbabel.ledgar.ui.components.SectionLabel
import com.issaczerubbabel.ledgar.ui.components.StatCard
import com.issaczerubbabel.ledgar.ui.components.tabularNumbers
import com.issaczerubbabel.ledgar.ui.theme.ExpenseOrange
import com.issaczerubbabel.ledgar.ui.theme.ExpenseRed
import com.issaczerubbabel.ledgar.util.formatRupees
import com.issaczerubbabel.ledgar.viewmodel.BudgetUiState
import com.issaczerubbabel.ledgar.viewmodel.BudgetViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BudgetHomeScreen(
    innerPadding: PaddingValues,
    onOpenBucket: (Long) -> Unit,
    onPlanBuckets: () -> Unit,
    onStartCycle: () -> Unit,
    vm: BudgetViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    BudgetHomeContent(
        state = state,
        innerPadding = innerPadding,
        onOlder = vm::showOlderCycle,
        onNewer = vm::showNewerCycle,
        onOpenBucket = onOpenBucket,
        onPlanBuckets = onPlanBuckets,
        onStartCycle = onStartCycle
    )
}

@Composable
private fun BudgetHomeContent(
    state: BudgetUiState,
    innerPadding: PaddingValues,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onOpenBucket: (Long) -> Unit,
    onPlanBuckets: () -> Unit,
    onStartCycle: () -> Unit
) {
    val summary = state.summary

    when {
        state.isLoading -> Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        summary == null -> EmptyBudget(innerPadding = innerPadding, onStartCycle = onStartCycle)

        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(text = "Budget", style = MaterialTheme.typography.headlineMedium)
            }
            item {
                CycleHeader(
                    summary = summary,
                    isRunning = state.isRunning,
                    canGoOlder = state.canGoOlder,
                    canGoNewer = state.canGoNewer,
                    onOlder = onOlder,
                    onNewer = onNewer
                )
            }
            if (summary.isOverdue && state.isRunning) {
                item { OverdueBanner(summary = summary, onStartCycle = onStartCycle) }
            }
            item { Hero(summary = summary) }
            item { StatRow(summary = summary, isRunning = state.isRunning) }

            item { SectionLabel("Buckets") }
            if (summary.buckets.isEmpty()) {
                item { NoBucketsCard(isRunning = state.isRunning) }
            } else {
                items(summary.buckets, key = { it.bucket.id }) { bucket ->
                    Column {
                        BucketRow(
                            summary = bucket,
                            paceFraction = summary.elapsedFraction.takeIf { state.isRunning },
                            onClick = { onOpenBucket(bucket.bucket.id) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            if (summary.unbucketed.isNotEmpty()) {
                item { UnbucketedNudge(summary = summary, isRunning = state.isRunning, onClick = onPlanBuckets) }
            }

            if (state.isRunning) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onPlanBuckets, modifier = Modifier.weight(1f)) {
                            Text("Plan buckets")
                        }
                        Button(onClick = onStartCycle, modifier = Modifier.weight(1f)) {
                            Text("New cycle")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CycleHeader(
    summary: CycleSummary,
    isRunning: Boolean,
    canGoOlder: Boolean,
    canGoNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOlder, enabled = canGoOlder) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Older cycle")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isRunning) "CURRENT CYCLE" else "CLOSED CYCLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = cycleRangeLabel(summary),
                    style = tabularNumbers(MaterialTheme.typography.titleMedium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            CyclePill(summary = summary, isRunning = isRunning)
            IconButton(onClick = onNewer, enabled = canGoNewer) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Newer cycle")
            }
        }

        // The ribbon shows how far through the cycle we are; its tick is today.
        PaceBar(
            spentFraction = if (isRunning) summary.elapsedFraction else 1f,
            paceFraction = summary.elapsedFraction.takeIf { isRunning },
            color = MaterialTheme.colorScheme.primary,
            isOver = false,
            modifier = Modifier.semantics {
                contentDescription = if (isRunning) {
                    if (summary.daysUntilStart > 0) "Starts in ${summary.daysUntilStart} days" else "Day ${summary.dayNumber} of ${summary.totalDays}"
                } else {
                    "Closed cycle of ${summary.totalDays} days"
                }
            }
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = when {
                    !isRunning -> "${summary.totalDays} ${plural(summary.totalDays, "day")}"
                    summary.daysUntilStart > 0 -> "${summary.totalDays}-day cycle"
                    else -> "Day ${summary.dayNumber} of ${summary.totalDays}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isRunning && summary.daysUntilStart == 0) {
                Text(
                    text = "${(summary.elapsedFraction * 100).roundToInt()}% elapsed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CyclePill(summary: CycleSummary, isRunning: Boolean) {
    val (text, tint) = when {
        !isRunning -> "Closed" to MaterialTheme.colorScheme.onSurfaceVariant
        summary.daysUntilStart > 0 ->
            (if (summary.daysUntilStart == 1) "Starts tomorrow" else "Starts in ${summary.daysUntilStart} days") to
                MaterialTheme.colorScheme.primary
        summary.isOverdue -> "${summary.daysOverdue} ${plural(summary.daysOverdue, "day")} overdue" to ExpenseOrange
        summary.daysLeft == 0 -> "Last day" to MaterialTheme.colorScheme.primary
        else -> "${summary.daysLeft} ${plural(summary.daysLeft, "day")} left" to MaterialTheme.colorScheme.primary
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        maxLines = 1,
        modifier = Modifier
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun OverdueBanner(summary: CycleSummary, onStartCycle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ExpenseOrange.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = ExpenseOrange, modifier = Modifier.size(20.dp))
            Text(text = "Payday has passed", style = MaterialTheme.typography.titleMedium, color = ExpenseOrange)
        }
        Text(
            text = "This cycle ended ${dateLabel(LocalDate.parse(summary.cycle.endDate))}. New spending is still " +
                "counted here until you start the next one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onStartCycle) { Text("Start new cycle") }
    }
}

@Composable
private fun Hero(summary: CycleSummary) {
    val overspent = summary.leftToSpend < 0
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionLabel(if (overspent) "Overspent" else "Left to spend")
        Text(
            text = formatRupees(summary.leftToSpend),
            style = tabularNumbers(MaterialTheme.typography.displaySmall),
            color = if (overspent) ExpenseRed else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "of ${formatRupees(summary.cycle.spendableAmount)} · ${formatRupees(summary.totalSpent)} spent",
            style = tabularNumbers(MaterialTheme.typography.bodyMedium),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatRow(summary: CycleSummary, isRunning: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (isRunning) {
            StatCard(
                label = "Daily pace",
                value = if (summary.dailyPace > 0.0) formatRupees(summary.dailyPace) else "—",
                modifier = Modifier.weight(1f)
            )
        } else {
            StatCard(label = "Spent", value = formatRupees(summary.totalSpent), modifier = Modifier.weight(1f))
        }
        StatCard(
            label = "Unallocated",
            value = formatRupees(summary.unallocated),
            valueColor = if (summary.unallocated < 0) ExpenseRed else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "Over budget",
            value = if (summary.overBucketCount == 0) "None" else "${summary.overBucketCount} ${plural(summary.overBucketCount, "bucket")}",
            valueColor = if (summary.overBucketCount > 0) ExpenseOrange else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun UnbucketedNudge(summary: CycleSummary, isRunning: Boolean, onClick: () -> Unit) {
    val count = summary.unbucketed.size
    val names = summary.unbucketed.take(2).joinToString(" and ") { it.category } +
        if (count > 2) " and ${count - 2} more" else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ExpenseOrange.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .then(if (isRunning) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = ExpenseOrange, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "$count ${plural(count, "category", "categories")} ${if (count == 1) "isn't" else "aren't"} in a bucket",
                style = MaterialTheme.typography.titleMedium,
                color = ExpenseOrange
            )
            Text(
                text = "$names · ${formatRupees(summary.unbucketedSpent)} this cycle" + if (isRunning) ". Tap to assign." else "",
                style = tabularNumbers(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoBucketsCard(isRunning: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "No buckets yet", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (isRunning) {
                "Use Plan buckets below to split your spendable amount and see where the money is going."
            } else {
                "This cycle had no buckets."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyBudget(innerPadding: PaddingValues, onStartCycle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.AccountBalanceWallet,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Text(text = "Give your salary a job", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            text = "On payday, enter what you have to spend, split it into buckets, and see what is left in " +
                "each one until the next payday.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onStartCycle) { Text("Start your first cycle") }
    }
}

private val RANGE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val RANGE_FORMAT_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

private fun dateLabel(date: LocalDate): String = date.format(RANGE_FORMAT)

/** "26 Aug — 25 Sep", with the year added only when the cycle is not in the current year. */
private fun cycleRangeLabel(summary: CycleSummary): String {
    val start = LocalDate.parse(summary.cycle.startDate)
    val end = LocalDate.parse(summary.cycle.endDate)
    val currentYear = LocalDate.now().year
    return if (start.year == currentYear && end.year == currentYear) {
        "${start.format(RANGE_FORMAT)} — ${end.format(RANGE_FORMAT)}"
    } else {
        "${start.format(RANGE_FORMAT_YEAR)} — ${end.format(RANGE_FORMAT_YEAR)}"
    }
}

private fun plural(count: Int, singular: String, plural: String = singular + "s"): String =
    if (count == 1) singular else plural
