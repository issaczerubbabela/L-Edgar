package com.issaczerubbabel.ledgar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.issaczerubbabel.ledgar.util.KeypadAction

private val KEY_ROW_HEIGHT = 60.dp
private val KEY_GAP = 1.dp

/**
 * Custom weighted numeric keypad: 7 8 9 [backspace] / 4 5 6 [commit, spans 3 rows] /
 * 1 2 3 / 0 [spans 2 cells] .
 *
 * Emits [KeypadAction]s for digit/dot/backspace against the caller's amount string, and calls
 * [onCommit] from a dedicated key that spans the right column's lower three rows.
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP)
    ) {
        Column(
            modifier = Modifier.weight(3f),
            verticalArrangement = Arrangement.spacedBy(KEY_GAP)
        ) {
            KeypadRow {
                DigitKey(7, Modifier.weight(1f)) { act(KeypadAction.Digit(7)) }
                DigitKey(8, Modifier.weight(1f)) { act(KeypadAction.Digit(8)) }
                DigitKey(9, Modifier.weight(1f)) { act(KeypadAction.Digit(9)) }
            }
            KeypadRow {
                DigitKey(4, Modifier.weight(1f)) { act(KeypadAction.Digit(4)) }
                DigitKey(5, Modifier.weight(1f)) { act(KeypadAction.Digit(5)) }
                DigitKey(6, Modifier.weight(1f)) { act(KeypadAction.Digit(6)) }
            }
            KeypadRow {
                DigitKey(1, Modifier.weight(1f)) { act(KeypadAction.Digit(1)) }
                DigitKey(2, Modifier.weight(1f)) { act(KeypadAction.Digit(2)) }
                DigitKey(3, Modifier.weight(1f)) { act(KeypadAction.Digit(3)) }
            }
            KeypadRow {
                SymbolKey("0", Modifier.weight(2f)) { act(KeypadAction.Digit(0)) }
                SymbolKey(".", Modifier.weight(1f)) { act(KeypadAction.Dot) }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(KEY_ROW_HEIGHT)
                    .clickable { act(KeypadAction.Backspace) }
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(KEY_GAP))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(enabled = commitEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCommit()
                    }
                    .background(
                        if (commitEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Save transaction",
                    tint = if (commitEnabled) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun KeypadRow(content: @Composable Row.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(KEY_ROW_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
        content = content
    )
}

@Composable
private fun DigitKey(value: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    SymbolKey(value.toString(), modifier, onClick)
}

@Composable
private fun SymbolKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
