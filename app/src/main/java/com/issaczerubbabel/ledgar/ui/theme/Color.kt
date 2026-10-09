package com.issaczerubbabel.ledgar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// ── Brand ─────────────────────────────────────────────────────────────────
val LavenderPrimary   = Color(0xFFD0BCFF)
val LavenderSecondary = Color(0xFFCCC2DC)
val LavenderTertiary  = Color(0xFFEFB8C8)
val DarkBackground    = Color(0xFF121212)
val DarkSurface       = Color(0xFF1E1E1E)

val TealPrimary   = Color(0xFF80CBC4)
val TealSecondary = Color(0xFFB2DFDB)
val TealTertiary  = Color(0xFF80DEEA)
val TealPrimaryContainer = Color(0xFF004D40)
val TealSecondaryContainer = Color(0xFF375A57)
val TealTertiaryContainer = Color(0xFF006064)

val RedPrimary   = Color(0xFFEF9A9A)
val RedSecondary = Color(0xFFF48FB1)
val RedTertiary  = Color(0xFFFFAB91)
val RedPrimaryContainer = Color(0xFF5D1F1F)
val RedSecondaryContainer = Color(0xFF5A1E3B)
val RedTertiaryContainer = Color(0xFF5B2B1A)

val TealDark      = Color(0xFF005B4F)
val TealLight     = Color(0xFF4EBAAA)

// ── Transaction colours (Money Manager palette) ───────────────────────────────
/**
 * Income and expense amounts. A dark background gets the lighter shade and a light one the darker,
 * so the amount text stays at 4.5:1 contrast or better in every theme.
 */
val IncomeBlue: Color
    @Composable @ReadOnlyComposable
    get() = if (isDarkBackground()) Color(0xFF64B5F6) else Color(0xFF1565C0)

val ExpenseOrange: Color
    @Composable @ReadOnlyComposable
    get() = if (isDarkBackground()) Color(0xFFFFB74D) else Color(0xFFB35A00)

@Composable
@ReadOnlyComposable
private fun isDarkBackground(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

val TransferGray  = Color(0xFF9E9E9E)  // transfer / neutral amount
val FabRed        = Color(0xFFE53935)  // large action FAB
val IncomeGreen   = Color(0xFF4CAF7D)  // kept for InsightsScreen cards
val ExpenseRed    = Color(0xFFEF5350)  // kept for InsightsScreen cards

// ── Light surface palette ─────────────────────────────────────────────────────
val LightBackground = Color(0xFFFFFFFF)
val LightSurface    = Color(0xFFF8F8F8)
val LightSurface2   = Color(0xFFF0F0F0)
val LightOutline    = Color(0xFFE0E0E0)

// ── Text ─────────────────────────────────────────────────────────────────────
val TextPrimary   = Color(0xFF1A1A1A)
val TextSecondary = Color(0xFF757575)
val TextTertiary  = Color(0xFFBDBDBD)

// ── Calendar category dot palette ───────────────────────────────────────────
val DOT_PALETTE = listOf(
    Color(0xFF26C6DA), // cyan
    Color(0xFFEF5350), // red
    Color(0xFF66BB6A), // green
    Color(0xFFFF9800), // orange
    Color(0xFF7E57C2), // purple
    Color(0xFFFFEE58), // yellow
    Color(0xFF42A5F5), // blue
    Color(0xFFEC407A), // pink
    Color(0xFF8D6E63), // brown
)

/** Colour for a stored bucket colourIndex; wraps so any stored value is safe. */
fun bucketColor(index: Int): Color = DOT_PALETTE[Math.floorMod(index, DOT_PALETTE.size)]

fun categoryDotColor(category: String): Color =
    DOT_PALETTE[Math.abs(category.hashCode()) % DOT_PALETTE.size]
val Teal80    = Color(0xFF80CBC4)
val Teal40    = Color(0xFF00897B)
val SurfaceDark   = Color(0xFF121212)
val Surface2Dark  = Color(0xFF1E1E1E)
val Surface3Dark  = Color(0xFF252525)
val OutlineDark   = Color(0xFF2C2C2C)
val OnSurfaceDark = Color(0xFFE8E8E8)
val OnSurface2Dark= Color(0xFFAAAAAA)
