package com.sheetsync.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand ─────────────────────────────────────────────────────────────────
val LavenderPrimary   = Color(0xFFD0BCFF)
val LavenderSecondary = Color(0xFFCCC2DC)
val LavenderTertiary  = Color(0xFFEFB8C8)
val DarkBackground    = Color(0xFF121212)
val DarkSurface       = Color(0xFF1E1E1E)

val TealPrimary   = Color(0xFF80CBC4)
val TealSecondary = Color(0xFFB2DFDB)
val TealTertiary  = Color(0xFF80DEEA)

val TealDark      = Color(0xFF005B4F)
val TealLight     = Color(0xFF4EBAAA)
val HeaderGreen   = Color(0xFF20C997)  // explicit alias for readability
val SelectedNavy  = Color(0xFF1A3A5C)  // calendar selected-cell bg in light theme

// ── Transaction colours (Money Manager palette) ───────────────────────────────
val IncomeBlue    = Color(0xFF1976D2)   // income amounts
val ExpenseOrange = Color(0xFFF57C00)  // expense amounts
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
