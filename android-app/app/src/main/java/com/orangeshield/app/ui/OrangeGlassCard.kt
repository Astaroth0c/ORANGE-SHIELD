package com.orangeshield.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Superfície compartilhada do ORANGE SHIELD: grafite translúcido,
 * reflexo quente discreto, contorno fino e transição suave de conteúdo.
 */
@Composable
fun OrangeGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .animateContentSize()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF25252A).copy(alpha = 0.76f),
                        Color(0xFF171719).copy(alpha = 0.84f),
                        Color(0xFF24170F).copy(alpha = 0.72f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.20f),
                        OrangePrimary.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.04f)
                    )
                ),
                shape = shape
            )
    ) {
        Column(
            modifier = Modifier.padding(1.dp),
            content = content
        )
    }
}
