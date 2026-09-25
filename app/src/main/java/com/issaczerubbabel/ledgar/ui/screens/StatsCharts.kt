package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.issaczerubbabel.ledgar.ui.components.tabularNumbers
import com.issaczerubbabel.ledgar.ui.theme.ChartColors
import com.issaczerubbabel.ledgar.viewmodel.DayCell
import com.issaczerubbabel.ledgar.viewmodel.Pace
import com.issaczerubbabel.ledgar.viewmodel.TrendBar
import java.time.DayOfWeek
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** "₹950", "₹15k", "₹1.2L": short amounts for axis ticks only; everything read as a figure uses full rupees. */
fun shortRupees(amount: Double): String {
    val a = abs(amount)
    val sign = if (amount < 0) "−" else ""
    return sign + when {
        a >= 1_00_000 -> "₹" + trimmed(a / 1_00_000) + "L"
        a >= 1_000 -> "₹" + trimmed(a / 1_000) + "k"
        else -> "₹" + a.roundToInt()
    }
}

private fun trimmed(v: Double): String = if (v >= 10 || v % 1.0 == 0.0) v.roundToInt().toString() else "%.1f".format(v)

/** A round axis maximum and three even steps below it, all values the axis actually reaches. */
private fun niceTicks(max: Double): List<Double> {
    if (max <= 0.0) return listOf(0.0, 1.0)
    val rough = max / 3
    val magnitude = 10.0.pow(log10(rough).toInt())
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= rough }
    val top = ceil(max / step) * step
    return (0..(top / step).roundToInt()).map { it * step }
}

/**
 * Animates a list of values from what was shown before to [target], so a new period's numbers move
 * into place instead of redrawing from zero. A list of a different length (another kind of period)
 * appears straight away.
 */
@Composable
private fun rememberMorph(target: List<Double>): List<Double> {
    val progress = remember { Animatable(1f) }
    val from = remember { arrayOf(target) }
    val to = remember { arrayOf(target) }
    LaunchedEffect(target) {
        if (to[0] == target) return@LaunchedEffect
        from[0] = if (to[0].size == target.size) blend(from[0], to[0], progress.value) else target
        to[0] = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(350))
    }
    return blend(from[0], to[0], progress.value)
}

private fun blend(a: List<Double>, b: List<Double>, t: Float): List<Double> =
    if (a.size != b.size) b else b.indices.map { i -> a[i] + (b[i] - a[i]) * t }

/**
 * Running spending against the previous period (grey) and the budget (dashed), with where it is
 * heading (dotted). Press and drag to read any day; [onSelect] gets the point under the finger.
 */
