package com.issaczerubbabel.ledgar.ui.screens

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.bucket.BucketSummary
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.ui.components.DateRangePickerDialog
import com.issaczerubbabel.ledgar.ui.components.SingleDatePickerDialog
import com.issaczerubbabel.ledgar.ui.components.tabularNumbers
import com.issaczerubbabel.ledgar.ui.theme.ChartColors
import com.issaczerubbabel.ledgar.ui.theme.bucketColor
import com.issaczerubbabel.ledgar.ui.theme.rememberChartColors
import com.issaczerubbabel.ledgar.viewmodel.CategoryDetail
import com.issaczerubbabel.ledgar.viewmodel.DayCell
import com.issaczerubbabel.ledgar.viewmodel.DetailRequest
import com.issaczerubbabel.ledgar.viewmodel.StatsPeriod
import com.issaczerubbabel.ledgar.viewmodel.StatsReportUi
import com.issaczerubbabel.ledgar.viewmodel.StatsScope
import com.issaczerubbabel.ledgar.viewmodel.StatsUiState
import com.issaczerubbabel.ledgar.viewmodel.StatsViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val WEEKDAY_DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** True when Android's animation scale is 0, so count-ups, stagger and shimmer are skipped. */
@Composable
private fun animationsOff(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    innerPadding: PaddingValues,
    onSetUpCycle: () -> Unit = {},
    vm: StatsViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val palette by vm.chartPalette.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    val colors = rememberChartColors(palette)
    val listState = rememberLazyListState()
    val compact by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val reduceMotion = animationsOff()
    var firstVisitDone by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().padding(innerPadding)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                StatsHeader(
                    state = state,
                    onScope = vm::selectScope,
                    onPrevious = vm::previousPeriod,
                    onNext = vm::nextPeriod,
                    onPickDate = { showDatePicker = true },
                    onPickRange = { showRangePicker = true }
                )
            }

            if (state.loading) {
                item(key = "loading") { LoadingCards(reduceMotion) }
                return@LazyColumn
            }

            val cards = statsCards(state, colors, vm, onSetUpCycle)
            cards.forEachIndexed { index, (key, content) ->
                item(key = key) {
                    Entrance(index, enabled = !firstVisitDone && !reduceMotion) { content() }
                }
            }
        }

        // The header collapses into this bar so a scrolled-down card still says which period it shows.
        AnimatedVisibility(
            visible = compact,
            enter = fadeIn(tween(150)) + slideInVertically(tween(200)) { -it / 2 },
            exit = fadeOut(tween(100)) + slideOutVertically(tween(150)) { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(tonalElevation = 3.dp, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = vm::previousPeriod, enabled = state.canGoBack) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous period")
                    }
                    Text(
                        text = periodTitle(state.period) + " · " + scopeLabel(state.scope),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = vm::nextPeriod, enabled = state.canGoForward) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next period")
                    }
                }
            }
        }
    }

    LaunchedEffect(state.loading) {
        if (!state.loading) {
            delay(600)
            firstVisitDone = true
        }
    }

    if (showDatePicker) {
        SingleDatePickerDialog(
            initialDate = state.period.start,
            onDismiss = { showDatePicker = false },
            onConfirm = { vm.jumpTo(it); showDatePicker = false }
        )
    }
    if (showRangePicker) {
        DateRangePickerDialog(
            initialStart = state.period.start,
            initialEnd = state.period.end,
            onDismiss = { showRangePicker = false },
            onConfirm = { a, b -> vm.selectCustomRange(a, b); showRangePicker = false }
        )
    }

    detail?.let { (request, data) ->
        DetailSheet(request, data, colors, state.period, vm::formatRupee, onDismiss = vm::closeDetail)
    }
}

