package com.anivault.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryOrange,
    onPrimary = TextOnPrimary,
    primaryContainer = PrimaryOrangeDark,
    onPrimaryContainer = TextOnPrimary,

    secondary = AccentPurple,
    onSecondary = TextOnPrimary,
    secondaryContainer = Color(0xFF4A148C),
    onSecondaryContainer = TextOnPrimary,

    tertiary = AccentBlue,
    onTertiary = TextOnPrimary,
    tertiaryContainer = Color(0xFF0D47A1),
    onTertiaryContainer = TextOnPrimary,

    background = BackgroundBlack,
    onBackground = TextPrimary,

    surface = SurfacePrimary,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceSecondary,
    onSurfaceVariant = TextSecondary,

    error = ErrorRed,
    onError = TextOnPrimary,
    errorContainer = Color(0xFFB71C1C),
    onErrorContainer = TextOnPrimary,

    outline = BorderColor,
    outlineVariant = DividerColor,

    surfaceTint = PrimaryOrange
)

@Composable
fun AnivaultTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = BackgroundBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
