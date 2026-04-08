package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.ChartValues

@Composable
fun AccountStatementChart(
    modelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier
) {
    val xFormatter = remember {
        object : CartesianValueFormatter {
            override fun format(
                value: Double,
                chartValues: ChartValues,
                verticalAxisPosition: Axis.Position.Vertical?
            ): CharSequence = value.toInt().toString()
        }
    }

    val yFormatter = remember {
        object : CartesianValueFormatter {
            override fun format(
                value: Double,
                chartValues: ChartValues,
                verticalAxisPosition: Axis.Position.Vertical?
            ): CharSequence = "₹ ${moneyCompact(value)}"
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = rememberStartAxis(valueFormatter = yFormatter),
            bottomAxis = rememberBottomAxis(valueFormatter = xFormatter)
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxWidth()
            .height(250.dp)
    )
}

private fun moneyCompact(value: Double): String {
    val abs = kotlin.math.abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        abs >= 1_000_000 -> "$sign${"%.1f".format(abs / 1_000_000)}M"
        abs >= 1_000 -> "$sign${"%.1f".format(abs / 1_000)}K"
        else -> "$sign${"%.0f".format(abs)}"
    }
}
