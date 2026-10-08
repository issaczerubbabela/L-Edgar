package com.issaczerubbabel.ledgar.ui.screens

import android.text.Layout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.ui.theme.ExpenseOrange
import com.issaczerubbabel.ledgar.ui.theme.IncomeBlue
import com.issaczerubbabel.ledgar.viewmodel.CashFlowMonthUi
import com.issaczerubbabel.ledgar.viewmodel.MoverUi
import com.issaczerubbabel.ledgar.viewmodel.NetWorthMonthUi
import com.issaczerubbabel.ledgar.viewmodel.NetWorthPeriod
import com.issaczerubbabel.ledgar.viewmodel.NetWorthUiState
import com.issaczerubbabel.ledgar.viewmodel.NetWorthViewModel
import com.issaczerubbabel.ledgar.viewmodel.ShareUi
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.ChartValues
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerValueFormatter
import com.patrykandpatrick.vico.core.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.common.Dimensions
import com.patrykandpatrick.vico.core.common.shape.Shape

/**
 * Net worth (was Overall Stats): the trend from month-end figures, cash flow that leaves out
 * moving money between your own Accounts, where your money is, and this month's movers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    vm: NetWorthViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Net worth") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { topPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(topPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = innerPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = state.netWorth,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold)
                )
                if (state.monthChange.isNotBlank()) {
                    Text(
                        text = state.monthChange,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.isMonthChangeNegative) ExpenseOrange else IncomeBlue
                    )
                }
            }
            HistorySection(state = state, producer = vm.historyChart, onPeriod = vm::setPeriod)
            CashFlowSection(state = state, producer = vm.cashFlowChart)
            SharesSection(state = state)
            MoversSection(movers = state.movers, note = state.moversNote)
        }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable
private fun HistorySection(state: NetWorthUiState, producer: CartesianChartModelProducer, onPeriod: (NetWorthPeriod) -> Unit) {
    SectionCard(title = "Net worth over time") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NetWorthPeriod.entries.forEach { period ->
                FilterChip(selected = state.period == period, onClick = { onPeriod(period) }, label = { Text(period.label) })
            }
        }
        if (state.showHistoryChart) {
            HistoryChart(points = state.history, producer = producer, summary = state.historySummary)
        } else {
            // Under four months, a line would mislead: show the figures.
            Column(modifier = Modifier.semantics { contentDescription = state.historySummary }) {
                state.history.forEachIndexed { index, point ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(point.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(point.netWorthText, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            point.change?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryChart(points: List<NetWorthMonthUi>, producer: CartesianChartModelProducer, summary: String) {
    val bottomAxisFormatter = remember(points) { indexFormatter { points.getOrNull(it)?.shortLabel.orEmpty() } }
    val startAxisFormatter = remember { valueFormatter(::compactRupees) }
    val markerFormatter = remember(points) {
        CartesianMarkerValueFormatter { _, targets ->
            val point = targets.firstOrNull()?.xIndex?.let { points.getOrNull(it) } ?: return@CartesianMarkerValueFormatter ""
            listOfNotNull(point.label, point.netWorthText, point.change).joinToString("\n")
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = rememberStartAxis(valueFormatter = startAxisFormatter),
            bottomAxis = rememberBottomAxis(valueFormatter = bottomAxisFormatter),
            marker = rememberMarker(markerFormatter)
        ),
        modelProducer = producer,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { contentDescription = summary }
    )
}

@Composable
private fun CashFlowSection(state: NetWorthUiState, producer: CartesianChartModelProducer) {
    SectionCard(
        title = "Cash flow",
        subtitle = "Money in and out of the accounts in your totals. Moving money between them isn't counted."
    ) {
        if (state.cashFlow.isEmpty()) return@SectionCard
        val months = state.cashFlow
        val bottomAxisFormatter = remember(months) { indexFormatter { months.getOrNull(it)?.shortLabel.orEmpty() } }
        val startAxisFormatter = remember { valueFormatter(::compactRupees) }
        val markerFormatter = remember(months) {
            CartesianMarkerValueFormatter { _, targets ->
                val month = targets.firstOrNull()?.xIndex?.let { months.getOrNull(it) } ?: return@CartesianMarkerValueFormatter ""
                listOfNotNull(month.shortLabel, "In ${month.moneyInText}", "Out ${month.moneyOutText}", month.adjustments, "Net ${month.net}")
                    .joinToString("\n")
            }
        }
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(),
                startAxis = rememberStartAxis(valueFormatter = startAxisFormatter),
                bottomAxis = rememberBottomAxis(valueFormatter = bottomAxisFormatter),
                marker = rememberMarker(markerFormatter)
            ),
            modelProducer = producer,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .semantics { contentDescription = state.cashFlowSummary }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend("In", IncomeBlue)
            Legend("Out", ExpenseOrange)
        }
        CashFlowNets(months)
    }
}

@Composable
private fun CashFlowNets(months: List<CashFlowMonthUi>) {
    // The latest few months' net and adjustments, so the bars' totals can be read without tapping.
    Column {
        months.takeLast(3).reversed().forEachIndexed { index, month ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(month.shortLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(48.dp))
                Text(
                    text = month.adjustments.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = month.net,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (month.isNetNegative) ExpenseOrange else IncomeBlue
                )
            }
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SharesSection(state: NetWorthUiState) {
    SectionCard(title = "Where your money is") {
        ShareGroup(title = "Assets · ${state.assetsTotal}", shares = state.assetShares, color = MaterialTheme.colorScheme.primary)
        if (state.liabilityShares.isNotEmpty()) {
            ShareGroup(title = "Liabilities · ${state.liabilitiesTotal}", shares = state.liabilityShares, color = ExpenseOrange)
        }
    }
}

@Composable
private fun ShareGroup(title: String, shares: List<ShareUi>, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        shares.forEach { share ->
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {}
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(share.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(share.amount, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        share.percent,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(56.dp).padding(start = 8.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(share.fraction.coerceIn(0f, 1f))
                            .height(8.dp)
                            .background(color, RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun MoversSection(movers: List<MoverUi>, note: String) {
    SectionCard(title = "This month's movers", subtitle = note) {
        if (movers.isEmpty()) {
            Text("No account has changed this month.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column {
            movers.forEachIndexed { index, mover ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(mover.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        mover.change,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (mover.isNegative) ExpenseOrange else IncomeBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberMarker(formatter: CartesianMarkerValueFormatter): CartesianMarker {
    val background = rememberShapeComponent(
        color = MaterialTheme.colorScheme.surface,
        shape = Shape.rounded(12f),
        strokeColor = MaterialTheme.colorScheme.outlineVariant,
        strokeThickness = 1.dp
    )
    val label = rememberTextComponent(
        color = MaterialTheme.colorScheme.onSurface,
        textAlignment = Layout.Alignment.ALIGN_NORMAL,
        padding = Dimensions(10f, 6f),
        background = background
    )
    return remember(label, formatter) { RupeeCartesianMarker(label = label, valueFormatter = formatter) }
}

private fun indexFormatter(labelAt: (Int) -> String) = object : CartesianValueFormatter {
    override fun format(value: Double, chartValues: ChartValues, verticalAxisPosition: Axis.Position.Vertical?): CharSequence =
        labelAt(value.toInt())
}

private fun valueFormatter(format: (Double) -> String) = object : CartesianValueFormatter {
    override fun format(value: Double, chartValues: ChartValues, verticalAxisPosition: Axis.Position.Vertical?): CharSequence =
        format(value)
}

/** Axis labels in lakh and thousand: ₹2.4L, ₹65K. */
private fun compactRupees(value: Double): String {
    val abs = kotlin.math.abs(value)
    val sign = if (value < 0) "−" else ""
    return when {
        abs >= 1_00_000 -> "$sign₹${"%.1f".format(abs / 1_00_000)}L"
        abs >= 1_000 -> "$sign₹${"%.0f".format(abs / 1_000)}K"
        else -> "$sign₹${"%.0f".format(abs)}"
    }
}

private val CartesianMarker.Target.xIndex: Int
    get() = when (this) {
        is ColumnCartesianLayerMarkerTarget -> columns.firstOrNull()?.entry?.x?.toInt() ?: 0
        is LineCartesianLayerMarkerTarget -> points.firstOrNull()?.entry?.x?.toInt() ?: 0
        else -> 0
    }
