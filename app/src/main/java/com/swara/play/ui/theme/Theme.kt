package com.swara.play.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.swara.play.model.AppThemeId

fun paletteForTheme(themeId: AppThemeId): SwaraPalette = when (themeId) {
    AppThemeId.DEFAULT -> SwaraPalette(
        bg = Color(0xFF070B08),
        surface = Color(0xFF121814),
        surfaceElevated = Color(0xFF1B241E),
        glass = Color(0xE0101612),
        border = Color(0x24FFFFFF),
        brandAccent = Color(0xFF22C55E),
        glowPrimary = Color(0x4222C55E),
        glowSecondary = Color(0x2810B981)
    )
    AppThemeId.OLED -> SwaraPalette(
        bg = Color(0xFF000000),
        surface = Color(0xFF101012),
        surfaceElevated = Color(0xFF1A1A1F),
        glass = Color(0xE60E0E10),
        border = Color(0x22FFFFFF),
        brandAccent = Color(0xFF4ADE80),
        glowPrimary = Color(0x18FFFFFF),
        glowSecondary = Color(0x0DFFFFFF)
    )
    AppThemeId.MIDNIGHT -> SwaraPalette(
        bg = Color(0xFF060A14),
        surface = Color(0xFF0F1628),
        surfaceElevated = Color(0xFF17223B),
        glass = Color(0xE00C1222),
        border = Color(0x24FFFFFF),
        brandAccent = Color(0xFF38BDF8),
        glowPrimary = Color(0x3D38BDF8),
        glowSecondary = Color(0x2A2563EB)
    )
    AppThemeId.AMETHYST -> SwaraPalette(
        bg = Color(0xFF0C0714),
        surface = Color(0xFF181028),
        surfaceElevated = Color(0xFF24183B),
        glass = Color(0xE0140C22),
        border = Color(0x24FFFFFF),
        brandAccent = Color(0xFFC084FC),
        glowPrimary = Color(0x3DC084FC),
        glowSecondary = Color(0x2A8B5CF6)
    )
    AppThemeId.EMERALD -> SwaraPalette(
        bg = Color(0xFF05110C),
        surface = Color(0xFF0C2017),
        surfaceElevated = Color(0xFF133023),
        glass = Color(0xE00A1B14),
        border = Color(0x24FFFFFF),
        brandAccent = Color(0xFF34D399),
        glowPrimary = Color(0x4734D399),
        glowSecondary = Color(0x2E10B981)
    )
}

val LocalSwaraPalette = staticCompositionLocalOf { paletteForTheme(AppThemeId.DEFAULT) }

@Composable
fun SwaraPlayTheme(
    themeId: AppThemeId = AppThemeId.DEFAULT,
    content: @Composable () -> Unit
) {
    val pal = paletteForTheme(themeId)
    val scheme = darkColorScheme(
        primary = Color.White,
        onPrimary = Color.Black,
        primaryContainer = pal.surfaceElevated,
        onPrimaryContainer = Color.White,
        secondary = pal.brandAccent,
        onSecondary = Color.Black,
        background = pal.bg,
        onBackground = TextPrimary,
        surface = pal.surface,
        onSurface = TextPrimary,
        surfaceVariant = pal.surfaceElevated,
        onSurfaceVariant = TextSecondary,
        outline = pal.border
    )
    CompositionLocalProvider(LocalSwaraPalette provides pal) {
        MaterialTheme(colorScheme = scheme, typography = SwaraPlayTypography, content = content)
    }
}
