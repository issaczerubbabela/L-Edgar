package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.issaczerubbabel.ledgar.trip.MemberPalette
import com.issaczerubbabel.ledgar.trip.TripMath
import com.issaczerubbabel.ledgar.trip.TripMember
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Longest a Trip screen's content grows on tablets and foldables; phones use the full width. */
private val MaxContentWidth = 640.dp
private val CardShape = RoundedCornerShape(20.dp)
private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM")

internal fun rupees(paise: Long): String = TripMath.rupees(paise)

internal fun dayLabel(date: String): String =
    runCatching { LocalDate.parse(date).format(dayFormat) }.getOrDefault(date)

@Composable
private fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Green for money owed to someone. Defined for light and dark; never used as a Member colour. */
@Composable
fun tripPositive(): Color = if (isDarkSurface()) Color(0xFF5FD19B) else Color(0xFF18794E)

/** Red for money someone owes. */
@Composable
fun tripNegative(): Color = if (isDarkSurface()) Color(0xFFFF8A7A) else Color(0xFFC8382A)

fun memberColor(member: TripMember): Color = Color(0xFF000000L or MemberPalette.colorFor(member.colorIndex))

fun memberColorAt(index: Int): Color = Color(0xFF000000L or MemberPalette.colorFor(index))

/** A neutral fill for tiles inside cards, derived from the theme so it suits every app theme. */
@Composable
fun softFill(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)

@Composable
fun hairline(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

/** Centres a screen's content and stops it stretching across a tablet. */
@Composable
fun TripContentWidth(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = MaxContentWidth).fillMaxSize()) { content() }
    }
}

@Composable
fun MemberAvatar(member: TripMember, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(memberColor(member)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = member.name.trim().take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp
        )
    }
}

/** Overlapping avatars, for a Trip's header and list rows. */
@Composable
fun AvatarStack(members: List<TripMember>, size: Dp = 28.dp, step: Dp = 20.dp, ring: Color = MaterialTheme.colorScheme.surface) {
    val shown = members.take(6)
    Box(Modifier.width(size + step * (shown.size - 1).coerceAtLeast(0)).height(size)) {
        shown.forEachIndexed { i, m ->
            MemberAvatar(m, size, Modifier.offset(x = step * i).border(2.dp, ring, CircleShape))
        }
    }
}

@Composable
fun TripCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it },
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, hairline())
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** A tile inside a card, for a single figure. */
@Composable
fun SoftTile(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(softFill()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

@Composable
fun Tag(text: String, warn: Boolean = false, modifier: Modifier = Modifier) {
    val bg = if (warn) MaterialTheme.colorScheme.errorContainer else softFill()
    val fg = if (warn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        color = fg,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

/** Pill-shaped tabs that scroll sideways when there isn't room, at any width or font size. */
@Composable
fun PillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.inverseSurface else Color.Transparent)
                    .border(1.dp, if (on) Color.Transparent else hairline(), CircleShape)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) })
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** A person chosen with a tap: their avatar and name in a pill, 48dp tall. */
@Composable
fun MemberChip(member: TripMember, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, radio: Boolean = false) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .border(1.dp, if (selected) Color.Transparent else hairline(), CircleShape)
            .selectable(selected = selected, role = if (radio) Role.RadioButton else Role.Checkbox, onClick = onClick)
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MemberAvatar(member, 30.dp)
        Text(member.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}

/**
 * Two figures side by side, or stacked on narrow screens and at large font sizes so that amounts
 * never wrap or get cut off.
 */
@Composable
fun TwoUp(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    val tight = LocalConfiguration.current.screenWidthDp < 340 || LocalDensity.current.fontScale > 1.3f
    if (tight) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
        }
    }
}
