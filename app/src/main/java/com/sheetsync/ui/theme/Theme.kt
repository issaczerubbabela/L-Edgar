package com.sheetsync.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

enum class AppThemeOption {
    SYSTEM,
    LAVENDER,
    TEAL,
    RED,
}

private val LavenderDarkColorScheme = darkColorScheme(
    primary = LavenderPrimary,
    secondary = LavenderSecondary,
    tertiary = LavenderTertiary,
    background = DarkBackground,
    surface = DarkSurface,
)

private val TealDarkColorScheme = darkColorScheme(
    primary = TealPrimary,
    secondary = TealSecondary,
    tertiary = TealTertiary,
    primaryContainer = TealPrimaryContainer,
    secondaryContainer = TealSecondaryContainer,
    tertiaryContainer = TealTertiaryContainer,
    background = DarkBackground,
    surface = DarkSurface,
)

private val RedDarkColorScheme = darkColorScheme(
    primary = RedPrimary,
    secondary = RedSecondary,
    tertiary = RedTertiary,
    primaryContainer = RedPrimaryContainer,
    secondaryContainer = RedSecondaryContainer,
    tertiaryContainer = RedTertiaryContainer,
    background = DarkBackground,
    surface = DarkSurface,
)

@Composable
fun SheetSyncTheme(
    themeOption: AppThemeOption = AppThemeOption.SYSTEM,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when (themeOption) {
        AppThemeOption.SYSTEM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context)
            } else {
                LavenderDarkColorScheme
            }
        }
        AppThemeOption.LAVENDER -> LavenderDarkColorScheme
        AppThemeOption.TEAL -> TealDarkColorScheme
        AppThemeOption.RED -> RedDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}

@Composable
fun SheetSyncTheme(isDarkTheme: Boolean = true, content: @Composable () -> Unit) {
    SheetSyncTheme(
        themeOption = AppThemeOption.SYSTEM,
        darkTheme = isDarkTheme,
        content = content,
    )
}
