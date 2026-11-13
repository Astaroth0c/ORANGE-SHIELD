package com.orangeshield.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orangeshield.app.EngineProvider

/**
 * Observatório local: visualizações calculadas exclusivamente a partir de
 * chamadas realmente triadas e persistidas no Room. Nenhum evento sintético.
 */
@Composable
fun ObservatoryScreen() {
    val events by EngineProvider.database.callEventDao()
        .listRecent(500).collectAsStateWithLifecycle(initialValue = emptyList())

    val blocked = events.count { it.decision == "BLOCK" }
    val warned = events.count { it.decision == "WARN" }
    val allowed = events.count { it.decision == "ALLOW" || it.decision == "SILENCE" }
    val prefixGroups = events.groupBy { it.e164.take(6) }
        .filterKeys { it.length >= 6 }
        .mapValues { it.value.size }
        .filterValues { it >= 2 }
        .toList()
        .sortedByDescending { it.second }
        .take(6)
    val hourly = (5 downTo 0).map { offset ->
        val start = System.currentTimeMillis() - (offset + 1) * 60L * 60L * 1000L
        val end = start + 60L * 60L * 1000L
        events.count { it.occurredAt in start until end }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ORANGE OBSERVATORY", fontSize = 19.sp, color = OrangePrimary)
                Text(
                    "Telemetria local · somente eventos reais do aparelho",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("Eventos", events.size.toString(), Modifier.weight(1f))
                MetricCard("Bloqueados", blocked.toString(), Modifier.weight(1f))
                MetricCard("Alertas", warned.toString(), Modifier.weight(1f))
            }
        }
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Distribuição das decisões", fontSize = 14.sp)
                    DecisionBar("Bloqueadas", blocked, events.size, RedBlock)
                    DecisionBar("Suspeitas", warned, events.size, AmberWarn)
                    DecisionBar("Permitidas / silêncio", allowed, events.size, EmeraldOk)
                    Text(
                        "Amostra: até 500 chamadas mais recentes. Os números refletem o banco local.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Atividade por hora", fontSize = 14.sp)
                    if (events.isEmpty()) EmptyChartMessage()
                    else {
                        Canvas(Modifier.fillMaxWidth().height(112.dp)) {
                            val max = (hourly.maxOrNull() ?: 0).coerceAtLeast(1)
                            val slot = size.width / 6f
                            hourly.forEachIndexed { index, count ->
                                val barHeight = size.height * (count.toFloat() / max)
                                drawRoundRect(
                                    color = OrangePrimary.copy(alpha = 0.85f),
                                    topLeft = Offset(index * slot + slot * 0.18f, size.height - barHeight),
                                    size = Size(slot * 0.64f, barHeight),
                                    cornerRadius = CornerRadius(8f, 8f)
                                )
                            }
                        }
                        Text("6 janelas consecutivas de 1 hora · mais recente à direita", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Concentração por prefixo", fontSize = 14.sp)
                    if (prefixGroups.isEmpty()) EmptyChartMessage()
                    else prefixGroups.forEach { (prefix, count) ->
                        DecisionBar(prefix, count, prefixGroups.maxOf { it.second }, OrangeSecondary)
                    }
                    Text(
                        "Agrupamento exploratório por prefixo apresentado, não prova de origem comum ou fraude.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Integridade dos dados", fontSize = 14.sp)
                    Text("Nenhuma chamada ou campanha é inventada. O observatório permanece vazio até existirem eventos reais.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, fontSize = 21.sp, color = OrangePrimary)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DecisionBar(label: String, value: Int, total: Int, color: Color) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = 11.sp)
            Text(value.toString(), fontSize = 11.sp, color = color)
        }
        Canvas(Modifier.fillMaxWidth().height(9.dp)) {
            drawRoundRect(
                color = trackColor,
                size = size,
                cornerRadius = CornerRadius(8f, 8f)
            )
            val fraction = if (total <= 0) 0f else (value.toFloat() / total).coerceIn(0f, 1f)
            drawRoundRect(
                color = color,
                size = Size(size.width * fraction, size.height),
                cornerRadius = CornerRadius(8f, 8f)
            )
        }
    }
}

@Composable
private fun EmptyChartMessage() {
    Text("Aguardando eventos reais para montar este gráfico.", fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
