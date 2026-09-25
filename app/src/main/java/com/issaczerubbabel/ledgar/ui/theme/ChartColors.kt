package com.issaczerubbabel.ledgar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.issaczerubbabel.ledgar.data.preferences.ChartPalette

/**
 * The colours of Stats charts. Colour means money direction only: in, out and saved. Each set was
 * checked for red-green colour blindness against the card surface in its own theme, except Classic.
 */
@Immutable
data class ChartColors(
    val moneyIn: Color,
    val moneyOut: Color,
    val saved: Color,
    /** Over a limit. Always shown with an icon and words, never colour alone. */
    val over: Color,
    /** Chart text, grid and the previous period's line. */
    val muted: Color,
    val grid: Color,
    /** The card behind the chart; the day heat ramp fades from it to [moneyOut]. */
    val surface: Color
) {
    /** Shade 0 (nothing spent) to 5 (the busiest days) for the spending calendar. */
    fun heat(step: Int): Color = when {
        step <= 0 -> lerp(surface, muted, 0.12f)
        else -> lerp(surface, moneyOut, HEAT_STEPS[(step - 1).coerceIn(0, HEAT_STEPS.lastIndex)])
    }

    private companion object {
        val HEAT_STEPS = listOf(0.22f, 0.40f, 0.58f, 0.78f, 1f)
    }
}

private data class Trio(val moneyIn: Long, val moneyOut: Long, val saved: Long)

private fun ChartPalette.trio(dark: Boolean): Trio = when (this) {
    ChartPalette.STANDARD -> if (dark) Trio(0xFF3A8AE6, 0xFFD9681A, 0xFF1A9E76) else Trio(0xFF1976D2, 0xFFE8710A, 0xFF0F8F6A)
    ChartPalette.OKABE_ITO -> if (dark) Trio(0xFF3A8FD0, 0xFFD86A1E, 0xFF0F9E78) else Trio(0xFF0072B2, 0xFFD55E00, 0xFF009E73)
    ChartPalette.DUSK -> if (dark) Trio(0xFF6583F0, 0xFFE0701F, 0xFF12A08A) else Trio(0xFF4C6EF5, 0xFFE8590C, 0xFF0A8F7E)
    ChartPalette.SUNSET -> if (dark) Trio(0xFF8A6CF0, 0xFFCC7410, 0xFF16A577) else Trio(0xFF7048E8, 0xFFD46A00, 0xFF0C9A6E)
    ChartPalette.CLASSIC -> if (dark) Trio(0xFF43A047, 0xFFE05252, 0xFF3F86E0) else Trio(0xFF2E7D32, 0xFFC62828, 0xFF1565C0)
}

/** The three money colours of [palette], for swatches outside a chart. */
fun ChartPalette.swatches(dark: Boolean = true): List<Color> = trio(dark).let { listOf(Color(it.moneyIn), Color(it.moneyOut), Color(it.saved)) }

@Composable
fun rememberChartColors(palette: ChartPalette, surface: Color = MaterialTheme.colorScheme.surfaceContainerLow): ChartColors {
    val scheme = MaterialTheme.colorScheme
    return remember(palette, surface, scheme) {
        val t = palette.trio(dark = surface.luminance() < 0.5f)
        ChartColors(
            moneyIn = Color(t.moneyIn),
            moneyOut = Color(t.moneyOut),
            saved = Color(t.saved),
            over = scheme.error,
            muted = scheme.onSurfaceVariant,
            grid = scheme.outlineVariant.copy(alpha = 0.5f),
            surface = surface
        )
    }
}
