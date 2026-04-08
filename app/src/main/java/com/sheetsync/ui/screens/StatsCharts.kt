package com.sheetsync.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sheetsync.ui.theme.ExpenseRed
import com.sheetsync.ui.theme.IncomeGreen
import com.sheetsync.viewmodel.CategoryTotal
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max

@Composable
fun ExpenseDonutChart(
    categoryTotals: List<CategoryTotal>,
    modifier: Modifier = Modifier
) {
    var hiddenCategories by remember { mutableStateOf(emptySet<String>()) }
    val totalSpent = remember(categoryTotals) { categoryTotals.sumOf { it.totalAmount } }
    val visibleCategories = remember(categoryTotals, hiddenCategories) {
        categoryTotals.filter { it.categoryName !in hiddenCategories }
    }
    val totalVisibleAmount = remember(visibleCategories) { visibleCategories.sumOf { it.totalAmount } }

    if (categoryTotals.isEmpty() || totalSpent <= 0.0) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No expense data",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(210.dp)) {
                val stroke = Stroke(width = 40.dp.toPx(), cap = StrokeCap.Butt)
                var startAngle = -90f

                if (totalVisibleAmount > 0.0) {
                    visibleCategories.forEach { category ->
                        val sweepAngle = ((category.totalAmount / totalVisibleAmount) * 360f).toFloat()
                        drawArc(
                            color = category.assignedColor,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = stroke
                        )
                        startAngle += sweepAngle
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total Spent",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatCurrency(totalVisibleAmount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categoryTotals.forEach { category ->
                val isHidden = category.categoryName in hiddenCategories
                val itemAlpha = if (isHidden) 0.3f else 1f
                val percentage = if (totalVisibleAmount == 0.0) 0.0 else (category.totalAmount / totalVisibleAmount) * 100.0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            hiddenCategories = if (isHidden) {
                                hiddenCategories - category.categoryName
                            } else {
                                hiddenCategories + category.categoryName
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .alpha(itemAlpha)
                            .background(category.assignedColor, shape = CircleShape)
                    )
                    Text(
                        text = category.categoryName,
                        modifier = Modifier
                            .weight(1f)
                            .alpha(itemAlpha),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isHidden) TextDecoration.LineThrough else TextDecoration.None
                    )
                    if (!isHidden) {
                        Text(
                            text = "${percentage.formatOneDecimal()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatCurrency(category.totalAmount),
                        modifier = Modifier.alpha(itemAlpha),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun CashFlowBarChart(
    cashFlowByPeriod: Map<String, Pair<Double, Double>>,
    modifier: Modifier = Modifier
) {
    val points = remember(cashFlowByPeriod) { cashFlowByPeriod.entries.toList() }
    val baselineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No cash flow data",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val maxValue = remember(points) {
        points.maxOfOrNull { (_, pair) -> max(pair.first, pair.second) }?.coerceAtLeast(1.0) ?: 1.0
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            val topPadding = 16.dp.toPx()
            val bottomPadding = 18.dp.toPx()
            val baselineY = size.height - bottomPadding
            val maxBarHeight = baselineY - topPadding

            drawLine(
                color = baselineColor,
                start = androidx.compose.ui.geometry.Offset(0f, baselineY),
                end = androidx.compose.ui.geometry.Offset(size.width, baselineY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            )

            val slotWidth = size.width / points.size
            val groupWidth = slotWidth * 0.72f
            val intraGroupGap = groupWidth * 0.12f
            val barWidth = (groupWidth - intraGroupGap) / 2f
            val corner = CornerRadius(8.dp.toPx(), 8.dp.toPx())

            points.forEachIndexed { index, (_, pair) ->
                val income = pair.first.coerceAtLeast(0.0)
                val expense = pair.second.coerceAtLeast(0.0)
                val incomeHeight = ((income / maxValue) * maxBarHeight).toFloat()
                val expenseHeight = ((expense / maxValue) * maxBarHeight).toFloat()

                val slotStart = index * slotWidth
                val groupStart = slotStart + (slotWidth - groupWidth) / 2f

                drawRoundRect(
                    color = IncomeGreen,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x = groupStart,
                        y = baselineY - incomeHeight
                    ),
                    size = androidx.compose.ui.geometry.Size(barWidth, incomeHeight),
                    cornerRadius = corner
                )

                drawRoundRect(
                    color = ExpenseRed,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x = groupStart + barWidth + intraGroupGap,
                        y = baselineY - expenseHeight
                    ),
                    size = androidx.compose.ui.geometry.Size(barWidth, expenseHeight),
                    cornerRadius = corner
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            points.forEach { (label, _) ->
                Text(
                    text = label,
                    modifier = Modifier.width(56.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendBadge(text = "Income", color = IncomeGreen)
            LegendBadge(text = "Expense", color = ExpenseRed)
        }
    }
}

@Composable
private fun LegendBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, shape = CircleShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatCurrency(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    formatter.maximumFractionDigits = 0
    return formatter.format(amount)
}

private fun Double.formatOneDecimal(): String = String.format(Locale.ENGLISH, "%.1f", this)
