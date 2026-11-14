package com.orangeshield.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orangeshield.app.data.HistoryCandidateEntity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val TraceCyan = Color(0xFF42D9F5)
private val TraceOrange = Color(0xFFFF8738)
private val TraceMuted = Color(0xFF8293A5)

@Composable
fun ForensicNetworkGraph(
    candidates: List<HistoryCandidateEntity>,
    onAnalyzeNumber: (String) -> Unit
) {
    val nodes = remember(candidates) { candidates.sortedByDescending { it.totalCalls }.take(12) }
    var selectedIndex by remember(nodes) { mutableIntStateOf(0) }
    val selected = nodes.getOrNull(selectedIndex)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ORANGE TRACE", color = TraceCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("Mapa de relações", color = LightOnDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text("${nodes.size} nós", color = TraceMuted, fontSize = 11.sp)
        }
        if (nodes.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
                Text("Importe o histórico de chamadas para construir o grafo real.", color = TraceMuted, fontSize = 12.sp)
            }
        } else {
            Canvas(
                Modifier.fillMaxWidth().height(250.dp)
                    .pointerInput(nodes) {
                        detectTapGestures { tap ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val center = Offset(w / 2f, h / 2f)
                            val radius = minOf(w * .39f, h * .39f)
                            val positions = nodes.indices.map { i ->
                                val angle = -PI / 2.0 + (2.0 * PI * i / nodes.size)
                                Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius)
                            }
                            val hit = positions.indices.minByOrNull { i ->
                                val dx = positions[i].x - tap.x
                                val dy = positions[i].y - tap.y
                                dx * dx + dy * dy
                            }
                            if (hit != null) {
                                val dx = positions[hit].x - tap.x
                                val dy = positions[hit].y - tap.y
                                if (dx * dx + dy * dy < 58f * 58f) selectedIndex = hit
                            }
                        }
                    }
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = minOf(size.width * .39f, size.height * .39f)
                val positions = nodes.indices.map { i ->
                    val angle = -PI / 2.0 + (2.0 * PI * i / nodes.size)
                    Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius)
                }
                // Conexões são agrupamentos observáveis por DDD, não prova de identidade comum.
                for (i in nodes.indices) {
                    for (j in i + 1 until nodes.size) {
                        val a = nodes[i].e164.filter { it.isDigit() }
                        val b = nodes[j].e164.filter { it.isDigit() }
                        val dddA = if (a.length >= 4) a.substring(2, 4) else ""
                        val dddB = if (b.length >= 4) b.substring(2, 4) else ""
                        if (dddA.isNotBlank() && dddA == dddB) {
                            drawLine(TraceCyan.copy(alpha = .22f), positions[i], positions[j], 5.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(TraceCyan.copy(alpha = .72f), positions[i], positions[j], 1.dp.toPx(), cap = StrokeCap.Round)
                        }
                    }
                }
                drawCircle(TraceOrange.copy(alpha = .08f), radius * .42f, center)
                drawCircle(TraceCyan.copy(alpha = .18f), radius * .42f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                drawCircle(TraceOrange, 5.dp.toPx(), center)
                positions.forEachIndexed { index, point ->
                    val active = index == selectedIndex
                    val nodeRadius = if (active) 13.dp.toPx() else (8.dp.toPx() + nodes[index].totalCalls.coerceAtMost(12) * .45f.dp.toPx())
                    if (active) drawCircle(TraceOrange.copy(alpha = .22f), nodeRadius * 2.2f, point)
                    drawCircle(if (active) TraceOrange else TraceCyan, nodeRadius, point)
                    drawCircle(Color(0xFF111820), nodeRadius * .55f, point)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(TraceCyan, "Número importado")
                LegendDot(TraceOrange, "Selecionado")
                LegendDot(Color(0xFF607D8B), "Mesmo DDD")
            }
            selected?.let { item ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .background(Color.White.copy(alpha = .055f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.e164, color = LightOnDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("${item.totalCalls} chamadas · ${item.missedCalls} perdidas · ${item.blockedCalls} marcadas bloqueadas",
                            color = TraceMuted, fontSize = 10.sp)
                        Text(item.suggestedLabel, color = TraceOrange, fontSize = 10.sp)
                    }
                    androidx.compose.material3.TextButton(onClick = { onAnalyzeNumber(item.e164) }) {
                        Text("INVESTIGAR", color = TraceCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text(
            "As linhas agrupam números pelo mesmo DDD. Isso é uma relação geográfica do plano de numeração, não evidência de que os números pertencem à mesma pessoa ou campanha.",
            color = TraceMuted, fontSize = 10.sp, lineHeight = 14.sp
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, color = TraceMuted, fontSize = 9.sp)
    }
}

@Composable
fun ForensicFrequencyChart(candidates: List<HistoryCandidateEntity>) {
    val ranked = remember(candidates) { candidates.sortedByDescending { it.totalCalls }.take(6) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("FREQUÊNCIA POR NÚMERO", color = TraceCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text("Volume real do histórico importado", color = LightOnDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (ranked.isEmpty()) {
            Text("Sem registros importados para calcular frequências.", color = TraceMuted, fontSize = 11.sp)
        } else {
            val maxValue = ranked.maxOf { it.totalCalls }.coerceAtLeast(1)
            ranked.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("N${index + 1}", color = TraceMuted, fontSize = 9.sp, modifier = Modifier.size(width = 24.dp, height = 16.dp))
                    Canvas(Modifier.weight(1f).height(13.dp)) {
                        val fraction = item.totalCalls.toFloat() / maxValue
                        drawRoundRect(
                            color = Color.White.copy(alpha = .06f),
                            size = androidx.compose.ui.geometry.Size(size.width, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                        )
                        drawRoundRect(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(TraceCyan, TraceOrange)),
                            size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                        )
                    }
                    Text(item.totalCalls.toString(), color = LightOnDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