/** The cards below the header, in order, each with a stable key for the list. */
private fun statsCards(
    state: StatsUiState,
    colors: ChartColors,
    vm: StatsViewModel,
    onSetUpCycle: () -> Unit
): List<Pair<String, @Composable () -> Unit>> {
    val report = state.report
    val rupees = vm::formatRupee
    val cards = mutableListOf<Pair<String, @Composable () -> Unit>>()
    cards += "headline" to { HeadlineCard(state, colors, rupees) }
    if (!report.hasTransactions && report.cycle == null) {
        cards += "empty" to { EmptyPeriodCard(state, vm::previousPeriod) }
        return cards
    }
    cards += "pace" to { PaceCard(state, colors, rupees, onSetUpCycle) }
    val cycle = report.cycle
    if (cycle != null) {
        cards += "buckets" to {
            BucketsCard(cycle.summary.buckets, cycle.summary.unbucketed.sumOf { it.amount }, cycle.summary.unbucketed.map { it.category }, colors, rupees) { vm.openDetail(it) }
        }
    } else {
        cards += "categories" to { CategoriesCard(report, colors, rupees, state.period) { vm.openDetail(it) } }
    }
    if (report.days.isNotEmpty()) cards += "calendar" to { CalendarCard(report.days, colors, rupees) }
    cards += "paid" to { PaidFromCard(report, rupees) }
    if (report.trend.size > 1) cards += "trend" to { TrendCard(state, colors, rupees) }
    if (report.biggest.isNotEmpty()) cards += "biggest" to { BiggestCard(report.biggest, rupees) }
    return cards
}

// ---- header ------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsHeader(
    state: StatsUiState,
    onScope: (StatsScope) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit,
    onPickRange: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Stats", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).semantics { heading() })
            Text(
                "as of ${state.today.format(DAY_MONTH)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val scopes = buildList {
            if (state.hasCycles) add(StatsScope.CYCLE)
            add(StatsScope.WEEKLY); add(StatsScope.MONTHLY); add(StatsScope.YEARLY)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            scopes.forEachIndexed { i, scope ->
                SegmentedButton(
                    selected = state.scope == scope,
                    onClick = { onScope(scope) },
                    shape = SegmentedButtonDefaults.itemShape(i, scopes.size),
                    icon = {},
                    label = { Text(scopeLabel(scope), maxLines = 1) }
                )
            }
        }
        // Swiping sideways on this strip only (never the whole page) changes period.
        var drag by remember { mutableStateOf(0f) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.pointerInput(state.canGoBack, state.canGoForward) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (drag > 80f && state.canGoBack) onPrevious()
                        if (drag < -80f && state.canGoForward) onNext()
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                    onHorizontalDrag = { _, amount -> drag += amount }
                )
            }
        ) {
            IconButton(onClick = onPrevious, enabled = state.canGoBack) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous period")
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClickLabel = "Choose a period") { menuOpen = true }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(periodTitle(state.period), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        periodSubtitle(state.period, state.today),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Go to a date") }, onClick = { menuOpen = false; onPickDate() })
                    DropdownMenuItem(text = { Text("Custom range") }, onClick = { menuOpen = false; onPickRange() })
                }
            }
            IconButton(onClick = onNext, enabled = state.canGoForward) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next period")
            }
        }
    }
}

private fun scopeLabel(scope: StatsScope) = when (scope) {
    StatsScope.CYCLE -> "Cycle"
    StatsScope.WEEKLY -> "Week"
    StatsScope.MONTHLY -> "Month"
    StatsScope.YEARLY -> "Year"
    StatsScope.SELECT_PERIOD -> "Range"
}

private fun previousPeriodName(p: StatsPeriod): String = when (p) {
    is StatsPeriod.Week -> "last week"
    is StatsPeriod.Month -> "last month"
    is StatsPeriod.Year -> "last year"
    is StatsPeriod.Cycle -> "last cycle"
    is StatsPeriod.Custom -> "the ${p.days} days before"
}

