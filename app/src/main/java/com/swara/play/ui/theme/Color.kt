package com.swara.play.ui.theme

import androidx.compose.ui.graphics.Color

val SwaraBlack = Color(0xFF070B08)
val SwaraCard = Color(0xFF121814)
val SwaraCardElevated = Color(0xFF1A221D)
val SwaraGlass = Color(0xE0101612)
val SwaraSubtleBorder = Color(0x24FFFFFF)

val SwaraAccent = Color(0xFFFFFFFF)
val SwaraAccentSoft = Color(0x22FFFFFF)
val SwaraStableGreen = Color(0xFF22C55E)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA1A8A3)
val StarGold = Color(0xFFF5C518)

data class SwaraPalette(
    val bg: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val glass: Color,
    val border: Color,
    val brandAccent: Color,
    val glowPrimary: Color,
    val glowSecondary: Color
)
