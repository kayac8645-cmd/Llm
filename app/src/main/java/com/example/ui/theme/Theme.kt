package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OledDarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF004E5B),
    onPrimaryContainer = Color(0xFF97F0FF),
    
    secondary = NeonViolet,
    onSecondary = Color(0xFF380066),
    secondaryContainer = Color(0xFF56118D),
    onSecondaryContainer = Color(0xFFEADBFF),
    
    tertiary = ElectricBlue,
    onTertiary = Color(0xFF002B75),
    tertiaryContainer = Color(0xFF14449E),
    onTertiaryContainer = Color(0xFFD6E3FF),
    
    background = OledBackground,
    onBackground = TextPrimary,
    
    surface = OledSurface,
    onSurface = TextPrimary,
    surfaceVariant = OledSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    
    outline = OledBorder,
    outlineVariant = OledBorderSubtle,
    
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun LlmWorldTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OledDarkColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun AuraTheme(
    content: @Composable () -> Unit
) {
    LlmWorldTheme(content = content)
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    LlmWorldTheme(content = content)
}
