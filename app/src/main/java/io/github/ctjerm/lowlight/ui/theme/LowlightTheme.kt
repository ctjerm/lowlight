package io.github.ctjerm.lowlight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LowlightPage = Color(0xFF090B0E)
val LowlightSurface = Color(0xFF13161B)
val LowlightSurfaceRaised = Color(0xFF191C22)
val LowlightAccent = Color(0xFFFFCF7A)
val LowlightOnAccent = Color(0xFF3C2800)
val LowlightOutline = Color(0xFF4E4A56)
val LowlightNightTrack = Color(0xFF565066)
val LowlightTextPrimary = Color(0xFFF5F0E8)
val LowlightTextSecondary = Color(0xFFC9C1B7)

private val LowlightColorScheme = darkColorScheme(
    primary = LowlightAccent,
    onPrimary = LowlightOnAccent,
    primaryContainer = Color(0xFF594100),
    onPrimaryContainer = Color(0xFFFFDEA6),
    background = LowlightPage,
    onBackground = LowlightTextPrimary,
    surface = LowlightSurface,
    onSurface = LowlightTextPrimary,
    surfaceVariant = LowlightSurfaceRaised,
    onSurfaceVariant = LowlightTextSecondary,
    outline = LowlightOutline,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LowlightTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 64.sp,
        lineHeight = 68.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1.5).sp,
        fontFeatureSettings = "tnum",
    ),
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 27.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 21.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
)

@Composable
fun LowlightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LowlightColorScheme,
        typography = LowlightTypography,
        content = content,
    )
}
