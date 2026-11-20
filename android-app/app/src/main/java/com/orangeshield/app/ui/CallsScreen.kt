package com.orangeshield.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orangeshield.app.EngineProvider
import com.orangeshield.app.voice.RingingCallState
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun CallsScreen() {
    val context = LocalContext.current
    val events by EngineProvider.database.callEventDao().listRecent(150).collectAsStateWithLifecycle(initialValue = emptyList())
    var ringingNumber by remember { mutableStateOf(RingingCallState.ringingNumber) }
    var ringingDecision by remember { mutableStateOf(RingingCallState.screeningDecision) }

    LaunchedEffect(Unit) {
        while (true) {
            ringingNumber = RingingCallState.ringingNumber
            ringingDecision = RingingCallState.screeningDecision
            delay(450)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("CENTRAL TELEFÔNICA", color = OrangePrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Text("Chamadas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Atendimento em tempo real e histórico das triagens feitas pelo escudo.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ATENDIMENTO", color = OrangePrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                if (!ringingNumber.isNullOrBlank()) {
                    Text("Chamada em andamento", color = EmeraldOk, fontWeight = FontWeight.SemiBold)
                    Text(ringingNumber.orEmpty(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Triagem: ${ringingDecision ?: "aguardando resultado"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    TextButton(onClick = { context.startActivity(Intent(context, CallAlertActivity::class.java).putExtra(com.orangeshield.app.notify.Notifier.EXTRA_NUMBER, ringingNumber)) }) {
                        Text("Abrir tela de atendimento")
                    }
                } else {
                    Text("Nenhuma chamada tocando agora.", fontWeight = FontWeight.SemiBold)
                    Text("Quando uma ligação chegar, a tela de atendimento em tela cheia será aberta pelo Android conforme as permissões e políticas do aparelho.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Histórico de triagem", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("${events.size} registros", color = MutedOnDark, fontSize = 11.sp)
        }
        if (events.isEmpty()) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Text("Ainda não há chamadas triadas. Ative ROLE_CALL_SCREENING em Configurações para habilitar a análise nativa.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events, key = { it.id }) { event ->
                    val color = when (event.decision) {
                        "BLOCK" -> RedBlock
                        "WARN" -> AmberWarn
                        "ALLOW" -> EmeraldOk
                        else -> OrangeSecondary
                    }
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(event.e164.ifBlank { event.rawNumber.ifBlank { "Número não identificado" } }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(event.decision, color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Text("${event.category ?: "Categoria não classificada"} · risco ${event.riskScore}/100", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            Text(event.reason, fontSize = 12.sp)
                            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(event.occurredAt)), color = MutedOnDark, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