private fun periodTitle(p: StatsPeriod): String = when (p) {
    is StatsPeriod.Month -> p.month.format(MONTH_YEAR)
    is StatsPeriod.Year -> p.year.toString()
    else -> "${p.start.format(DAY_MONTH)} – ${p.end.format(DAY_MONTH)}"
}

private fun periodSubtitle(p: StatsPeriod, today: LocalDate): String {
    val running = !today.isBefore(p.start) && !today.isAfter(p.end)
    return when {
        p is StatsPeriod.Cycle && !p.span.isRunning -> "Closed · ${p.days} days"
        running -> "Day ${ChronoUnit.DAYS.between(p.start, today) + 1} of ${p.days}"
        today.isBefore(p.start) -> "Not started"
        else -> "${p.days} days"
    }
}

// ---- motion helpers ----------------------------------------------------------------------------

/** Cards fade in and rise 12dp, 40ms apart, on the first visit in a session only. */
@Composable
private fun Entrance(index: Int, enabled: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(40L * index)
            progress.animateTo(1f, tween(250, easing = EmphasizedDecelerate))
        }
    }
    Box(Modifier.graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * 12.dp.toPx() }) { content() }
}

/**
 * A new period slides in from the side you moved towards; a new scope fades through, because there
 * is no direction between a week and a year.
 */
private fun periodTransition(from: Pair<StatsScope, LocalDate>, to: Pair<StatsScope, LocalDate>): ContentTransform =
    if (from.first != to.first) {
        fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(210, delayMillis = 90), initialScale = 0.92f) togetherWith fadeOut(tween(90))
    } else {
        val forward = to.second.isAfter(from.second)
        (slideInHorizontally(tween(300, easing = EmphasizedDecelerate)) { w -> if (forward) w / 4 else -w / 4 } + fadeIn(tween(300))) togetherWith
            (slideOutHorizontally(tween(200, easing = EmphasizedAccelerate)) { w -> if (forward) -w / 4 else w / 4 } + fadeOut(tween(200)))
    }

/** The headline number counts from its old value to its new one; the first value shows straight away. */
@Composable
private fun CountingAmount(value: Double, format: (Double) -> String, style: androidx.compose.ui.text.TextStyle, color: Color = Color.Unspecified) {
    val off = animationsOff()
    val shown = remember { Animatable(value.toFloat()) }
    LaunchedEffect(value) {
        if (off) shown.snapTo(value.toFloat()) else shown.animateTo(value.toFloat(), tween(400))
    }
    Text(format(shown.value.toDouble()), style = tabularNumbers(style), color = color, maxLines = 1)
}

// ---- cards -------------------------------------------------------------------------------------

@Composable
private fun StatsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun CardTitle(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

/** A change against the previous period, as an arrow and words, never colour alone. */
@Composable
private fun ChangeChip(text: String, up: Boolean?, goodWhenUp: Boolean) {
    val good = up != null && up == goodWhenUp
    val tint = when {
        up == null -> MaterialTheme.colorScheme.onSurfaceVariant
        good -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }
    Row(
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            when (up) { true -> Icons.Filled.TrendingUp; false -> Icons.Filled.TrendingDown; null -> Icons.Filled.TrendingFlat },
            contentDescription = null, tint = tint, modifier = Modifier.size(16.dp)
        )
        Text(text, style = tabularNumbers(MaterialTheme.typography.labelMedium), color = tint, maxLines = 1)
    }
}

@Composable
private fun HeadlineCard(state: StatsUiState, colors: ChartColors, rupees: (Double) -> String) {
    StatsCard {
        AnimatedContent(
            targetState = state,
            contentKey = { it.scope to it.period.start },
            transitionSpec = { periodTransition(initialState.scope to initialState.period.start, targetState.scope to targetState.period.start) },
            label = "headline"
        ) { s ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val cycle = s.report.cycle
                if (cycle != null) CycleHeadline(s, cycle, colors, rupees) else LeftOverHeadline(s.report, s.period, colors, rupees)
            }
        }
    }
}

