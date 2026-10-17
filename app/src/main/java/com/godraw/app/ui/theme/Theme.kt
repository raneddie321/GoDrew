package com.godraw.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.godraw.app.data.settings.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF5B8CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1F3A7A),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFB98CFF),
    onSecondary = Color(0xFF1E0B3D),
    secondaryContainer = Color(0xFF3B2468),
    onSecondaryContainer = Color(0xFFEBDDFF),
    tertiary = Color(0xFFFF9A4D),
    onTertiary = Color(0xFF331400),
    background = GoDrawColors.Charcoal,
    onBackground = Color(0xFFE6E8EE),
    surface = GoDrawColors.CharcoalSurface,
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = GoDrawColors.CharcoalHigh,
    onSurfaceVariant = Color(0xFFB5BAC6),
    surfaceContainer = GoDrawColors.CharcoalSurface,
    surfaceContainerHigh = GoDrawColors.CharcoalHigh,
    surfaceContainerHighest = Color(0xFF2E3341),
    surfaceContainerLow = Color(0xFF171920),
    outline = GoDrawColors.CharcoalOutline,
    outlineVariant = Color(0xFF2C303B),
    error = Color(0xFFFF6B6B),
)

private val LightScheme = lightColorScheme(
    primary = GoDrawColors.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF001A4D),
    secondary = GoDrawColors.Purple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBDDFF),
    onSecondaryContainer = Color(0xFF240A4F),
    tertiary = Color(0xFFE56A00),
    background = GoDrawColors.Paper,
    onBackground = Color(0xFF1A1C22),
    surface = Color.White,
    onSurface = Color(0xFF1A1C22),
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF555B66),
    surfaceContainer = Color(0xFFF1F2F5),
    surfaceContainerHigh = Color(0xFFE9EBEF),
    surfaceContainerHighest = Color(0xFFE2E4E9),
    surfaceContainerLow = Color(0xFFF8F9FB),
    outline = Color(0xFFC4C8D0),
    outlineVariant = Color(0xFFDDE0E6),
)

private val ColorfulScheme = darkColorScheme(
    primary = Color(0xFFFF4FA3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF6B1747),
    onPrimaryContainer = Color(0xFFFFD9EA),
    secondary = Color(0xFF00C6FF),
    onSecondary = Color(0xFF00222E),
    secondaryContainer = Color(0xFF00546B),
    onSecondaryContainer = Color(0xFFC9F1FF),
    tertiary = GoDrawColors.Gold,
    onTertiary = Color(0xFF2E2200),
    background = GoDrawColors.Violet,
    onBackground = Color(0xFFF3EAFF),
    surface = GoDrawColors.VioletSurface,
    onSurface = Color(0xFFF3EAFF),
    surfaceVariant = GoDrawColors.VioletHigh,
    onSurfaceVariant = Color(0xFFD4C3F0),
    surfaceContainer = GoDrawColors.VioletSurface,
    surfaceContainerHigh = GoDrawColors.VioletHigh,
    surfaceContainerHighest = Color(0xFF3E2C6B),
    surfaceContainerLow = Color(0xFF1F1438),
    outline = Color(0xFF5B4790),
    outlineVariant = Color(0xFF3A2A62),
)

/** Extra theme values beyond Material's color scheme. */
data class GoDrawThemeExtras(
    val mode: ThemeMode,
    val headerBrush: Brush,
    val canvasBackdrop: Color,
    val isDark: Boolean,
)

val LocalGoDrawExtras = staticCompositionLocalOf {
    GoDrawThemeExtras(ThemeMode.DARK, GoDrawColors.brandBrush, Color(0xFF0D0F14), true)
}

fun colorSchemeFor(mode: ThemeMode, accent: Color?): ColorScheme {
    val base = when (mode) {
        ThemeMode.DARK -> DarkScheme
        ThemeMode.LIGHT -> LightScheme
        ThemeMode.COLORFUL -> ColorfulScheme
    }
    return if (accent != null) base.copy(primary = accent) else base
}

@Composable
fun GoDrawTheme(mode: ThemeMode, accent: Color? = null, content: @Composable () -> Unit) {
    val extras = when (mode) {
        ThemeMode.DARK -> GoDrawThemeExtras(
            mode,
            Brush.linearGradient(listOf(Color(0xFF1B2747), Color(0xFF2A1B47))),
            Color(0xFF0D0F14),
            true,
        )
        ThemeMode.LIGHT -> GoDrawThemeExtras(
            mode,
            Brush.linearGradient(listOf(Color(0xFFE6EEFF), Color(0xFFF3E8FF))),
            Color(0xFFDADDE3),
            false,
        )
        ThemeMode.COLORFUL -> GoDrawThemeExtras(
            mode,
            Brush.linearGradient(listOf(GoDrawColors.Purple, GoDrawColors.Magenta, GoDrawColors.Orange)),
            Color(0xFF120A22),
            true,
        )
    }
    CompositionLocalProvider(LocalGoDrawExtras provides extras) {
        MaterialTheme(colorScheme = colorSchemeFor(mode, accent), typography = GoDrawTypography, content = content)
    }
}
