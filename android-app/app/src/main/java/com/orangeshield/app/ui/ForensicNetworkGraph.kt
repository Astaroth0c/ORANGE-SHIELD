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
import com.orangeshield.app.data.CallEventEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val TraceCyan = Color(0xFF42D9F5)
private val TraceOrange = Color(0xFFFF8738)
private val TraceMuted = Color(0xFF8293A5)

@Composable
fun ForensicNetworkGraph(
    candidates: List<HistoryCandidateEntity>,
    events: List<CallEventEntity> = emptyList(),
    onAnalyzeNumber: (String) -> Unit
) {
    val eventCounts = remember(events) { events.groupingBy { it.e164 }.eachCount() }
    val nodes = remember(candidates, eventCounts) {
        candidates.sortedWith(compareByDescending<HistoryCandidateEntity> { eventCounts[it.e164] ?: 0 }
            .thenByDescending { it.totalCalls }).take(12)
    }
    var selectedIndex by remember(nodes) { mutableIntStateOf(0) }
    val selected = nodes.getOrNull(selectedIndex)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ORANGE TRACE", color = TraceCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("Números observados", color = LightOnDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                // Cada raio liga um número ao núcleo de registros locais.
                // Não desenhamos ligações entre números sem evidência relacional real.
                positions.forEachIndexed { index, point ->
                    val weight = (eventCounts[nodes[index].e164] ?: 0).coerceAtMost(12)
                    val alpha = if (weight > 0) .22f + weight * .035f else .12f
                    drawLine(TraceCyan.copy(alpha = alpha), center, point, (1f + weight * .12f).dp.toPx(), cap = StrokeCap.Round)
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
                LegendDot(Color(0xFF607D8B), "Registro local")
            }
            selected?.let { item ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .background(Color.White.copy(alpha = .055f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.e164, color = LightOnDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("${item.totalCalls} no histórico · ${eventCounts[item.e164] ?: 0} triagens do app",
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
            "Os raios indicam apenas que o número possui registro local. A intensidade considera triagens do ORANGE-SHIELD; não representa vínculo entre números.",
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


/**
 * Linha do tempo das triagens efetivamente gravadas pelo motor local.
 * Cada coluna representa uma janela de duas horas nas últimas 24 horas.
 */
@Composable
fun ForensicActivityTimeline(events: List<CallEventEntity>) {
    val now = System.currentTimeMillis()
    val window = 24L * 60L * 60L * 1000L
    val bucketMillis = window / 12L
    val counts = remember(events) {
        val end = System.currentTimeMillis()
        val start = end - window
        val values = MutableList(12) { 0 }
        events.forEach { event ->
            if (event.occurredAt in start..end) {
                val index = ((event.occurredAt - start) / bucketMillis).toInt().coerceIn(0, 11)
                values[index]++
            }
        }
        values
    }
    var selectedBucket by remember(counts) { mutableIntStateOf(counts.indices.maxByOrNull { counts[it] } ?: 11) }
    val maxCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    val start = now - window
    val formatter = remember { SimpleDateFormat("HH:mm", Locale("pt", "BR")) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ACTIVITY TIMELINE", color = TraceCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Text("Triagens nas últimas 24 horas", color = LightOnDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("até 500 triagens", color = TraceMuted, fontSize = 10.sp)
        }
        if (events.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                Text("Ainda não existem triagens registradas pelo ORANGE-SHIELD.", color = TraceMuted, fontSize = 11.sp)
            }
        } else {
            Canvas(
                Modifier.fillMaxWidth().height(112.dp)
                    .pointerInput(counts) {
                        detectTapGestures { tap ->
                            selectedBucket = (tap.x / size.width * 12).toInt().coerceIn(0, 11)
                        }
                    }
            ) {
                val gap = 5.dp.toPx()
                val barWidth = (size.width - gap * 11) / 12f
                val baseline = size.height - 4.dp.toPx()
                val availableHeight = size.height - 12.dp.toPx()
                counts.forEachIndexed { index, count ->
                    val fraction = count.toFloat() / maxCount
                    val height = if (count == 0) 2.dp.toPx() else (availableHeight * fraction).coerceAtLeast(4.dp.toPx())
                    val left = index * (barWidth + gap)
                    drawRoundRect(
                        color = Color.White.copy(alpha = .055f),
                        topLeft = Offset(left, 0f),
                        size = androidx.compose.ui.geometry.Size(barWidth, baseline),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    )
                    drawRoundRect(
                        color = if (index == selectedBucket) TraceOrange else TraceCyan.copy(alpha = if (count == 0) .18f else .78f),
                        topLeft = Offset(left, baseline - height),
                        size = androidx.compose.ui.geometry.Size(barWidth, height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    )
                }
            }
            val bucketStart = start + selectedBucket * bucketMillis
            val bucketEnd = bucketStart + bucketMillis
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${formatter.format(Date(bucketStart))}–${formatter.format(Date(bucketEnd))}",
                    color = TraceMuted, fontSize = 10.sp
                )
                Text(
                    "${counts[selectedBucket]} triagens",
                    color = TraceOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(formatter.format(Date(start)), color = TraceMuted, fontSize = 9.sp)
                Text("Toque em uma coluna para inspecionar o período", color = TraceMuted, fontSize = 9.sp)
                Text(formatter.format(Date(now)), color = TraceMuted, fontSize = 9.sp)
            }
        }
    }
}