@Composable
fun PaceChart(
    pace: Pace,
    previousLabel: String,
    colors: ChartColors,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    description: String,
    modifier: Modifier = Modifier
) {
    val points = pace.labels.size
    if (points < 2) return
    val current = rememberMorph(pace.current)
    val previous = rememberMorph(pace.previous)
    val budgetStart = pace.budget.indexOfFirst { it != null }.coerceAtLeast(0)
    val budget = rememberMorph(pace.budget.drop(budgetStart).map { it ?: 0.0 })
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val latestSelected by rememberUpdatedState(selected)
    val tickStyle = tabularNumbers(MaterialTheme.typography.labelSmall).copy(color = colors.muted)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
    val ticks = remember(pace) {
        niceTicks(listOfNotNull(pace.current.maxOrNull(), pace.previous.maxOrNull(), pace.budget.filterNotNull().maxOrNull(), pace.projection).maxOrNull() ?: 0.0)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .semantics { contentDescription = description }
            .pointerInput(points) {
                fun indexAt(x: Float): Int {
                    val left = 44.dp.toPx()
                    val right = size.width - 40.dp.toPx()
                    return (((x - left) / (right - left)) * (points - 1)).roundToInt().coerceIn(0, points - 1)
                }
                fun pick(x: Float) {
                    val i = indexAt(x)
                    if (i != latestSelected) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(i)
                    }
                }
                detectHorizontalDragGestures(
                    onDragStart = { pick(it.x) },
                    onDragEnd = { onSelect(null) },
                    onDragCancel = { onSelect(null) },
                    onHorizontalDrag = { change, _ -> pick(change.position.x) }
                )
            }
            .pointerInput(points) {
                detectTapGestures(onPress = { offset ->
                    val left = 44.dp.toPx()
                    val right = size.width - 40.dp.toPx()
                    onSelect((((offset.x - left) / (right - left)) * (points - 1)).roundToInt().coerceIn(0, points - 1))
                    tryAwaitRelease()
                })
            }
    ) {
        val left = 44.dp.toPx()
        val right = size.width - 40.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val yMax = ticks.last()
        fun x(i: Int) = left + (right - left) * i / (points - 1)
        fun y(v: Double) = bottom - ((v / yMax).toFloat() * (bottom - top))

        ticks.forEach { v ->
            drawLine(colors.grid, Offset(left, y(v)), Offset(right, y(v)), 1.dp.toPx())
            val text = measurer.measure(if (v == 0.0) "₹0" else shortRupees(v), tickStyle)
            drawText(text, topLeft = Offset(left - 6.dp.toPx() - text.size.width, y(v) - text.size.height / 2f))
        }
        val every = ((points - 1) / 4).coerceAtLeast(1)
        (0 until points step every).forEach { i ->
            val text = measurer.measure(pace.labels[i], tickStyle)
            drawText(text, topLeft = Offset(x(i) - text.size.width / 2f, bottom + 4.dp.toPx()))
        }

        fun line(values: List<Double>, color: Color, width: Float, effect: PathEffect? = null, from: Int = 0) {
            if (values.size < 2) return
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(from + i), y(v)) else path.lineTo(x(from + i), y(v)) }
            drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
        }
        fun endLabel(text: String, value: Double, color: Color, nudge: Float = 0f) {
            val m = measurer.measure(text, labelStyle.copy(color = color))
            drawText(m, topLeft = Offset(right + 6.dp.toPx(), y(value) - m.size.height / 2f + nudge))
        }

        val budgetEnd = budget.lastOrNull()
        val previousEnd = previous.take(points).lastOrNull()
        if (budget.size >= 2) {
            line(budget, colors.muted, 1.5.dp.toPx(), PathEffect.dashPathEffect(floatArrayOf(10f, 8f)), from = budgetStart)
            endLabel("Budget", budgetEnd!!, colors.muted)
        }
        line(previous.take(points), colors.muted.copy(alpha = 0.55f), 2.dp.toPx())
        if (previousEnd != null) {
            val clash = budgetEnd != null && abs(y(previousEnd) - y(budgetEnd)) < 14.dp.toPx()
            endLabel(previousLabel, previousEnd, colors.muted.copy(alpha = 0.8f), if (clash) 14.dp.toPx() else 0f)
        }
        val last = current.lastIndex
        if (last >= 0 && pace.projection != null && last < points - 1) {
            drawLine(
                colors.moneyOut.copy(alpha = 0.8f), Offset(x(last), y(current[last])), Offset(x(points - 1), y(pace.projection)),
                2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 10f))
            )
        }
        line(current, colors.moneyOut, 2.5.dp.toPx())
        if (last >= 0) {
            drawCircle(colors.surface, 6.dp.toPx(), Offset(x(last), y(current[last])))
            drawCircle(colors.moneyOut, 4.dp.toPx(), Offset(x(last), y(current[last])))
        }

        selected?.let { i ->
            drawLine(colors.muted, Offset(x(i), top), Offset(x(i), bottom), 1.dp.toPx())
            current.getOrNull(i)?.let { drawCircle(colors.moneyOut, 4.dp.toPx(), Offset(x(i), y(it))) }
        }
    }
}

/**
 * One bar per period: spent with saved stacked on top, and a tick at what was earned. The gap from the
 * top of the bar to the tick is what was left over.
 */
