package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = AccentBlueContainer,
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = CharcoalSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = WarmSurfaceVariantLight,
    onSecondaryContainer = CharcoalPrimaryLight,
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    background = WarmCanvasLight,
    onBackground = CharcoalPrimaryLight,
    surface = WarmSurfaceLight,
    onSurface = CharcoalPrimaryLight,
    surfaceVariant = WarmSurfaceVariantLight,
    onSurfaceVariant = CharcoalSecondaryLight,
    outline = BorderSubtleLight,
    outlineVariant = Color(0xFFE2E4E8),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedBg,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccentBlue,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = DarkAccentBlueContainer,
    onPrimaryContainer = Color(0xFFBFDBFE),
    secondary = DarkTextSecondary,
    onSecondary = Color(0xFF111317),
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF0C4A6E),
    background = DarkCanvas,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorderSubtle,
    outlineVariant = Color(0xFF374151),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA)
)

@Composable
fun NotesMakerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias for template references
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    NotesMakerTheme(darkTheme = darkTheme, content = content)
}
