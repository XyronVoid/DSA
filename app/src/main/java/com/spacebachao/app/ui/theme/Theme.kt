package com.spacebachao.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Apple-style colours (iOS system palette) + glass layers. */
@Immutable
data class ApplePalette(
    val dark: Boolean,
    val background: Color,
    val blobA: Color,
    val blobB: Color,
    val blobC: Color,
    val card: Color,
    val glass: Color,
    val glassStrong: Color,
    val gloss: Color,
    val rimTop: Color,
    val rimBottom: Color,
    val label: Color,
    val secondary: Color,
    val tertiary: Color,
    val separator: Color,
    val track: Color,
    val thumb: Color,
    val accent: Color,
    val accentLight: Color,
    val green: Color,
    val greenLight: Color,
    val red: Color,
    val orange: Color,
    val gold: Color,
    val goldDeep: Color,
)

val LightApple = ApplePalette(
    dark = false,
    background = Color(0xFFF2F2F7),
    blobA = Color(0x705AC8FA),
    blobB = Color(0x45AF52DE),
    blobC = Color(0x4034C759),
    card = Color.White,
    glass = Color(0xC7FFFFFF),
    glassStrong = Color(0xEBFFFFFF),
    gloss = Color(0x99FFFFFF),
    rimTop = Color(0xFFFFFFFF),
    rimBottom = Color(0x40FFFFFF),
    label = Color(0xFF000000),
    secondary = Color(0x993C3C43),
    tertiary = Color(0x4D3C3C43),
    separator = Color(0x293C3C43),
    track = Color(0x1F767680),
    thumb = Color.White,
    accent = Color(0xFF007AFF),
    accentLight = Color(0xFF5AC8FA),
    green = Color(0xFF34C759),
    greenLight = Color(0xFF8CE99A),
    red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500),
    gold = Color(0xFFFFCC00),
    goldDeep = Color(0xFFFF9500),
)

val DarkApple = ApplePalette(
    dark = true,
    background = Color(0xFF000000),
    blobA = Color(0x660A84FF),
    blobB = Color(0x4DBF5AF2),
    blobC = Color(0x3330D158),
    card = Color(0xFF1C1C1E),
    glass = Color(0x1FFFFFFF),
    glassStrong = Color(0x2EFFFFFF),
    gloss = Color(0x24FFFFFF),
    rimTop = Color(0x66FFFFFF),
    rimBottom = Color(0x0DFFFFFF),
    label = Color.White,
    secondary = Color(0x99EBEBF5),
    tertiary = Color(0x4DEBEBF5),
    separator = Color(0x40545458),
    track = Color(0x3D767680),
    thumb = Color(0xFF636366),
    accent = Color(0xFF0A84FF),
    accentLight = Color(0xFF64D2FF),
    green = Color(0xFF30D158),
    greenLight = Color(0xFF8CE99A),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
    gold = Color(0xFFFFD60A),
    goldDeep = Color(0xFFFF9F0A),
)

val LocalApple = staticCompositionLocalOf { LightApple }

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SpaceBachaoTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) DarkApple else LightApple
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = p.accent,
            onPrimary = Color.White,
            secondary = p.accent,
            background = p.background,
            onBackground = p.label,
            surface = p.background,
            onSurface = p.label,
            surfaceVariant = p.card,
            onSurfaceVariant = p.secondary,
            surfaceContainer = p.card,
            surfaceContainerHigh = p.card,
            surfaceContainerHighest = p.card,
            outline = p.separator,
            error = p.red,
        )
    } else {
        lightColorScheme(
            primary = p.accent,
            onPrimary = Color.White,
            secondary = p.accent,
            background = p.background,
            onBackground = p.label,
            surface = p.background,
            onSurface = p.label,
            surfaceVariant = p.card,
            onSurfaceVariant = p.secondary,
            surfaceContainer = p.card,
            surfaceContainerHigh = p.card,
            surfaceContainerHighest = p.card,
            outline = p.separator,
            error = p.red,
        )
    }
    CompositionLocalProvider(LocalApple provides p) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
