package com.issaczerubbabel.ledgar.ui.components

import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.issaczerubbabel.ledgar.util.KeypadAction

private val KEY_GAP = 8.dp
private val KEY_RADIUS_IDLE = 22.dp
private val KEY_RADIUS_PRESSED = 10.dp
private const val KEYPAD_ROWS = 4

// Keypad fills a fraction of the actual screen height rather than a fixed dp value, so it
// reads as a real keypad (roughly half the screen) on any device instead of a fixed-size strip
// that shrinks to a sliver on taller screens.
private const val KEYPAD_HEIGHT_FRACTION = 0.48f
private val KEYPAD_MIN_HEIGHT = 300.dp
private val KEYPAD_MAX_HEIGHT = 460.dp

/**
 * Custom weighted numeric keypad: 7 8 9 [backspace] / 4 5 6 [commit, spans 3 rows] /
 * 1 2 3 / 0 [spans 2 cells] .
 *
 * Emits [KeypadAction]s for digit/dot/backspace against the caller's amount string, and calls
 * [onCommit] from a dedicated key that spans the right column's lower three rows.
 *
 * The keypad never pads for the system navigation bar itself: whatever hosts it (the screen's
 * Scaffold, or the Quick Add sheet) owns the bottom inset, so it is applied once and cannot eat
 * into the fixed key heights.
 *
 * The outer row is given an explicit height (a fraction of [LocalConfiguration]'s screen
 * height, clamped) rather than left to wrap its content: the commit key uses a column weight
 * to span three rows, and a `ColumnScope.weight` child only resolves sensibly against a
 * bounded parent height — leaving it unbounded let the commit key balloon to fill whatever
 * space Compose handed the row, instead of matching the digit grid.
 */
@Composable
fun NumericKeypad(
    onAction: (KeypadAction) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    commitEnabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current

    fun act(action: KeypadAction) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onAction(action)
    }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val keypadHeight = (screenHeightDp * KEYPAD_HEIGHT_FRACTION).coerceIn(KEYPAD_MIN_HEIGHT, KEYPAD_MAX_HEIGHT)
    val keyRowHeight = (keypadHeight - KEY_GAP * (KEYPAD_ROWS - 1)) / KEYPAD_ROWS
    val keyFontSize = (keyRowHeight.value * 0.34f).coerceIn(24f, 38f)
    val keyIconSize = (keyRowHeight.value * 0.36f).dp.coerceIn(22.dp, 34.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(keypadHeight)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP)
    ) {
        Column(
            modifier = Modifier.weight(3f),
            verticalArrangement = Arrangement.spacedBy(KEY_GAP)
        ) {
            KeypadRow(keyRowHeight) {
                SymbolKey("7", keyFontSize, Modifier.weight(1f)) { act(KeypadAction.Digit(7)) }
                SymbolKey("8", keyFontSize, Modifier.weight(1f)) { act(KeypadAction.Digit(8)) }
                SymbolKey("9", keyFontSize, Modifier.weight(1f)) { act(KeypadAction.Digit(9)) }
            }
            KeypadRow(keyRowHeight) {
                SymbolKey("4", keyFontSize, Modifier.weight(1f)) { act(KeypadAction.Digit(4)) }
                SymbolKey("5", keyFontSize, Modifier.weight(1f)) { act(KeypadAction.Digit(5)) }
                SymbolKey("6", keyFontSize, Modifier.weight(1f)) { act(KeypadAction.Digit(6)) }
            }
            KeypadRow(keyRowHeight) {
                SymbolKey("1", keyFontSize, Modifier.weight(1f)) { act(KeypadAction.Digit(1)) }
                SymbolKey("2", keyFontSize, Modifier.weight(1f)) { act(KeypadAction.Digit(2)) }
                SymbolKey("3", keyFontSize, Modifier.weight(1f)) { act(KeypadAction.Digit(3)) }
            }
            KeypadRow(keyRowHeight) {
                SymbolKey("0", keyFontSize, Modifier.weight(2f)) { act(KeypadAction.Digit(0)) }
                SymbolKey(".", keyFontSize, Modifier.weight(1f)) { act(KeypadAction.Dot) }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(KEY_GAP)
        ) {
            KeypadKey(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(keyRowHeight),
                background = MaterialTheme.colorScheme.surfaceVariant,
                onClick = { act(KeypadAction.Backspace) }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(keyIconSize)
                )
            }

            val commitBackground by animateColorAsState(
                targetValue = if (commitEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                animationSpec = tween(200),
                label = "commitBackground"
            )
            KeypadKey(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                background = commitBackground,
                enabled = commitEnabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCommit()
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Save transaction",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(keyIconSize * 1.15f)
                )
            }
        }
    }
}

/**
 * Shared key surface: morphs its corner radius from [KEY_RADIUS_IDLE] to
 * [KEY_RADIUS_PRESSED] while held, via [animateDpAsState] on the pressed interaction state, so
 * every key gets the same tactile "squish" feedback instead of a flat ripple alone.
 */
@Composable
private fun KeypadKey(
    modifier: Modifier = Modifier,
    background: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) KEY_RADIUS_PRESSED else KEY_RADIUS_IDLE,
        animationSpec = tween(120),
        label = "keyCornerRadius"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(background)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun KeypadRow(rowHeight: Dp, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
        content = content
    )
}

@Composable
private fun SymbolKey(label: String, fontSize: Float, modifier: Modifier = Modifier, onClick: () -> Unit) {
    KeypadKey(
        modifier = modifier.fillMaxHeight(),
        background = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick
    ) {
        Text(
            text = label,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
