package com.issaczerubbabel.ledgar.ui.screens

import android.text.Layout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.stacked
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.ChartValues
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerValueFormatter
import com.patrykandpatrick.vico.core.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.common.Dimensions
import com.patrykandpatrick.vico.core.common.shape.Shape
import com.issaczerubbabel.ledgar.ui.theme.ExpenseOrange
import com.issaczerubbabel.ledgar.ui.theme.IncomeBlue
import com.issaczerubbabel.ledgar.viewmodel.BalancePoint
import com.issaczerubbabel.ledgar.viewmodel.IncomeExpensePoint
import com.issaczerubbabel.ledgar.viewmodel.OverallAccountStatsViewModel
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverallAccountStatsScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    vm: OverallAccountStatsViewModel = hiltViewModel(),
) {
    val selectedMonth by vm.selectedYearMonth.collectAsStateWithLifecycle()
    val historicalBalance by vm.historicalBalance.collectAsStateWithLifecycle()
    val monthlyIncomeExpense by vm.monthlyIncomeExpense.collectAsStateWithLifecycle()

    var showMonthPicker by remember { mutableStateOf(false) }
    var pickerMonth by remember(selectedMonth) { mutableIntStateOf(selectedMonth.monthValue) }
    var pickerYear by remember(selectedMonth) { mutableIntStateOf(selectedMonth.year) }

    val monthLabel = remember(selectedMonth) {
        selectedMonth.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Overall Stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = vm::prevMonth) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
                    }
                    Row(
                        modifier = Modifier
                            .clickable { showMonthPicker = true }
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = monthLabel, fontSize = 14.sp)
                    }
                    IconButton(onClick = vm::nextMonth) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { topPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(topPadding)
                .padding(bottom = innerPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            LineChartSection(
                lineChartModelProducer = vm.lineChartModelProducer,
                points = historicalBalance,
            )
            Spacer(modifier = Modifier.height(24.dp))
            BarChartSection(
                barChartModelProducer = vm.barChartModelProducer,
                points = monthlyIncomeExpense,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showMonthPicker) {
        AlertDialog(
            onDismissRequest = { showMonthPicker = false },
            title = { Text("Select Month") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DropdownField(
                        label = "Month",
                        options = monthNames,
                        selected = monthNames[pickerMonth - 1],
                        onSelect = { selected -> pickerMonth = monthNames.indexOf(selected) + 1 },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(onClick = { pickerYear -= 1 }) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous year")
                        }
                        Text(pickerYear.toString(), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { pickerYear += 1 }) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Next year")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setMonthYear(year = pickerYear, month = pickerMonth)
                    showMonthPicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMonthPicker = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun LineChartSection(
    lineChartModelProducer: CartesianChartModelProducer,
    points: List<BalancePoint>,
    modifier: Modifier = Modifier,
) {
    val latestBalance = points.lastOrNull()?.balance ?: 0.0

    val bottomAxisFormatter = remember(points) {
        object : CartesianValueFormatter {
            override fun format(
                value: Double,
                chartValues: ChartValues,
                verticalAxisPosition: Axis.Position.Vertical?,
            ): CharSequence {
                return points.getOrNull(value.toInt())?.label ?: ""
            }
        }
    }

    val startAxisFormatter = remember {
        object : CartesianValueFormatter {
            override fun format(
                value: Double,
                chartValues: ChartValues,
                verticalAxisPosition: Axis.Position.Vertical?,
            ): CharSequence = moneyCompact(value)
        }
    }

    val markerValueFormatter = remember(points) {
        CartesianMarkerValueFormatter { _, targets ->
            val xIndex = targets.firstOrNull()?.xValue?.toInt()
            val point = xIndex?.let { points.getOrNull(it) }
            if (point == null) {
                ""
            } else {
                "${point.label}\n🔴 ${formatCurrencySigned(point.balance)}"
            }
        }
    }

    val markerLabelBackground = rememberShapeComponent(
        color = MaterialTheme.colorScheme.surface,
        shape = Shape.rounded(12f),
        strokeColor = MaterialTheme.colorScheme.outlineVariant,
        strokeThickness = 1.dp,
    )

    val markerLabel = rememberTextComponent(
        color = MaterialTheme.colorScheme.onSurface,
        textAlignment = Layout.Alignment.ALIGN_CENTER,
        padding = Dimensions(10f, 6f),
        background = markerLabelBackground,
    )

    val marker: CartesianMarker = remember(markerLabel, markerValueFormatter) {
        RupeeCartesianMarker(label = markerLabel, valueFormatter = markerValueFormatter)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Balance", style = MaterialTheme.typography.titleMedium)
        Text(
            text = formatCurrencySigned(latestBalance),
            style = MaterialTheme.typography.headlineMedium,
            color = if (latestBalance < 0) ExpenseOrange else MaterialTheme.colorScheme.onSurface,
        )

        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = rememberStartAxis(valueFormatter = startAxisFormatter),
                bottomAxis = rememberBottomAxis(valueFormatter = bottomAxisFormatter),
                marker = marker,
            ),
            modelProducer = lineChartModelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            points.forEach { point ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatAmountPlain(point.balance),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (point.balance < 0) ExpenseOrange else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun BarChartSection(
    barChartModelProducer: CartesianChartModelProducer,
    points: List<IncomeExpensePoint>,
    modifier: Modifier = Modifier,
) {
    val bottomAxisFormatter = remember(points) {
        object : CartesianValueFormatter {
            override fun format(
                value: Double,
                chartValues: ChartValues,
                verticalAxisPosition: Axis.Position.Vertical?,
            ): CharSequence {
                return points.getOrNull(value.toInt())?.label ?: ""
            }
        }
    }

    val startAxisFormatter = remember {
        object : CartesianValueFormatter {
            override fun format(
                value: Double,
                chartValues: ChartValues,
                verticalAxisPosition: Axis.Position.Vertical?,
            ): CharSequence = moneyCompact(value)
        }
    }

    val markerValueFormatter = remember(points) {
        CartesianMarkerValueFormatter { _, targets ->
            val point = targets.firstOrNull()?.xValue?.toInt()?.let { points.getOrNull(it) }
            if (point == null) {
                ""
            } else {
                "${point.label}\n🔵 Income ${formatCurrencySigned(point.income)}\n🔴 Expenses ${formatCurrencySigned(point.expense)}"
            }
        }
    }

    val markerLabelBackground = rememberShapeComponent(
        color = MaterialTheme.colorScheme.surface,
        shape = Shape.rounded(12f),
        strokeColor = MaterialTheme.colorScheme.outlineVariant,
        strokeThickness = 1.dp,
    )

    val markerLabel = rememberTextComponent(
        color = MaterialTheme.colorScheme.onSurface,
        textAlignment = Layout.Alignment.ALIGN_NORMAL,
        padding = Dimensions(10f, 6f),
        background = markerLabelBackground,
    )

    val marker: CartesianMarker = remember(markerLabel, markerValueFormatter) {
        RupeeCartesianMarker(label = markerLabel, valueFormatter = markerValueFormatter)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(mergeMode = { ColumnCartesianLayer.MergeMode.stacked() }),
                startAxis = rememberStartAxis(valueFormatter = startAxisFormatter),
                bottomAxis = rememberBottomAxis(valueFormatter = bottomAxisFormatter),
                marker = marker,
            ),
            modelProducer = barChartModelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            LegendBadge(text = "Income", color = IncomeBlue)
            LegendBadge(text = "Expenses", color = ExpenseOrange)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            points.forEach { point ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatAmountPlain(point.income),
                        style = MaterialTheme.typography.bodySmall,
                        color = IncomeBlue,
                    )
                    Text(
                        text = formatAmountPlain(point.expense),
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseOrange,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendBadge(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "●", color = color)
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val monthNames = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

private fun formatCurrencySigned(value: Double): String {
    return synchronized(currencyFormatter) {
        val formatted = currencyFormatter.format(kotlin.math.abs(value))
        if (value < 0) "-$formatted" else formatted
    }
}

private fun formatAmountPlain(value: Double): String {
    return String.format(Locale.ENGLISH, "%,.2f", value)
}

private fun moneyCompact(value: Double): String {
    val abs = kotlin.math.abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        abs >= 1_000_000 -> "$sign${"%.0f".format(abs / 1_000_000)}M"
        abs >= 1_000 -> "$sign${"%.0f".format(abs / 1_000)}K"
        else -> "$sign${"%.0f".format(abs)}"
    }
}

private val CartesianMarker.Target.xValue: Float
    get() = when (this) {
        is ColumnCartesianLayerMarkerTarget -> columns.firstOrNull()?.entry?.x?.toFloat() ?: 0f
        is LineCartesianLayerMarkerTarget -> points.firstOrNull()?.entry?.x?.toFloat() ?: 0f
        else -> 0f
    }
