package com.swara.play.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SwaraMonochromeColorScheme = darkColorScheme(
    primary = SwaraAccent,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2C2C32),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFB0B0B8),
    onSecondary = Color.Black,
    background = SwaraBlack,
    onBackground = TextPrimary,
    surface = SwaraCard,
    onSurface = TextPrimary,
    surfaceVariant = SwaraCardElevated,
    onSurfaceVariant = TextSecondary,
    outline = SwaraSubtleBorder
)

@Composable
fun SwaraPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SwaraMonochromeColorScheme,
        typography = SwaraPlayTypography,
        content = content
    )
}