@Composable
private fun CycleHeadline(s: StatsUiState, cycle: com.issaczerubbabel.ledgar.viewmodel.CycleView, colors: ChartColors, rupees: (Double) -> String) {
    val summary = cycle.summary
    val left = summary.leftToSpend
    CardTitle(if (cycle.isRunning) "Left to spend" else "Left unspent")
    CountingAmount(left, rupees, MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), if (left < 0) colors.over else Color.Unspecified)
    val sub = if (cycle.isRunning && summary.daysLeft > 0) {
        "of ${rupees(summary.cycle.spendableAmount)} · ${summary.daysLeft} days left · ${rupees(summary.dailyPace)} a day"
    } else {
        "of ${rupees(summary.cycle.spendableAmount)} · spent ${rupees(summary.totalSpent)}"
    }
    Text(sub, style = tabularNumbers(MaterialTheme.typography.bodyMedium), color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (cycle.saved > 0) {
        Text(
            "${rupees(cycle.saved)} of that went to savings. Cycles count it against your budget, as the Budget tab does.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LeftOverHeadline(report: StatsReportUi, period: StatsPeriod, colors: ChartColors, rupees: (Double) -> String) {
    val t = report.totals
    CardTitle("Left over") {
        report.previousLeftOver?.let { prev ->
            val diff = t.leftOver - prev
            ChangeChip("${shortRupees(abs(diff))} vs ${previousPeriodName(period)}", if (abs(diff) < 1) null else diff > 0, goodWhenUp = true)
        }
    }
    CountingAmount(t.leftOver, rupees, MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), if (t.leftOver < 0) colors.over else Color.Unspecified)
    if (t.earned > 0) {
        Text(
            "${(t.leftOver / t.earned * 100).roundToInt()}% of ${rupees(t.earned)} earned",
            style = tabularNumbers(MaterialTheme.typography.bodyMedium), color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    FlowBar(t.spent, t.saved, t.leftOver, colors)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendAmount("Earned", t.earned, colors.moneyIn, colors, rupees, Modifier.weight(1f))
            LegendAmount("Spent", t.spent, colors.moneyOut, colors, rupees, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendAmount("Saved", t.saved, colors.saved, colors, rupees, Modifier.weight(1f))
            LegendAmount("Left over", t.leftOver, null, colors, rupees, Modifier.weight(1f))
        }
    }
    if (t.refunds > 0) {
        Text("Spent is after ${rupees(t.refunds)} of refunds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendAmount(label: String, value: Double, color: Color?, colors: ChartColors, rupees: (Double) -> String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LegendDot(color, colors)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(rupees(value), style = tabularNumbers(MaterialTheme.typography.bodySmall).copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun PaceCard(state: StatsUiState, colors: ChartColors, rupees: (Double) -> String, onSetUpCycle: () -> Unit) {
    val report = state.report
    val pace = report.pace
    var selected by remember(state.period) { mutableStateOf<Int?>(null) }
    val previousName = when (state.period) {
        is StatsPeriod.Month -> (state.period.previous() as StatsPeriod.Month).month.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
        is StatsPeriod.Year -> (state.period.year - 1).toString()
        is StatsPeriod.Week, is StatsPeriod.Cycle -> "Last"
        else -> "Before"
    }
    val chipName = if (state.period is StatsPeriod.Month || state.period is StatsPeriod.Year) previousName else previousPeriodName(state.period)
    StatsCard {
        CardTitle(if (report.cycle != null) "Pace against budget" else "Spending pace") {
            val prev = pace.previousAtSamePoint
            if (prev != null && prev > 0 && pace.current.isNotEmpty()) {
                val pct = ((pace.spentSoFar - prev) / prev * 100).roundToInt()
                ChangeChip("${abs(pct)}% vs $chipName", if (pct == 0) null else pct > 0, goodWhenUp = false)
            }
        }
        val readout = selected?.let { i ->
            buildString {
                append(pace.labels[i])
                append(" · ")
                append(pace.current.getOrNull(i)?.let(rupees) ?: "—")
                pace.previous.getOrNull(i)?.let { append(" · $previousName ").append(rupees(it)) }
                pace.budget.getOrNull(i)?.let { append(" · budget ").append(rupees(it)) }
            }
        } ?: buildString {
            append(rupees(pace.spentSoFar)).append(" so far")
            pace.projection?.let { append(" · heading for ~").append(shortRupees(it)) }
            pace.budget.lastOrNull()?.let { append(" · budget ").append(shortRupees(it)) }
        }
        Text(readout, style = tabularNumbers(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        PaceChart(
            pace = pace,
            previousLabel = previousName,
            colors = colors,
            selected = selected,
            onSelect = { selected = it },
            description = "Spent ${rupees(pace.spentSoFar)} so far" + (pace.previousAtSamePoint?.let { ", ${rupees(it)} at the same point last period" } ?: "")
        )
        if (!report.hasBudget && !state.hasCycles) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Set up a salary cycle to see budget pace", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onSetUpCycle) { Text("Set up") }
            }
        }
    }
}

@Composable
private fun BucketsCard(
    buckets: List<BucketSummary>,
    unbucketedSpent: Double,
    unbucketedCategories: List<String>,
    colors: ChartColors,
    rupees: (Double) -> String,
    onOpen: (DetailRequest) -> Unit
) {
    StatsCard {
        CardTitle("Buckets")
        val track = MaterialTheme.colorScheme.surfaceContainerHighest
        buckets.sortedBy { it.bucket.sortOrder }.forEach { b ->
            val limit = b.bucket.allocatedAmount
            val max = maxOf(b.spent, limit).coerceAtLeast(1.0)
            val over = (b.spent - limit).coerceAtLeast(0.0)
            RankedRow(
                name = b.bucket.name,
                amount = "${rupees(b.spent)} / ${shortRupees(limit)}",
                fraction = (minOf(b.spent, limit) / max).toFloat(),
                barColor = colors.muted.copy(alpha = 0.7f),
                track = track,
                overFraction = (over / max).toFloat(),
                overColor = colors.over,
                note = if (over > 0) "Over by ${rupees(over)}" else "${rupees(limit - b.spent)} left",
                noteColor = if (over > 0) colors.over else MaterialTheme.colorScheme.onSurfaceVariant,
                leading = {
                    if (over > 0) Icon(Icons.Filled.Warning, contentDescription = "Over its limit", tint = colors.over, modifier = Modifier.size(16.dp))
                    else ColorDot(bucketColor(b.bucket.colorIndex))
                },
                onClick = { onOpen(DetailRequest(b.bucket.name, b.categories.map { it.category }.toSet(), b.bucket.colorIndex)) }
            )
        }
        if (unbucketedSpent > 0) {
            RankedRow(
                name = "Not in a bucket",
                amount = rupees(unbucketedSpent),
                fraction = 0f,
                barColor = colors.muted,
                track = Color.Transparent,
                note = unbucketedCategories.take(3).joinToString(", ") + if (unbucketedCategories.size > 3) " +${unbucketedCategories.size - 3}" else "",
                onClick = { onOpen(DetailRequest("Not in a bucket", unbucketedCategories.toSet())) }
            )
        }
    }
}

@Composable
private fun CategoriesCard(report: StatsReportUi, colors: ChartColors, rupees: (Double) -> String, period: StatsPeriod, onOpen: (DetailRequest) -> Unit) {
    var showAll by rememberSaveable(period.start, period.end) { mutableStateOf(false) }
    val rows = if (showAll) report.categories else report.categories.take(6)
    val max = report.categories.maxOfOrNull { maxOf(it.amount, it.usual ?: 0.0) }?.coerceAtLeast(1.0) ?: 1.0
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    // With no earlier periods at all (the first year of records, say) "new" would be true of everything.
    val hasHistory = report.categories.any { it.usual != null }
    StatsCard {
        CardTitle("Where it went") {
            if (report.categories.size > 6) {
                TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Top 6" else "All ${report.categories.size}") }
            }
        }
        if (hasHistory) Text("Bar: this period · tick: your usual (average of the 3 before)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        rows.forEach { row ->
            val usual = row.usual
            val delta = usual?.takeIf { it > 0 }?.let { ((row.amount - it) / it * 100).roundToInt() }
            RankedRow(
                name = row.category,
                amount = rupees(row.amount),
                fraction = (row.amount / max).toFloat(),
                barColor = colors.moneyOut,
                track = track,
                usualFraction = usual?.let { (it / max).toFloat() },
                note = when {
                    usual == null -> if (hasHistory) "New this period" else null
                    delta == null || abs(delta) < 5 -> "About usual"
                    delta > 0 -> "▲ $delta% above your usual ${rupees(usual)}"
                    else -> "▼ ${-delta}% below your usual ${rupees(usual)}"
                },
                onClick = { onOpen(DetailRequest(row.category, setOf(row.category))) }
            )
        }
        if (report.totals.refunds > 0) {
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Refunds", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("−" + rupees(report.totals.refunds), style = tabularNumbers(MaterialTheme.typography.bodyMedium), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CalendarCard(days: List<DayCell>, colors: ChartColors, rupees: (Double) -> String) {
    var selected by remember(days.first().date) { mutableStateOf<DayCell?>(null) }
    val peak = days.maxByOrNull { it.spent }
    StatsCard {
        CardTitle("Daily spending")
        Text(
            text = selected?.let { "${it.date.format(WEEKDAY_DAY)} · ${if (it.spent > 0) rupees(it.spent) else "No spending"}" }
                ?: peak?.takeIf { it.spent > 0 }?.let { "Busiest day: ${it.date.format(DAY_MONTH)}, ${rupees(it.spent)}. Tap a day." }
                ?: "No spending yet",
            style = tabularNumbers(MaterialTheme.typography.bodySmall),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SpendCalendar(
            days = days,
            colors = colors,
            selected = selected,
            onSelect = { selected = if (selected == it) null else it },
            describe = { "${it.date.format(WEEKDAY_DAY)}, ${if (it.spent > 0) rupees(it.spent) else "no spending"}" }
        )
        HeatLegend(colors)
    }
}

@Composable
private fun PaidFromCard(report: StatsReportUi, rupees: (Double) -> String) {
    val p = report.paidFrom
    val shades = listOf(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.outline)
    StatsCard {
        CardTitle("Paid from")
        NeutralStack(listOf(p.cashAndAccounts, p.card), shades)
        val total = (p.cashAndAccounts + p.card).coerceAtLeast(1.0)
        listOf("Cash and bank" to p.cashAndAccounts, "Cards" to p.card).forEachIndexed { i, (label, v) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(9.dp).background(shades[i], RoundedCornerShape(3.dp)))
                Text("$label · ${(v / total * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(rupees(v), style = tabularNumbers(MaterialTheme.typography.bodySmall).copy(fontWeight = FontWeight.SemiBold))
            }
        }
        if (p.transfers > 0) {
            Text("Transfers between accounts: ${rupees(p.transfers)} (not spending)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrendCard(state: StatsUiState, colors: ChartColors, rupees: (Double) -> String) {
    val trend = state.report.trend
    var selected by remember(state.period) { mutableStateOf<Int?>(null) }
    val title = when (state.period) {
        is StatsPeriod.Cycle -> "Cycle by cycle"
        is StatsPeriod.Week -> "Week by week"
        else -> "Month by month"
    }
    val avgLeft = trend.filter { it.earned > 0 || it.spent > 0 }.map { it.leftOver }.takeIf { it.isNotEmpty() }?.average()
    StatsCard {
        CardTitle(title)
        Text(
            selected?.let { i -> trend[i].let { "${it.label} · earned ${rupees(it.earned)} · spent ${rupees(it.spent)} · saved ${rupees(it.saved)}" } }
                ?: avgLeft?.let { "On average ${rupees(it)} left over each time. Tap a bar." } ?: "",
            style = tabularNumbers(MaterialTheme.typography.bodySmall),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TrendChart(
            trend = trend,
            colors = colors,
            selected = selected,
            onSelect = { selected = it },
            description = "$title: " + (avgLeft?.let { "on average ${rupees(it)} left over" } ?: "no data")
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("Spent" to colors.moneyOut, "Saved" to colors.saved, "Earned" to colors.moneyIn).forEach { (label, c) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (label == "Earned") Box(Modifier.size(width = 12.dp, height = 3.dp).background(c, RoundedCornerShape(2.dp)))
                    else LegendDot(c, colors)
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BiggestCard(biggest: List<ExpenseRecord>, rupees: (Double) -> String) {
    StatsCard {
        CardTitle("Biggest expenses")
        biggest.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TransactionLine(r, rupees)
        }
    }
}

@Composable
private fun TransactionLine(r: ExpenseRecord, rupees: (Double) -> String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.description.ifBlank { r.category }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val date = com.issaczerubbabel.ledgar.util.parseFlexibleDate(r.date)?.format(DAY_MONTH) ?: r.date
            Text("${r.category} · $date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(rupees(r.amount), style = tabularNumbers(MaterialTheme.typography.bodyMedium).copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun EmptyPeriodCard(state: StatsUiState, onPrevious: () -> Unit) {
    StatsCard {
        Text("Nothing in this ${scopeLabel(state.scope).lowercase()} yet", style = MaterialTheme.typography.titleSmall)
        Text("Transactions you add for these dates will show up here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.canGoBack) OutlinedButton(onClick = onPrevious) { Text("‹ Previous ${scopeLabel(state.scope).lowercase()}") }
    }
}

/** Placeholders shaped like the cards, shown only if loading takes longer than 150ms. */
@Composable
private fun LoadingCards(reduceMotion: Boolean) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); visible = true }
    if (!visible) return
    val shimmer = remember { Animatable(0.35f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            shimmer.animateTo(0.7f, tween(600))
            shimmer.animateTo(0.35f, tween(600))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(150.dp, 230.dp, 260.dp).forEach { h ->
            Box(
                Modifier.fillMaxWidth().height(h)
                    .graphicsLayer { alpha = shimmer.value }
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(18.dp))
            )
        }
    }
}

// ---- drill-down ----------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailSheet(
    request: DetailRequest,
    data: CategoryDetail,
    colors: ChartColors,
    period: StatsPeriod,
    rupees: (Double) -> String,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    request.bucketColorIndex?.let { ColorDot(bucketColor(it)) }
                    Text(request.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                }
                Text(periodTitle(period), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("This period", rupees(data.spent), Modifier.weight(1f))
                    Stat("Usual", data.usual?.let(rupees) ?: "—", Modifier.weight(1f))
                    Stat("Per day", rupees(data.perDay), Modifier.weight(1f))
                }
            }
            if (data.trend.size > 1) {
                item {
                    var selected by remember { mutableStateOf<Int?>(null) }
                    Text(
                        selected?.let { "${data.trend[it].label} · ${rupees(data.trend[it].spent)}" } ?: "Tap a bar",
                        style = tabularNumbers(MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TrendChart(data.trend, colors, selected, { selected = it }, "${request.title} over time")
                }
            }
            item { Text("Transactions", style = MaterialTheme.typography.titleSmall) }
            if (data.transactions.isEmpty()) {
                item { Text("None in this period", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(data.transactions.take(100), key = { it.id }) { TransactionLine(it, rupees) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = tabularNumbers(MaterialTheme.typography.titleSmall), maxLines = 1)
    }
}
