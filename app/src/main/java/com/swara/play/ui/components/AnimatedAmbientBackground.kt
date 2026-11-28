package com.swara.play.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.swara.play.ui.theme.LocalSwaraPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated ambient mesh / floating aurora orbs for all non-Home tabs (1:1 with Web Cinejoy).
 * Automatically adapts to the active SwaraPlay color theme while remaining lightweight on GPU.
 */
@Composable
fun AnimatedAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val pal = LocalSwaraPalette.current
    val transition = rememberInfiniteTransition(label = "ambientMesh")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbPhase"
    )

    Box(modifier = modifier.fillMaxSize().background(pal.bg)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val c1 = Offset(
                x = w * (0.24f + 0.14f * cos(phase)),
                y = h * (0.22f + 0.10f * sin(phase))
            )
            val c2 = Offset(
                x = w * (0.78f + 0.14f * sin(phase * 0.8f)),
                y = h * (0.74f + 0.12f * cos(phase * 0.8f))
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(pal.glowPrimary.copy(alpha = 0.34f), Color.Transparent),
                    center = c1,
                    radius = w.coerceAtLeast(h) * 0.62f
                ),
                center = c1,
                radius = w.coerceAtLeast(h) * 0.62f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(pal.brandAccent.copy(alpha = 0.22f), Color.Transparent),
                    center = c2,
                    radius = w.coerceAtLeast(h) * 0.56f
                ),
                center = c2,
                radius = w.coerceAtLeast(h) * 0.56f
            )
        }
        content()
    }
}
