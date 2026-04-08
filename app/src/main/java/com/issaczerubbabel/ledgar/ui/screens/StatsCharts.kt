package com.issaczerubbabel.ledgar.ui.screens

import android.text.Layout
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.ChartValues
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerValueFormatter
import com.patrykandpatrick.vico.core.common.Dimensions
import com.patrykandpatrick.vico.core.common.shape.Shape
import com.issaczerubbabel.ledgar.ui.theme.ExpenseRed
import com.issaczerubbabel.ledgar.ui.theme.IncomeGreen
import com.issaczerubbabel.ledgar.viewmodel.CategoryTotal
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ExpenseDonutChart(
    categoryTotals: List<CategoryTotal>,
    modifier: Modifier = Modifier
) {
    var hiddenCategories by remember { mutableStateOf(emptySet<String>()) }
    var selectedMarker by remember { mutableStateOf<DonutSliceMarker?>(null) }
    val totalSpent = remember(categoryTotals) { categoryTotals.sumOf { it.totalAmount } }
    val visibleCategories = remember(categoryTotals, hiddenCategories) {
        categoryTotals.filter { it.categoryName !in hiddenCategories }
    }
    val totalVisibleAmount = remember(visibleCategories) { visibleCategories.sumOf { it.totalAmount } }
    val currentVisibleCategories by rememberUpdatedState(visibleCategories)
    val currentTotalVisibleAmount by rememberUpdatedState(totalVisibleAmount)

    if (selectedMarker != null && selectedMarker?.categoryName !in visibleCategories.map { it.categoryName }) {
        selectedMarker = null
    }

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
            Box(modifier = Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier
                        .size(210.dp)
                        .pointerInput(currentVisibleCategories, currentTotalVisibleAmount) {
                            detectTapGestures { tapOffset ->
                                if (currentVisibleCategories.isEmpty() || currentTotalVisibleAmount <= 0.0) {
                                    selectedMarker = null
                                    return@detectTapGestures
                                }

                                val chartWidth = size.width.toFloat()
                                val chartHeight = size.height.toFloat()
                                val center = Offset(chartWidth / 2f, chartHeight / 2f)
                                val dx = tapOffset.x - center.x
                                val dy = tapOffset.y - center.y
                                val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                                val outerRadius = minOf(chartWidth, chartHeight) / 2f
                                val ringThickness = 40.dp.toPx()
                                val innerRadius = outerRadius - ringThickness

                                if (distance < innerRadius || distance > outerRadius) {
                                    selectedMarker = null
                                    return@detectTapGestures
                                }

                                val rawAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                val normalizedAngle = (rawAngle + 450f) % 360f

                                var accumulatedSweep = 0f
                                currentVisibleCategories.forEach { category ->
                                    val sweep = ((category.totalAmount / currentTotalVisibleAmount) * 360f).toFloat()
                                    val isInSlice = normalizedAngle >= accumulatedSweep &&
                                        normalizedAngle < accumulatedSweep + sweep

                                    if (isInSlice) {
                                        val sliceMiddleAngle = accumulatedSweep + (sweep / 2f)
                                        val drawAngle = sliceMiddleAngle - 90f
                                        val radians = (drawAngle / 180f) * PI.toFloat()
                                        val markerRadius = (innerRadius + outerRadius) / 2f
                                        val markerAnchor = Offset(
                                            x = center.x + cos(radians) * markerRadius,
                                            y = center.y + sin(radians) * markerRadius
                                        )

                                        selectedMarker = DonutSliceMarker(
                                            categoryName = category.categoryName,
                                            amount = category.totalAmount,
                                            percentage = if (currentTotalVisibleAmount == 0.0) {
                                                0.0
                                            } else {
                                                (category.totalAmount / currentTotalVisibleAmount) * 100.0
                                            },
                                            anchor = markerAnchor
                                        )
                                        return@detectTapGestures
                                    }

                                    accumulatedSweep += sweep
                                }

                                selectedMarker = null
                            }
                        }
                ) {
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

                selectedMarker?.let { marker ->
                    val markerText = "${marker.categoryName}  ${formatCurrency(marker.amount)} (${marker.percentage.formatOneDecimal()}%)"
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                IntOffset(
                                    x = (marker.anchor.x - 68f).roundToInt(),
                                    y = (marker.anchor.y - 40f).roundToInt()
                                )
                            }
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = markerText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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

private data class DonutSliceMarker(
    val categoryName: String,
    val amount: Double,
    val percentage: Double,
    val anchor: Offset
)

@Composable
fun CashFlowBarChart(
    modelProducer: CartesianChartModelProducer,
    xAxisLabels: List<String>,
    markerValueFormatter: CartesianMarkerValueFormatter,
    formatRupee: (Double) -> String,
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

    val startAxisFormatter = remember(formatRupee) {
        object : CartesianValueFormatter {
            override fun format(
                value: Double,
                chartValues: ChartValues,
                verticalAxisPosition: Axis.Position.Vertical?
            ): CharSequence {
                return formatRupee(value)
            }
        }
    }

    val markerLabelBackground = rememberShapeComponent(
        color = MaterialTheme.colorScheme.surface,
        shape = Shape.Companion.rounded(12f),
        strokeColor = MaterialTheme.colorScheme.outlineVariant,
        strokeThickness = 1.dp
    )

    val markerLabel = rememberTextComponent(
        color = MaterialTheme.colorScheme.onSurface,
        textAlignment = Layout.Alignment.ALIGN_CENTER,
        padding = Dimensions(10f, 6f),
        background = markerLabelBackground
    )

    val marker = remember(markerLabel, markerValueFormatter) {
        RupeeCartesianMarker(
            label = markerLabel,
            valueFormatter = markerValueFormatter
        )
    }

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(),
                startAxis = rememberStartAxis(valueFormatter = startAxisFormatter),
                bottomAxis = rememberBottomAxis(valueFormatter = bottomAxisFormatter),
                marker = marker
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .background(Color.Transparent)
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
