package com.weproz.superreader.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppTheme {
    LIGHT, DARK, SEPIA
}

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val SepiaColorScheme = lightColorScheme(
    primary = SepiaPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE8BC), // Light sepia for containers
    onPrimaryContainer = SepiaOnBackground,

    secondary = SepiaOnBackground,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE4D4), // Lighter sepia for secondary containers
    onSecondaryContainer = SepiaOnBackground,

    tertiary = Pink40, // Keep some accent color

    background = SepiaBackground,
    onBackground = SepiaOnBackground,

    surface = SepiaSurface,
    onSurface = SepiaOnSurface,

    surfaceVariant = Color(0xFFEDE4D4), // Important: Lighter variant for cards
    onSurfaceVariant = SepiaOnBackground,

    outline = Color(0xFF7D7565), // Border color
    outlineVariant = Color(0xFFCEC6B6),

    // Error colors (keep standard)
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Suppress("DEPRECATION")
@Composable
fun SuperReaderTheme(
    appTheme: AppTheme = AppTheme.LIGHT,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (appTheme) {
        AppTheme.LIGHT -> LightColorScheme
        AppTheme.DARK -> DarkColorScheme
        AppTheme.SEPIA -> SepiaColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                appTheme != AppTheme.DARK
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}