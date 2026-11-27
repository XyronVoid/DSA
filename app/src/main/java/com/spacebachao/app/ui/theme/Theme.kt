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

/** Brand palette taken from the Space Bachao logo: deep navy, cyan → violet gradient, soft glows. */
@Immutable
data class Palette(
    val background: Color,
    val glowTeal: Color,
    val glowPurple: Color,
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
    val gradA: Color,
    val gradB: Color,
    val gradC: Color,
    val logoA: Color,
    val logoB: Color,
    val logoC: Color,
    val gold: Color,
    val goldDeep: Color,
    val onGold: Color,
    val green: Color,
    val greenLight: Color,
    val red: Color,
    val orange: Color,
) {
    /** Cyan → indigo → violet, like the logo and the "Download" button. */
    val brand: List<Color> get() = listOf(gradA, gradB, gradC)
    val logo: List<Color> get() = listOf(logoA, logoB, logoC)
}

val Brand = Palette(
    background = Color(0xFF0B0B1A),
    glowTeal = Color(0xFF0B3A4A),
    glowPurple = Color(0xFF3A1F66),
    card = Color(0xFF12142A),
    card2 = Color(0xFF181B35),
    border = Color(0xFF262A4A),
    label = Color(0xFFF4F6FF),
    secondary = Color(0xFF9399BB),
    tertiary = Color(0xFF5C6285),
    separator = Color(0xFF22264A),
    track = Color(0xFF1E2240),
    thumb = Color(0xFFF4F6FF),
    onThumb = Color.White,
    button = Color(0xFF27CCEF),
    onButton = Color.White,
    accent = Color(0xFF4FD8F5),
    accentLight = Color(0xFF8DE7FA),
    gradA = Color(0xFF27CCEF),
    gradB = Color(0xFF6A7DF4),
    gradC = Color(0xFFA256F6),
    logoA = Color(0xFF6ACDF7),
    logoB = Color(0xFF7B86F5),
    logoC = Color(0xFF9C58F4),
    gold = Color(0xFFF3D28B),
    goldDeep = Color(0xFFD9A84E),
    onGold = Color(0xFF1C1405),
    green = Color(0xFF34D399),
    greenLight = Color(0xFF6EE7B7),
    red = Color(0xFFFF7A7A),
    orange = Color(0xFFFBBF24),
)

val LocalPalette = staticCompositionLocalOf { Brand }

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
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun SpaceBachaoTheme(content: @Composable () -> Unit) {
    val p = Brand
    val scheme = darkColorScheme(
        primary = p.gradB,
        onPrimary = Color.White,
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
