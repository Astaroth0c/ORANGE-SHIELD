package com.orangeshield.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orangeshield.app.EngineProvider
import com.orangeshield.app.engine.formatPhone

/** Observatório local: dados do Radar e decisões reais do serviço de triagem. */
@Composable
fun ObservatoryScreen() {
    val candidates by EngineProvider.database.historyCandidateDao()
        .observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val events by EngineProvider.database.callEventDao()
        .listRecent(500).collectAsStateWithLifecycle(initialValue = emptyList())

    val formatAnomalies = candidates.mapNotNull { candidate ->
        val flags = EngineProvider.spoofDetector.formatStepChecks(candidate.e164)
            .filter { it.status == com.orangeshield.app.engine.SpoofStatus.FLAGGED }
        if (flags.isEmpty()) null else candidate to flags
    }
    val importedCalls = candidates.sumOf { it.totalCalls }
    val importedBlocked = candidates.sumOf { it.blockedCalls }
    val triagedBlocked = events.count { it.decision == "BLOCK" }
    val warned = events.count { it.decision == "WARN" }
    val allowed = events.count { it.decision == "ALLOW" || it.decision == "SILENCE" }
    val topNumbers = candidates.sortedWith(
        compareByDescending<com.orangeshield.app.data.HistoryCandidateEntity> { it.totalCalls }
            .thenByDescending { it.lastSeenAt }
    ).take(8)
    val prefixGroups = candidates.groupBy { number ->
        val digits = number.e164.filter { it.isDigit() }
        when {
            digits.startsWith("55") && digits.length >= 4 -> "+${digits.take(4)}"
            digits.length >= 2 -> digits.take(2)
            else -> "Outros"
        }
    }.mapValues { it.value.sumOf { candidate -> candidate.totalCalls } }
        .toList().sortedByDescending { it.second }.take(6)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ORANGE OBSERVATORY", fontSize = 20.sp, color = OrangePrimary, fontWeight = FontWeight.Bold)
                Text("Inteligência local · fontes verificáveis do dispositivo", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("Chamadas no Radar", importedCalls.toString(), Modifier.weight(1f))
                MetricCard("Números mapeados", candidates.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("Bloqueios no histórico", importedBlocked.toString(), Modifier.weight(1f))
                MetricCard("Triagens do app", events.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            GlassCard {
                Text("RECORRÊNCIA · TOP NÚMEROS", fontSize = 12.sp, color = OrangeSecondary, fontWeight = FontWeight.Bold)
                if (topNumbers.isEmpty()) {
                    EmptyChartMessage("Faça a varredura no Radar. O gráfico será montado com o histórico real importado.")
                } else {
                    val max = topNumbers.maxOf { it.totalCalls }.coerceAtLeast(1)
                    topNumbers.forEach { candidate ->
                        DecisionBar(formatPhone(candidate.e164), candidate.totalCalls, max, OrangePrimary)
                    }
                }
            }
        }
        item {
            GlassCard {
                Text("ANOMALIAS ESTRUTURAIS · RADAR", fontSize = 12.sp, color = OrangeSecondary, fontWeight = FontWeight.Bold)
                MetricCard("Números com formato atípico", formatAnomalies.size.toString(), Modifier.fillMaxWidth())
                if (formatAnomalies.isEmpty()) {
                    EmptyChartMessage("Nenhuma anomalia estrutural foi identificada nos números importados.")
                } else {
                    formatAnomalies.take(8).forEach { (candidate, flags) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(formatPhone(candidate.e164), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(flags.joinToString(" · ") { it.label }, fontSize = 10.sp, color = AmberWarn)
                        }
                    }
                }
                Text("Esta seção mostra somente anomalias estruturais do Passo 1. Não confirma falsificação de caller ID nem substitui os demais passos do detector.",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            GlassCard {
                Text("CONCENTRAÇÃO POR PREFIXO", fontSize = 12.sp, color = OrangeSecondary, fontWeight = FontWeight.Bold)
                if (prefixGroups.isEmpty()) EmptyChartMessage("Sem volume suficiente para agrupar prefixos.")
                else prefixGroups.forEach { (prefix, count) ->
                    DecisionBar(prefix, count, prefixGroups.maxOf { it.second }.coerceAtLeast(1), OrangeSecondary)
                }
                Text("Prefixos são agrupamentos exploratórios. Não comprovam origem comum nem fraude.",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            GlassCard {
                Text("DECISÕES DO CALL GUARD", fontSize = 12.sp, color = OrangeSecondary, fontWeight = FontWeight.Bold)
                if (events.isEmpty()) {
                    EmptyChartMessage("Nenhuma chamada foi triada pelo ORANGE SHIELD ainda. Ative ROLE_CALL_SCREENING e aguarde uma chamada recebida.")
                } else {
                    DecisionBar("Bloqueadas pelo app", triagedBlocked, events.size, RedBlock)
                    DecisionBar("Alertadas pelo app", warned, events.size, AmberWarn)
                    DecisionBar("Permitidas / silêncio", allowed, events.size, EmeraldOk)
                    Text("Amostra: até 500 decisões mais recentes registradas pelo serviço de triagem.",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            GlassCard {
                Text("INTEGRIDADE E ORIGEM", fontSize = 12.sp, color = OrangeSecondary, fontWeight = FontWeight.Bold)
                Text(
                    "Histórico nativo: $importedCalls registros agregados em ${candidates.size} números. " +
                        "Triagens do app: ${events.size} decisões registradas separadamente. Os dois conjuntos não são misturados.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    OrangeGlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    OrangeGlassCard(
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, fontSize = 22.sp, color = OrangePrimary, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DecisionBar(label: String, value: Int, total: Int, color: Color) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text(value.toString(), fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
        }
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            drawRoundRect(color = trackColor, size = size, cornerRadius = CornerRadius(8f, 8f))
            val fraction = if (total <= 0) 0f else (value.toFloat() / total).coerceIn(0f, 1f)
            drawRoundRect(
                color = color.copy(alpha = 0.92f),
                topLeft = Offset.Zero,
                size = Size(size.width * fraction, size.height),
                cornerRadius = CornerRadius(8f, 8f)
            )
        }
    }
}

@Composable
private fun EmptyChartMessage(message: String) {
    Text(message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
