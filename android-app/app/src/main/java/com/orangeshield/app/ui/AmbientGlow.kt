package com.orangeshield.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * ORANGE SHIELD — fundo ambiente animado.
 * Três halos laranja de baixa intensidade derivam lentamente atrás do
 * conteúdo, dando profundidade aos cartões de vidro (glassmorphism)
 * sem competir com a leitura dos dados.
 */
@Composable
fun AmbientGlow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 24000, easing = LinearEasing)),
        label = "ambientPhase"
    )
    val angle = t * 2f * Math.PI.toFloat()

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        fun halo(centerX: Float, centerY: Float, radius: Float, color: Color) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = radius
                ),
                radius = radius,
                center = Offset(centerX, centerY)
            )
        }

        halo(
            centerX = w * (0.18f + 0.10f * sin(angle)),
            centerY = h * 0.06f + 24f * cos(angle),
            radius = w * 0.75f,
            color = OrangePrimary.copy(alpha = 0.10f)
        )
        halo(
            centerX = w * (0.92f - 0.08f * cos(angle)),
            centerY = h * 0.38f + 40f * sin(angle),
            radius = w * 0.62f,
            color = Color(0xFF7C2D12).copy(alpha = 0.16f)
        )
        halo(
            centerX = w * (0.45f + 0.12f * sin(angle + 1.9f)),
            centerY = h * (1.02f + 0.04f * sin(angle + 0.7f)),
            radius = w * 0.85f,
            color = OrangeSecondary.copy(alpha = 0.06f)
        )
    }
}
