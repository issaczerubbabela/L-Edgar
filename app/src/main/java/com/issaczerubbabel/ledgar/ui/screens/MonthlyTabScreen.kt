package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.issaczerubbabel.ledgar.ledger.LedgerMonthRow
import com.issaczerubbabel.ledgar.ledger.LedgerWeek
import com.issaczerubbabel.ledgar.ledger.LedgerYear
import com.issaczerubbabel.ledgar.ui.theme.ExpenseOrange
import com.issaczerubbabel.ledgar.ui.theme.IncomeBlue
import java.time.YearMonth

/**
 * Monthly: the year of the Ledger's month, newest month first. Tapping a month or a week opens it on
 * Daily; the arrow beside a month only shows or hides its weeks.
 */
@Composable
fun MonthlyTabScreen(
    year: LedgerYear,
    expanded: Set<YearMonth>,
    onOpenMonth: (YearMonth) -> Unit,
    onOpenWeek: (YearMonth, LedgerWeek) -> Unit,
    onToggleExpand: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    if (year.months.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nothing logged yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 96.dp)) {
        year.months.forEach { month ->
            item(key = month.month.toString()) {
                MonthRow(
                    month = month,
                    isExpanded = month.month in expanded,
                    onOpen = { onOpenMonth(month.month) },
                    onToggleExpand = { onToggleExpand(month.month) }
                )
            }
            if (month.month in expanded) {
                month.weeks.forEach { week ->
                    item(key = "${month.month}-${week.start}") {
                        WeekRow(week = week, onOpen = { onOpenWeek(month.month, week) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthRow(month: LedgerMonthRow, isExpanded: Boolean, onOpen: () -> Unit, onToggleExpand: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClickLabel = "Open on Daily", onClick = onOpen)
            .semantics { contentDescription = month.spokenLabel }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).clearAndSetSemantics {}) {
            Text(month.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(month.range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StackedAmounts(month.income, month.expense, month.net, Modifier.clearAndSetSemantics {})
        // Apart from the row's own tap, so expanding and opening never fight over one tap.
        IconButton(onClick = onToggleExpand) {
            Icon(
                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (isExpanded) "Hide weeks of ${month.name}" else "Show weeks of ${month.name}"
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
}

@Composable
private fun WeekRow(week: LedgerWeek, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open on Daily", onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = week.spokenLabel
                onClick(label = "Open on Daily") { onOpen(); true }
            }
            .heightIn(min = 48.dp)
            .padding(start = 32.dp, end = 48.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(week.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        StackedAmounts(week.income, week.expense, week.net)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp, modifier = Modifier.padding(start = 32.dp))
}

/** Income, expense and Net stacked on the right, so even ₹10,00,000 fits a 360dp phone uncut. */
@Composable
private fun StackedAmounts(income: String, expense: String, net: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        Text(income, style = MaterialTheme.typography.bodySmall, color = IncomeBlue, fontWeight = FontWeight.SemiBold)
        Text(expense, style = MaterialTheme.typography.bodySmall, color = ExpenseOrange, fontWeight = FontWeight.SemiBold)
        Text(net, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