@Composable
fun TrendChart(
    trend: List<TrendBar>,
    colors: ChartColors,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    description: String,
    modifier: Modifier = Modifier
) {
    if (trend.isEmpty()) return
    val spent = rememberMorph(trend.map { it.spent })
    val saved = rememberMorph(trend.map { it.saved.coerceAtLeast(0.0) })
    val earned = rememberMorph(trend.map { it.earned })
    val measurer = rememberTextMeasurer()
    val tickStyle = tabularNumbers(MaterialTheme.typography.labelSmall).copy(color = colors.muted)
    val currentStyle = tickStyle.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    val ticks = remember(trend) { niceTicks(trend.maxOf { maxOf(it.earned, it.spent + it.saved.coerceAtLeast(0.0)) }) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = description }
            .pointerInput(trend.size) {
                detectTapGestures { offset ->
                    val left = 44.dp.toPx()
                    val slot = (size.width - left) / trend.size
                    val i = ((offset.x - left) / slot).toInt()
                    onSelect(if (i in trend.indices && i != selected) i else null)
                }
            }
    ) {
        val left = 44.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val yMax = ticks.last()
        fun y(v: Double) = bottom - ((v / yMax).toFloat() * (bottom - top))
        val slot = (size.width - left) / trend.size
        val barWidth = (slot * 0.5f).coerceAtMost(22.dp.toPx())
        val gap = 2.dp.toPx()

        ticks.forEach { v ->
            drawLine(colors.grid, Offset(left, y(v)), Offset(size.width, y(v)), 1.dp.toPx())
            val t = measurer.measure(if (v == 0.0) "₹0" else shortRupees(v), tickStyle)
            drawText(t, topLeft = Offset(left - 6.dp.toPx() - t.size.width, y(v) - t.size.height / 2f))
        }
        trend.forEachIndexed { i, bar ->
            val cx = left + slot * i + slot / 2
            val x0 = cx - barWidth / 2
            val alpha = if (selected == null || selected == i) 1f else 0.45f
            val spentTop = y(spent[i])
            drawRect(colors.moneyOut.copy(alpha = alpha), Offset(x0, spentTop), Size(barWidth, bottom - spentTop))
            if (saved[i] > 0.0) {
                val savedTop = y(spent[i] + saved[i])
                roundedTop(colors.saved.copy(alpha = alpha), x0, savedTop, barWidth, (spentTop - gap - savedTop).coerceAtLeast(0f))
            }
            if (earned[i] > 0.0) {
                drawLine(colors.moneyIn.copy(alpha = alpha), Offset(x0 - 4.dp.toPx(), y(earned[i])), Offset(x0 + barWidth + 4.dp.toPx(), y(earned[i])), 3.dp.toPx(), cap = StrokeCap.Round)
            }
            val label = measurer.measure(bar.label, if (bar.isCurrent) currentStyle else tickStyle, maxLines = 1)
            drawText(label, topLeft = Offset(cx - label.size.width / 2f, bottom + 4.dp.toPx()))
        }
    }
}

private fun DrawScope.roundedTop(color: Color, x: Float, top: Float, width: Float, height: Float) {
    if (height <= 0f) return
    val r = minOf(4.dp.toPx(), height, width / 2)
    val path = Path().apply {
        moveTo(x, top + height)
        lineTo(x, top + r)
        quadraticBezierTo(x, top, x + r, top)
        lineTo(x + width - r, top)
        quadraticBezierTo(x + width, top, x + width, top + r)
        lineTo(x + width, top + height)
        close()
    }
    drawPath(path, color)
}

/** A month-style grid of days, Monday first, shaded by how much was spent. */
@Composable
fun SpendCalendar(
    days: List<DayCell>,
    colors: ChartColors,
    selected: DayCell?,
    onSelect: (DayCell) -> Unit,
    describe: (DayCell) -> String,
    modifier: Modifier = Modifier
) {
    if (days.isEmpty()) return
    val lead = (days.first().date.dayOfWeek.value - DayOfWeek.MONDAY.value)
    val cells: List<DayCell?> = List(lead) { null } + days
    val weekday = MaterialTheme.typography.labelSmall.copy(color = colors.muted)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, style = weekday, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (day != null) CalendarCell(day, colors, day == selected, { onSelect(day) }, describe(day))
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CalendarCell(day: DayCell, colors: ChartColors, isSelected: Boolean, onClick: () -> Unit, description: String) {
    val shape = RoundedCornerShape(6.dp)
    val base = Modifier.fillMaxWidth().fillMaxHeight().clip(shape)
    val modifier = when {
        day.isFuture -> base.border(BorderStroke(1.dp, colors.grid), shape)
        else -> base
            .background(colors.heat(day.step))
            .clickable(role = Role.Button, onClickLabel = "Show this day", onClick = onClick)
            .semantics { contentDescription = description }
    }
    Box(
        modifier = modifier.then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier),
        contentAlignment = Alignment.TopEnd
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = tabularNumbers(MaterialTheme.typography.labelSmall).copy(fontSize = 10.sp),
            color = if (day.step >= 4) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, end = 4.dp)
        )
    }
}

