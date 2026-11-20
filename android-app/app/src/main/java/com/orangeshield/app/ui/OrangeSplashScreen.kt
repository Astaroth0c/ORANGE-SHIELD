package com.orangeshield.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun OrangeSplashScreen() {
    val transition = rememberInfiniteTransition(label = "orange-splash")
    val pulse = transition.animateFloat(0.88f, 1.08f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val glow = transition.animateFloat(0.22f, 0.72f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")
    Box(Modifier.background(Brush.radialGradient(listOf(Color(0xFF392013), Color(0xFF151014), DarkBackground))).then(Modifier), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(
                Modifier.size(148.dp).scale(pulse.value).background(Color(0xFF24140D).copy(alpha = glow.value), CircleShape)
                    .border(1.dp, OrangePrimary.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Shield, contentDescription = "ORANGE-SHIELD", tint = OrangePrimary, modifier = Modifier.size(88.dp))
            }
            Text("ORANGE", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Text("SHIELD", color = OrangePrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 7.sp)
            Text("PROTEÇÃO INTELIGENTE", color = Color(0xFFB7A49A), fontSize = 10.sp, letterSpacing = 2.sp)
        }
    }
}
