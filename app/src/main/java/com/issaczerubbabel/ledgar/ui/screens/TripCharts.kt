package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.issaczerubbabel.ledgar.trip.CategoryTotal
import com.issaczerubbabel.ledgar.trip.DayTotal
import com.issaczerubbabel.ledgar.trip.MemberBalance
import com.issaczerubbabel.ledgar.trip.TripMember
import java.time.LocalDate

/** A horizontal bar, [fraction] of the available width. Outlined bars show a Share against a filled Paid bar. */
@Composable
fun ChartBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp, outlined: Boolean = false) {
    val shape = RoundedCornerShape(height / 2)
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(softFill())) {
        val width = fraction.coerceIn(0f, 1f).coerceAtLeast(0.02f)
        Box(
            Modifier.fillMaxWidth(width).height(height).clip(shape)
                .let { if (outlined) it.border(BorderStroke(2.dp, color), shape) else it.background(color) }
        )
    }
}

/** Where the money went, one bar per Category, largest first. Expenses with no Category show in the error colour. */
@Composable
fun CategoryChart(rows: List<CategoryTotal>) {
    if (rows.isEmpty()) {
        Text("Nothing to show yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val max = rows.maxOf { it.paise }.coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        rows.forEach { row ->
            val tone = if (row.isUncategorised) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        if (row.isUncategorised) "No Category" else row.category,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (row.isUncategorised) tone else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(rupees(row.paise), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                ChartBar(row.paise.toFloat() / max, tone)
            }
        }
    }
}

/** What each person paid against their Share, in their own colour, with the difference spelled out. */
@Composable
fun PaidVsShareChart(members: List<TripMember>, balances: List<MemberBalance>) {
    val max = balances.maxOfOrNull { maxOf(it.paidPaise, it.sharePaise) }?.coerceAtLeast(1L) ?: 1L
    val byId = members.associateBy { it.id }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendKey(filled = true, label = "Paid")
            LegendKey(filled = false, label = "Share")
        }
        balances.forEach { b ->
            val member = byId[b.memberId] ?: return@forEach
            val diff = b.paidPaise - b.sharePaise
            val tone = memberColor(member)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MemberAvatar(member, 28.dp)
                    Text(member.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = when {
                            diff > 0 -> "fronted ${rupees(diff)}"
                            diff < 0 -> "owes ${rupees(-diff)}"
                            else -> "even"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            diff > 0 -> tripPositive()
                            diff < 0 -> tripNegative()
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                AmountBar("Paid", b.paidPaise, b.paidPaise.toFloat() / max, tone, outlined = false)
                AmountBar("Share", b.sharePaise, b.sharePaise.toFloat() / max, tone, outlined = true)
            }
        }
    }
}

@Composable
private fun LegendKey(filled: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val tone = MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            Modifier.size(12.dp).clip(RoundedCornerShape(3.dp))
                .let { if (filled) it.background(tone) else it.border(2.dp, tone, RoundedCornerShape(3.dp)) }
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = tone)
    }
}

@Composable
private fun AmountBar(label: String, paise: Long, fraction: Float, color: Color, outlined: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(rupees(paise), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        ChartBar(fraction, color, height = 8.dp, outlined = outlined)
    }
}

/** Spend per day of the trip as vertical bars; scrolls sideways when the trip has more days than fit. */
@Composable
fun DayChart(days: List<DayTotal>) {
    if (days.isEmpty()) {
        Text("Nothing to show yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val max = days.maxOf { it.paise }.coerceAtLeast(1L)
    val barArea = 120.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = maxWidth
        val slot = (available / days.size).coerceAtLeast(60.dp)
        val scrolls = slot * days.size > available
        Row(
            modifier = (if (scrolls) Modifier.horizontalScroll(rememberScrollState()) else Modifier).fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            days.forEach { day ->
                val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
                Column(
                    modifier = Modifier.width(if (scrolls) slot else available / days.size),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        rupees(day.paise),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (day.paise == 0L) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                    Box(Modifier.height(barArea).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        val h = if (day.paise == 0L) 3.dp else (barArea * (day.paise.toFloat() / max)).coerceAtLeast(6.dp)
                        Box(
                            Modifier.width(28.dp).height(h)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(if (day.paise == 0L) softFill() else MaterialTheme.colorScheme.primary)
                        )
                    }
                    Text(
                        date?.let { it.dayOfWeek.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } } ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        date?.let { "${it.dayOfMonth} ${it.month.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }}" } ?: day.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
