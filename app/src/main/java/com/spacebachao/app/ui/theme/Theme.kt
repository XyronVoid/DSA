package com.spacebachao.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SuccessGreen = Color(0xFF22C55E)
val WarnAmber = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFEF4444)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF00201C),
    primaryContainer = Color(0xFF0F3B36),
    onPrimaryContainer = Color(0xFFB4F4EA),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF261900),
    background = Color(0xFF0B1116),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0B1116),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF17212A),
    onSurfaceVariant = Color(0xFFA8B5C2),
    surfaceContainer = Color(0xFF121B22),
    surfaceContainerHigh = Color(0xFF18232C),
    outline = Color(0xFF3A4752),
    outlineVariant = Color(0xFF26323C),
    error = ErrorRed,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E9F8E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F7F1),
    onPrimaryContainer = Color(0xFF00332D),
    secondary = Color(0xFFB45309),
    onSecondary = Color.White,
    background = Color(0xFFF6F8F9),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF6F8F9),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE8EDF0),
    onSurfaceVariant = Color(0xFF52606D),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F3F5),
    outline = Color(0xFFC3CCD3),
    outlineVariant = Color(0xFFDDE3E8),
    error = ErrorRed,
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    )
}

@Composable
fun SpaceBachaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
