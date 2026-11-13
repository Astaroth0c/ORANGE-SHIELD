package com.orangeshield.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orangeshield.app.EngineProvider
import com.orangeshield.app.history.CallHistoryScanner
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun IntelligenceScreen(onAnalyzeNumber: (String) -> Unit = {}) {
    val context = LocalContext.current
    val dao = EngineProvider.database.historyCandidateDao()
    val candidates by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(0) }
    val pageSize = 20
    val filteredCandidates = remember(candidates, search) {
        if (search.isBlank()) candidates else candidates.filter {
            val digitsQuery = search.filter { ch -> ch.isDigit() }
            (digitsQuery.isNotEmpty() && it.e164.contains(digitsQuery)) ||
                it.suggestedLabel.contains(search, ignoreCase = true)
        }
    }
    val pageCount = ((filteredCandidates.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    val safePage = page.coerceIn(0, pageCount - 1)
    val pageCandidates = filteredCandidates.drop(safePage * pageSize).take(pageSize)
    val listState = rememberLazyListState()
    var disclosure by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("Aguardando autorização para a primeira varredura.") }
    var summary by remember { mutableStateOf<com.orangeshield.app.history.ScanSummary?>(null) }

    val scanner = remember { CallHistoryScanner(context.applicationContext, dao) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                scanning = true
                runCatching { scanner.scan() }
                    .onSuccess {
                        summary = it
                        result = "Varredura concluída. Os dados foram agregados localmente."
                    }
                    .onFailure { result = it.message ?: "Não foi possível ler o histórico." }
                scanning = false
            }
        } else {
            result = "Acesso recusado. Nenhum dado foi lido."
        }
    }

    if (disclosure) {
        AlertDialog(
            onDismissRequest = { disclosure = false },
            title = { Text("Varredura do histórico de chamadas") },
            text = {
                Text(
                    "O ORANGE SHIELD vai ler até 5.000 registros disponíveis no histórico de chamadas para agrupar números, frequência, chamadas perdidas, rejeitadas e registros marcados como bloqueados pelo aparelho. A leitura é somente local. Não apagamos, desbloqueamos, bloqueamos nem alteramos chamadas ou listas nativas. Os resultados são sugestões, não acusações de fraude. Você pode cancelar e continuar usando os demais recursos."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    disclosure = false
                    permissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                }) { Text("Concordo e iniciar") }
            },
            dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Cancelar") } }
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(OrangePrimary.copy(alpha = .14f)),
                    contentAlignment = Alignment.Center
                ) { androidx.compose.material3.Icon(Icons.Filled.Radar, null, tint = OrangePrimary) }
                Column {
                    Text("ORANGE INTELLIGENCE", color = OrangePrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Central de inteligência local", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("VARREDURA DO DISPOSITIVO", color = OrangeSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Conheça o histórico antes da próxima chamada", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                        androidx.compose.material3.Icon(Icons.Filled.Fingerprint, null, tint = OrangePrimary, modifier = Modifier.size(30.dp))
                    }
                    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                            val radius = minOf(size.width, size.height) * .40f
                            for (i in 1..3) {
                                drawCircle(
                                    color = OrangePrimary.copy(alpha = .12f + i * .035f),
                                    radius = radius * i / 3f,
                                    center = center,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                )
                            }
                            drawLine(
                                color = OrangePrimary.copy(alpha = .8f),
                                start = center,
                                end = androidx.compose.ui.geometry.Offset(center.x + radius * .88f, center.y - radius * .35f),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawCircle(color = OrangePrimary, radius = 4.dp.toPx(), center = center)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(candidates.size.toString(), color = LightOnDark, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Text("números mapeados", color = MutedOnDark, fontSize = 11.sp)
                        }
                    }
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                                scope.launch {
                                    scanning = true
                                    runCatching { scanner.scan() }
                                        .onSuccess { summary = it; result = "Varredura concluída com sucesso." }
                                        .onFailure { result = it.message ?: "Falha na leitura." }
                                    scanning = false
                                }
                            } else disclosure = true
                        },
                        enabled = !scanning,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (scanning) "ANALISANDO..." else "INICIAR VARREDURA INTELIGENTE") }
                    Text(result, color = MutedOnDark, fontSize = 11.sp)
                    summary?.let {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            MiniMetric("Registros", it.scannedRows.toString())
                            MiniMetric("Números", it.uniqueNumbers.toString())
                            MiniMetric("Repetidos", it.repeatedNumbers.toString())
                        }
                    }
                }
            }
        }
        item {
            Text("MAPA DE RECORRÊNCIA", color = OrangeSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Candidatos encontrados no aparelho", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; page = 0 },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Buscar número ou classificação") }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${filteredCandidates.size} resultados · página ${safePage + 1}/${pageCount}",
                    color = MutedOnDark, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { page = 0; scope.launch { listState.animateScrollToItem(0) } },
                        enabled = safePage > 0
                    ) { Text("Início") }
                    OutlinedButton(
                        onClick = { page = pageCount - 1; scope.launch { listState.animateScrollToItem(0) } },
                        enabled = safePage < pageCount - 1
                    ) { Text("Final") }
                }
            }
        }
        if (candidates.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = DarkSurface)) {
                    Text("Nenhum dado importado ainda. Autorize a varredura para construir seu mapa inicial.", modifier = Modifier.padding(16.dp), color = MutedOnDark)
                }
            }
        } else {
            items(pageCandidates, key = { it.e164 }) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = DarkSurface)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.e164, fontWeight = FontWeight.SemiBold)
                            Text(item.suggestedLabel, color = OrangeSecondary, fontSize = 11.sp)
                            Text(
                                "${item.totalCalls} chamadas · ${item.missedCalls} perdidas · ${item.blockedCalls} marcadas bloqueadas",
                                color = MutedOnDark, fontSize = 10.sp
                            )
                            Text("Última ocorrência: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.lastSeenAt))}",
                                color = MutedOnDark, fontSize = 10.sp)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (item.totalCalls >= 8) "ALTA\nRECORRÊNCIA" else "HISTÓRICO",
                                color = if (item.totalCalls >= 8) OrangePrimary else MutedOnDark,
                                fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = { onAnalyzeNumber(item.e164) }) { Text("Analisar") }
                        }
                    }
                }
            }
        }
        if (filteredCandidates.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { page = (safePage - 1).coerceAtLeast(0); scope.launch { listState.animateScrollToItem(0) } },
                        enabled = safePage > 0) { Text("Anterior") }
                    Text("${safePage + 1} / ${pageCount}", color = MutedOnDark, fontSize = 11.sp)
                    Button(onClick = { page = (safePage + 1).coerceAtMost(pageCount - 1); scope.launch { listState.animateScrollToItem(0) } },
                        enabled = safePage < pageCount - 1) { Text("Próxima") }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun MiniMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = OrangePrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MutedOnDark, fontSize = 10.sp)
    }
}
