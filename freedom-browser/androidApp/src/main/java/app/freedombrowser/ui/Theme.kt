package app.freedombrowser.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.freedombrowser.data.ThemeMode
import app.freedombrowser.shared.DesignTokens

data class FreedomColors(val palette: DesignTokens.Palette, val isDark: Boolean) {
    val accent = Color(palette.accent)
    val private = Color(palette.privateAccent)
    val danger = Color(palette.danger)
    val warning = Color(palette.warning)
    val success = Color(palette.success)
    val textSecondary = Color(palette.textSecondary)
    val surfaceRaised = Color(palette.surfaceRaised)
    val outline = Color(palette.outline)
}

val LocalFreedomColors = staticCompositionLocalOf { FreedomColors(DesignTokens.Dark, true) }

@Composable
fun FreedomTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val p = if (dark) DesignTokens.Dark else DesignTokens.Light
    val scheme = if (dark) darkColorScheme(
        primary = Color(p.accent), onPrimary = Color(p.onAccent), primaryContainer = Color(p.accentSoft), onPrimaryContainer = Color(p.textPrimary),
        secondary = Color(p.accent), onSecondary = Color(p.onAccent), secondaryContainer = Color(p.accentSoft), onSecondaryContainer = Color(p.textPrimary),
        background = Color(p.background), onBackground = Color(p.textPrimary), surface = Color(p.surface), onSurface = Color(p.textPrimary),
        surfaceVariant = Color(p.surfaceRaised), onSurfaceVariant = Color(p.textSecondary), surfaceContainer = Color(p.surface),
        surfaceContainerHigh = Color(p.surfaceRaised), surfaceContainerHighest = Color(p.surfaceHover), surfaceContainerLow = Color(p.surface),
        surfaceContainerLowest = Color(p.background), outline = Color(p.outline), outlineVariant = Color(p.outline), error = Color(p.danger),
    ) else lightColorScheme(
        primary = Color(p.accent), onPrimary = Color(p.onAccent), primaryContainer = Color(p.accentSoft), onPrimaryContainer = Color(p.textPrimary),
        secondary = Color(p.accent), onSecondary = Color(p.onAccent), secondaryContainer = Color(p.accentSoft), onSecondaryContainer = Color(p.textPrimary),
        background = Color(p.background), onBackground = Color(p.textPrimary), surface = Color(p.surface), onSurface = Color(p.textPrimary),
        surfaceVariant = Color(p.surfaceRaised), onSurfaceVariant = Color(p.textSecondary), surfaceContainer = Color(p.surface),
        surfaceContainerHigh = Color(p.surfaceRaised), surfaceContainerHighest = Color(p.surfaceHover), surfaceContainerLow = Color(p.surface),
        surfaceContainerLowest = Color(p.background), outline = Color(p.outline), outlineVariant = Color(p.outline), error = Color(p.danger),
    )
    val base = Typography()
    val typography = base.copy(
        headlineMedium = TextStyle(fontSize = DesignTokens.TEXT_DISPLAY.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        titleMedium = base.titleMedium.copy(fontSize = DesignTokens.TEXT_TITLE.sp, fontWeight = FontWeight.Medium),
        bodyMedium = base.bodyMedium.copy(fontSize = DesignTokens.TEXT_BODY.sp),
        labelSmall = base.labelSmall.copy(fontSize = DesignTokens.TEXT_SMALL.sp),
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalFreedomColors provides FreedomColors(p, dark)) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
