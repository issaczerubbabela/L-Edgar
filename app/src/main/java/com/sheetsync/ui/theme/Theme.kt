package com.sheetsync.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Dark colour scheme (teal accents, original) ───────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary            = Teal80,
    onPrimary          = TealDark,
    primaryContainer   = TealDark,
    onPrimaryContainer = Teal80,
    secondary          = Teal40,
    background         = SurfaceDark,
    surface            = Surface2Dark,
    surfaceVariant     = Surface3Dark,
    onBackground       = OnSurfaceDark,
    onSurface          = OnSurfaceDark,
    onSurfaceVariant   = OnSurface2Dark,
    outline            = OutlineDark
)

// ── Light colour scheme ───────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary            = TealPrimary,
    onPrimary          = Color.White,
    primaryContainer   = TealLight,
    onPrimaryContainer = TealDark,
    secondary          = TealPrimary,
    background         = LightBackground,
    surface            = LightSurface,
    surfaceVariant     = LightSurface2,
    onBackground       = TextPrimary,
    onSurface          = TextPrimary,
    onSurfaceVariant   = TextSecondary,
    outline            = LightOutline
)

@Composable
fun SheetSyncTheme(isDarkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDarkTheme) DarkColorScheme else LightColorScheme,
        typography  = Typography,
        content     = content
    )
}
