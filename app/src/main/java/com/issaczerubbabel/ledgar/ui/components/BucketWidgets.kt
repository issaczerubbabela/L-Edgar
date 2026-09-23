package com.issaczerubbabel.ledgar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.issaczerubbabel.ledgar.data.bucket.BucketSummary
import com.issaczerubbabel.ledgar.ui.theme.ExpenseRed
import com.issaczerubbabel.ledgar.ui.theme.bucketColor
import com.issaczerubbabel.ledgar.util.formatRupees
import kotlin.math.abs
import kotlin.math.roundToInt

/** Digits of equal width so amounts line up down a column and do not jitter as they change. */
@Composable
fun tabularNumbers(style: TextStyle): TextStyle = style.copy(fontFeatureSettings = "tnum")

/**
 * A budget bar with a marker for where you would be if you spent evenly.
 *
 * The white tick is the point of the design: a bar that is 40% full is fine on day 10 of 30 and
 * a warning on day 25, and the tick is what tells those apart. Pass a null [paceFraction] to
 * hide it, as a closed cycle has no "today". An overspent bar is filled with diagonal stripes
 * as well as being red, so the state does not depend on colour alone.
 */
@Composable
fun PaceBar(
    spentFraction: Float,
    paceFraction: Float?,
    color: Color,
    isOver: Boolean,
    modifier: Modifier = Modifier
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tick = MaterialTheme.colorScheme.onSurface
    val stripe = MaterialTheme.colorScheme.background.copy(alpha = 0.35f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
    ) {
        val barHeight = 10.dp.toPx()
        val top = (size.height - barHeight) / 2f
        val radius = CornerRadius(barHeight / 2f, barHeight / 2f)

        drawRoundRect(track, Offset(0f, top), Size(size.width, barHeight), radius)

        val fillWidth = size.width * spentFraction.coerceIn(0f, 1f)
        if (fillWidth > 0f) {
            drawRoundRect(if (isOver) ExpenseRed else color, Offset(0f, top), Size(fillWidth, barHeight), radius)
            if (isOver) {
                clipRect(left = 0f, top = top, right = fillWidth, bottom = top + barHeight) {
                    var x = -barHeight
                    while (x < fillWidth) {
                        drawLine(
                            color = stripe,
                            start = Offset(x, top + barHeight),
                            end = Offset(x + barHeight, top),
                            strokeWidth = 3.dp.toPx()
                        )
                        x += 9.dp.toPx()
                    }
                }
            }
        }

        paceFraction?.let { pace ->
            val x = pace.coerceIn(0f, 1f) * size.width
            drawLine(
                color = tick,
                start = Offset(x, top - 2.dp.toPx()),
                end = Offset(x, top + barHeight + 2.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun BucketColorDot(colorIndex: Int, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 10.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(bucketColor(colorIndex), CircleShape)
    )
}

/** The pill shown on an overspent bucket. Icon and words as well as colour. */
@Composable
fun OverBudgetFlag(overBy: Double, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(ExpenseRed.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(14.dp))
        Text(
            text = "Over by ${formatRupees(overBy)}",
            style = tabularNumbers(MaterialTheme.typography.labelSmall),
            color = ExpenseRed,
            maxLines = 1
        )
    }
}

/** A small labelled figure, used for the row of headline numbers under the hero amount. */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = tabularNumbers(MaterialTheme.typography.titleMedium),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** One bucket on the Budget home: name, what is left, the pace bar and the numbers behind it. */
@Composable
fun BucketRow(
    summary: BucketSummary,
    paceFraction: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bucket = summary.bucket
    val percent = if (summary.spentFraction.isInfinite()) null else (summary.spentFraction * 100).roundToInt()
    val description = buildString {
        append(bucket.name)
        append(": ")
        append(formatRupees(summary.spent)).append(" spent of ").append(formatRupees(bucket.allocatedAmount))
        if (summary.isOver) {
            append(", over by ").append(formatRupees(abs(summary.remaining)))
        } else {
            append(", ").append(formatRupees(summary.remaining)).append(" left")
        }
        if (bucket.note.isNotBlank()) append(". ").append(bucket.note)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BucketColorDot(bucket.colorIndex)
            if (bucket.emoji.isNotBlank()) {
                Text(bucket.emoji, modifier = Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                text = bucket.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.weight(1f))
            if (summary.isOver) {
                OverBudgetFlag(overBy = abs(summary.remaining))
            } else {
                Text(
                    text = "${formatRupees(summary.remaining)} left",
                    style = tabularNumbers(MaterialTheme.typography.titleMedium),
                    maxLines = 1
                )
            }
        }

        if (bucket.note.isNotBlank()) {
            Text(
                text = bucket.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 18.dp)
            )
        }

        PaceBar(
            spentFraction = summary.spentFraction,
            paceFraction = paceFraction,
            color = bucketColor(bucket.colorIndex),
            isOver = summary.isOver
        )

        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${formatRupees(summary.spent)} of ${formatRupees(bucket.allocatedAmount)}",
                style = tabularNumbers(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = percent?.let { "$it%" } ?: "—",
                style = tabularNumbers(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
