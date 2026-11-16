package com.orangeshield.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay

/**
 * ORANGE SHIELD — utilitários de movimento compartilhados.
 * Entradas em cascata, contadores animados e escala de toque usados
 * pelas telas para dar vida à interface sem alterar a lógica do motor.
 */

/** Entrada em cascata: fade + subida suave, com atraso crescente por posição. */
@Composable
fun Modifier.entrance(index: Int = 0): Modifier {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        visible = true
    }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        label = "entrance"
    )
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 40f
        scaleX = 0.965f + 0.035f * progress
        scaleY = 0.965f + 0.035f * progress
    }
}

/** Número que sobe suavemente até o valor real (estatísticas do Painel). */
@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit,
    color: Color = OrangePrimary
) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "counter"
    )
    androidx.compose.material3.Text(
        text = animated.toString(),
        modifier = modifier,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Indicador de risco com largura animada (0..1) para substituir barras estáticas. */
@Composable
fun rememberAnimatedFraction(target: Float): Float {
    val fraction by animateFloatAsState(
        targetValue = target.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "fraction"
    )
    return fraction
}
