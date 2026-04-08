package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.issaczerubbabel.ledgar.ui.theme.ExpenseOrange
import com.issaczerubbabel.ledgar.ui.theme.IncomeBlue
import com.issaczerubbabel.ledgar.viewmodel.MonthGroup
import com.issaczerubbabel.ledgar.viewmodel.WeeklyItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MonthlyTabScreen(
    monthGroups: List<MonthGroup>,
    onToggleExpand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        monthGroups.forEach { monthGroup ->
            item(key = monthGroup.monthId) {
                MonthHeaderRow(
                    monthGroup = monthGroup,
                    onToggleExpand = { onToggleExpand(monthGroup.monthId) }
                )
            }

            if (monthGroup.isExpanded) {
                monthGroup.weeks.forEach { weeklyItem ->
                    item(key = "${monthGroup.monthId}-${weeklyItem.startDate}") {
                        WeeklyBreakdownRow(weeklyItem)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeaderRow(
    monthGroup: MonthGroup,
    onToggleExpand: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = monthName(monthGroup.month),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )
            Text(
                text = "${formatMonthDay(monthGroup.startDate)} ~ ${formatMonthDay(monthGroup.endDate)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AmountCell(
                    value = "₹ ${formatCompact(monthGroup.monthIncome)}",
                    color = IncomeBlue,
                    width = 120.dp,
                    fontSize = 15.sp
                )
                AmountCell(
                    value = "₹ ${formatCompact(monthGroup.monthExpense)}",
                    color = ExpenseOrange,
                    width = 120.dp,
                    fontSize = 15.sp
                )
            }
            AmountCell(
                value = "₹ ${formatSigned(monthGroup.monthTotal)}",
                color = MaterialTheme.colorScheme.onBackground,
                width = 120.dp,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (monthGroup.isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
}

@Composable
private fun WeeklyBreakdownRow(item: WeeklyItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (item.isHighlighted) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.background
                }
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${formatWeek(item.startDate)} ~ ${formatWeek(item.endDate)}",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )

        Column(horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AmountCell(
                    value = "₹ ${formatCompact(item.income)}",
                    color = IncomeBlue,
                    width = 120.dp,
                    fontSize = 15.sp
                )
                AmountCell(
                    value = "₹ ${formatCompact(item.expense)}",
                    color = ExpenseOrange,
                    width = 120.dp,
                    fontSize = 15.sp
                )
            }
            AmountCell(
                value = "₹ ${formatSigned(item.total)}",
                color = MaterialTheme.colorScheme.onBackground,
                width = 120.dp,
                fontSize = 12.sp
            )
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
}

@Composable
private fun AmountCell(value: String, color: Color, width: Dp, fontSize: androidx.compose.ui.unit.TextUnit) {
    Text(
        text = value,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.End,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier.width(width)
    )
}

private fun formatMonthDay(date: LocalDate): String =
    "${date.monthValue}.${date.dayOfMonth}"

private fun formatWeek(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("MM.dd", Locale.ENGLISH))

private fun monthName(month: Int): String =
    java.time.Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

private fun formatCompact(value: Double): String =
    "%,.2f".format(kotlin.math.abs(value))

private fun formatSigned(value: Double): String {
    val sign = if (value < 0) "-" else ""
    return "$sign${formatCompact(value)}"
}
