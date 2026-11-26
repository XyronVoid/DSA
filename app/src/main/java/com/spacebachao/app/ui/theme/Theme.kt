package com.spacebachao.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Dark premium palette: matte black, off-white buttons, gold highlights. Always dark. */
@Immutable
data class Palette(
    val background: Color,
    val card: Color,
    val card2: Color,
    val border: Color,
    val label: Color,
    val secondary: Color,
    val tertiary: Color,
    val separator: Color,
    val track: Color,
    val thumb: Color,
    val onThumb: Color,
    val button: Color,
    val onButton: Color,
    val accent: Color,
    val accentLight: Color,
    val gold: Color,
    val goldDeep: Color,
    val green: Color,
    val greenLight: Color,
    val red: Color,
    val orange: Color,
)

val Premium = Palette(
    background = Color(0xFF0D0D0D),
    card = Color(0xFF161616),
    card2 = Color(0xFF1E1E1E),
    border = Color(0xFF262626),
    label = Color(0xFFF2F2F2),
    secondary = Color(0xFF8A8A8A),
    tertiary = Color(0xFF5A5A5A),
    separator = Color(0xFF242424),
    track = Color(0xFF242424),
    thumb = Color(0xFFF2F2F2),
    onThumb = Color(0xFF0D0D0D),
    button = Color(0xFFF2F2F2),
    onButton = Color(0xFF0D0D0D),
    accent = Color(0xFFC9A96E),
    accentLight = Color(0xFFE6CD9C),
    gold = Color(0xFFE6CD9C),
    goldDeep = Color(0xFFC9A96E),
    green = Color(0xFF6FD39A),
    greenLight = Color(0xFF9BE3BA),
    red = Color(0xFFFF6B6B),
    orange = Color(0xFFF5A524),
)

val LocalPalette = staticCompositionLocalOf { Premium }

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.2).sp),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SpaceBachaoTheme(content: @Composable () -> Unit) {
    val p = Premium
    val scheme = darkColorScheme(
        primary = p.button,
        onPrimary = p.onButton,
        secondary = p.accent,
        background = p.background,
        onBackground = p.label,
        surface = p.background,
        onSurface = p.label,
        surfaceVariant = p.card,
        onSurfaceVariant = p.secondary,
        surfaceContainer = p.card,
        surfaceContainerHigh = p.card2,
        surfaceContainerHighest = p.card2,
        outline = p.border,
        error = p.red,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
