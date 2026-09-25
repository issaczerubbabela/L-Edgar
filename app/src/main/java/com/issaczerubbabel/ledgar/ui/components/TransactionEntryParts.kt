package com.issaczerubbabel.ledgar.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.issaczerubbabel.ledgar.ui.theme.ExpenseOrange
import com.issaczerubbabel.ledgar.ui.theme.IncomeBlue
import com.issaczerubbabel.ledgar.util.TransactionType

/** One tappable chip under the amount: [filled] once the user has chosen a value for it. */
data class ChipSpec(val label: String, val filled: Boolean, val onClick: () -> Unit)

/**
 * The big amount at the right edge, coloured by transaction [type]. It shrinks as it grows and
 * pulses whenever [pulseSignal] changes. Shared by Add Transaction and Quick Add so they read alike.
 */
@Composable
fun AmountDisplay(
    amount: String,
    type: String,
    modifier: Modifier = Modifier,
    pulseSignal: Int = 0
) {
    val color = when (type) {
        TransactionType.EXPENSE -> ExpenseOrange
        TransactionType.INCOME -> IncomeBlue
        else -> MaterialTheme.colorScheme.onSurface
    }
    val displayValue = amount.ifEmpty { "0" }
    val baseSp = when {
        displayValue.length <= 5 -> 96f
        displayValue.length <= 8 -> 72f
        else -> 52f
    }
    val fontSize = responsiveTextSize(baseSp = baseSp, minSp = 36f, maxSp = 104f)

    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(pulseSignal) {
        if (pulseSignal == 0) return@LaunchedEffect
        pulseScale.animateTo(1.06f, tween(durationMillis = 90, easing = LinearEasing))
        pulseScale.animateTo(1f, tween(durationMillis = 160, easing = LinearEasing))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .scale(pulseScale.value),
        contentAlignment = Alignment.CenterEnd
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "₹",
                fontSize = fontSize.value.times(0.4f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = fontSize.value.times(0.12f).dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = displayValue,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun responsiveTextSize(baseSp: Float, minSp: Float = 12f, maxSp: Float = 48f) =
    (baseSp * (LocalConfiguration.current.screenWidthDp / 411f).coerceIn(0.9f, 1.08f))
        .coerceIn(minSp, maxSp).sp

/**
 * The chip row under the amount. [primaryChips] animate as a group when [swapKey] changes (for
 * example the category/account pair swapping for From/To on a transfer); [trailingChip] stays put.
 */
@Composable
fun ChipRow(
    primaryChips: List<ChipSpec>,
    swapKey: Any,
    modifier: Modifier = Modifier,
    trailingChip: ChipSpec? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedContent(
            targetState = swapKey to primaryChips,
            contentKey = { it.first },
            transitionSpec = { slideSwap().using(SizeTransform(clip = false)) },
            label = "chip-type-swap"
        ) { (_, chips) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { TransactionChip(it) }
            }
        }
        trailingChip?.let { TransactionChip(it) }
    }
}

@Composable
private fun TransactionChip(chip: ChipSpec) {
    AssistChip(
        onClick = chip.onClick,
        label = { Text(chip.label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (chip.filled) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            labelColor = if (chip.filled) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
