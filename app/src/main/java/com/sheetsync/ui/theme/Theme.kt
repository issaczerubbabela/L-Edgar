package com.sheetsync.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = TealDark,
    primaryContainer = TealDark,
    onPrimaryContainer = Teal80,
    secondary = Teal40,
    background = SurfaceDark,
    surface = Surface2Dark,
    surfaceVariant = Surface3Dark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurface2Dark,
    outline = OutlineDark
)

@Composable
fun SheetSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
