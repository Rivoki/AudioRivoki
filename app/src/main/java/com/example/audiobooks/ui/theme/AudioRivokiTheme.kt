package com.example.audiobooks.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalIsDarkMode = staticCompositionLocalOf { false }

private val LightColors: ColorScheme = lightColorScheme()

private val DarkColors: ColorScheme = darkColorScheme(
    background = Color(0xFF1F1F1F),
    surface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFF2A2A2A),
    primary = Color(0xFF197BAB),
    secondary = Color(0xFF197BAB),
    tertiary = Color(0xFF197BAB),
    onBackground = Color.White,
    onSurface = Color.White,
    onPrimary = Color.White
)

@Composable
fun AudioRivokiTheme(
    isDarkMode: Boolean,
    content: @Composable () -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalIsDarkMode provides isDarkMode
    ) {
        MaterialTheme(
            colorScheme = if (isDarkMode) DarkColors else LightColors,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content
        )
    }
}
