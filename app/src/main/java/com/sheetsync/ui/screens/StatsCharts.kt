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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.ChartValues
import com.sheetsync.ui.theme.ExpenseRed
import com.sheetsync.ui.theme.IncomeGreen
import com.sheetsync.viewmodel.CategoryTotal
import java.text.NumberFormat
import java.util.Locale

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
    modelProducer: CartesianChartModelProducer,
    xAxisLabels: List<String>,
    modifier: Modifier = Modifier
) {
    if (xAxisLabels.isEmpty()) {
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

    val bottomAxisFormatter = remember(xAxisLabels) {
        object : CartesianValueFormatter {
            override fun format(
                value: Double,
                chartValues: ChartValues,
                verticalAxisPosition: Axis.Position.Vertical?
            ): CharSequence {
                return xAxisLabels.getOrNull(value.toInt()) ?: ""
            }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(),
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(valueFormatter = bottomAxisFormatter)
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )

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
