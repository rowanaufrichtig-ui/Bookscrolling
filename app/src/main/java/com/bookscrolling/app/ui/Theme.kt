package com.bookscrolling.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DangerRed = Color(0xFFE0245E)
val BookmarkGreen = Color(0xFF2FA866)

private val Light = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    secondary = Color(0xFF6B6B6B),
    background = Color.White,
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF3F3F3),
    onSurfaceVariant = Color(0xFF6B6B6B),
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFF7F7F7),
    surfaceContainerHighest = Color(0xFFF0F0F0),
    secondaryContainer = Color(0xFFEDEDED),
    outline = Color(0xFFDDDDDD),
    outlineVariant = Color(0xFFEEEEEE),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF2F2F2),
    onPrimary = Color(0xFF111111),
    secondary = Color(0xFF9A9A9A),
    background = Color(0xFF0D0D0D),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF0D0D0D),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFF9A9A9A),
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerLow = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF202020),
    secondaryContainer = Color(0xFF262626),
    outline = Color(0xFF2E2E2E),
    outlineVariant = Color(0xFF1F1F1F),
)

@Composable
fun BookTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) Dark else Light
    MaterialTheme(colorScheme = colors) {
        // Surface sets LocalContentColor so plain Text follows the theme (e.g. light text in dark mode).
        Surface(color = colors.background, contentColor = colors.onBackground, content = content)
    }
}
