package com.swara.play.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SwaraOledColorScheme = darkColorScheme(
    primary = CinemaCrimson,
    onPrimary = TextPrimary,
    secondary = CinemaCrimson,
    onSecondary = TextPrimary,
    background = OledBlack,
    onBackground = TextPrimary,
    surface = OledSurface,
    onSurface = TextPrimary,
    surfaceVariant = OledSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = OledBorder
)

@Composable
fun SwaraPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SwaraOledColorScheme,
        typography = SwaraPlayTypography,
        content = content
    )
}
