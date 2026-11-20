package com.orangeshield.app.ui

import android.app.role.RoleManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orangeshield.app.EngineProvider

@Composable
fun PainelScreen() {
    val context = LocalContext.current
    val dao = EngineProvider.database.callEventDao()
    val total by dao.countTotal().collectAsStateWithLifecycle(initialValue = 0)
    val blocked by dao.countByDecision("BLOCK").collectAsStateWithLifecycle(initialValue = 0)
    val warned by dao.countByDecision("WARN").collectAsStateWithLifecycle(initialValue = 0)
    val allowed by dao.countByDecision("ALLOW").collectAsStateWithLifecycle(initialValue = 0)
    val silenced by dao.countByDecision("SILENCE").collectAsStateWithLifecycle(initialValue = 0)
    val recent by dao.listRecent(5).collectAsStateWithLifecycle(initialValue = emptyList())
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    val screeningActive = remember { roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("CENTRO DE OPERAÇÕES", color = OrangePrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Text("Visão geral do escudo", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Acompanhe a atividade e a saúde da proteção em um só lugar.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ESTADO DA PROTEÇÃO", color = OrangePrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(if (screeningActive) "TRIAGEM ANDROID ATIVA" else "TRIAGEM AINDA NÃO AUTORIZADA", color = if (screeningActive) EmeraldOk else AmberWarn, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(if (screeningActive) "O Android pode encaminhar chamadas recebidas ao mecanismo de análise." else "Abra Configurações para conceder ROLE_CALL_SCREENING. Sem essa autorização, o app não intercepta chamadas.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardMetric("Triagens", total, Modifier.weight(1f))
            DashboardMetric("Bloqueadas", blocked, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardMetric("Alertadas", warned, Modifier.weight(1f))
            DashboardMetric("Permitidas", allowed + silenced, Modifier.weight(1f))
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ÚLTIMAS ATIVIDADES", color = OrangePrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                if (recent.isEmpty()) {
                    Text("Nenhuma chamada analisada ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                } else recent.forEach { event ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(event.e164.ifBlank { event.rawNumber }, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text(event.reason, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 2)
                        }
                        Text(event.decision, color = when (event.decision) { "BLOCK" -> RedBlock; "WARN" -> AmberWarn; else -> EmeraldOk }, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("CENTRAIS DO SISTEMA", color = OrangePrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("Chamadas: atendimento e histórico de triagem.", fontSize = 12.sp)
                Text("Radar e Análise: inteligência, evidências e investigação de números.", fontSize = 12.sp)
                Text("Listas: números bloqueados e contatos autorizados.", fontSize = 12.sp)
                Text("Configurações: todas as permissões, regras e preferências ficam na última aba.", color = OrangeSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DashboardMetric(label: String, value: Int, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value.toString(), color = OrangePrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}