/** The five calendar shades from lightest to darkest, labelled Less and More. */
@Composable
fun HeatLegend(colors: ChartColors) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("Less", style = MaterialTheme.typography.labelSmall, color = colors.muted)
        (1..5).forEach { Box(Modifier.size(width = 14.dp, height = 10.dp).clip(RoundedCornerShape(3.dp)).background(colors.heat(it))) }
        Text("More", style = MaterialTheme.typography.labelSmall, color = colors.muted)
    }
}

/**
 * A row of the breakdown: name and amount, a bar scaled to [max], and a tick at the usual amount.
 * The bar grows from its left edge (a scale, not a re-layout) when the numbers change.
 */
@Composable
fun RankedRow(
    name: String,
    amount: String,
    fraction: Float,
    barColor: Color,
    track: Color,
    modifier: Modifier = Modifier,
    usualFraction: Float? = null,
    overFraction: Float = 0f,
    overColor: Color = barColor,
    note: String? = null,
    noteColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val tickColor = MaterialTheme.colorScheme.onSurface
    val scale by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(300), label = "bar")
    val overScale by animateFloatAsState(overFraction.coerceIn(0f, 1f), tween(300), label = "over")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leading?.invoke()
            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(amount, style = tabularNumbers(MaterialTheme.typography.bodyMedium).copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(8.dp)) {
            Box(Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(track))
            Box(
                Modifier.fillMaxWidth().fillMaxHeight()
                    .graphicsLayer { scaleX = scale; transformOrigin = TransformOrigin(0f, 0.5f) }
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
            if (overScale > 0f) {
                Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                    val start = size.width * (1f - overScale)
                    drawRoundRect(overColor, Offset(start, 0f), Size(size.width - start, size.height), CornerRadius(4.dp.toPx()))
                }
            }
            usualFraction?.let { f ->
                Canvas(Modifier.fillMaxWidth().height(14.dp).offset(y = (-3).dp)) {
                    val x = size.width * f.coerceIn(0f, 1f)
                    drawLine(tickColor, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        note?.let { Text(it, style = tabularNumbers(MaterialTheme.typography.labelMedium), color = noteColor) }
    }
}

/**
 * Where the money went, as one bar: spent, saved and what is left over (hatched). Negative left over
 * (spending more than was earned) shows no hatch.
 */
@Composable
fun FlowBar(spent: Double, saved: Double, leftOver: Double, colors: ChartColors, modifier: Modifier = Modifier) {
    val parts = listOf(spent to colors.moneyOut, saved.coerceAtLeast(0.0) to colors.saved, leftOver.coerceAtLeast(0.0) to null)
    val total = parts.sumOf { it.first }
    if (total <= 0.0) return
    Canvas(modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
        val gap = 2.dp.toPx()
        var x = 0f
        parts.filter { it.first > 0.0 }.forEach { (value, color) ->
            val w = (size.width * (value / total)).toFloat()
            val drawW = (w - gap).coerceAtLeast(1f)
            if (color != null) {
                drawRect(color, Offset(x, 0f), Size(drawW, size.height))
            } else {
                clipRect(x, 0f, x + drawW, size.height) {
                    drawRect(colors.muted.copy(alpha = 0.18f), Offset(x, 0f), Size(drawW, size.height))
                    var s = x - size.height
                    while (s < x + drawW) {
                        drawLine(colors.muted.copy(alpha = 0.7f), Offset(s, size.height), Offset(s + size.height, 0f), 1.5.dp.toPx())
                        s += 5.dp.toPx()
                    }
                }
            }
            x += w
        }
    }
}

/** A small legend swatch; the hatched one stands for Left over. */
@Composable
fun LegendDot(color: Color?, colors: ChartColors) {
    Box(
        Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color ?: colors.muted.copy(alpha = 0.35f))
    )
}

/** A dot in a Bucket's own colour. */
@Composable
fun ColorDot(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

/** Grey shades for Paid from, so accounts never look like money directions. */
@Composable
fun NeutralStack(values: List<Double>, shades: List<Color>, modifier: Modifier = Modifier) {
    val total = values.sum()
    if (total <= 0.0) return
    Row(modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        values.forEachIndexed { i, v ->
            if (v > 0.0) Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(shades[i % shades.size]))
        }
    }
}
