package com.orangeshield.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orangeshield.app.EngineProvider
import com.orangeshield.app.osint.OrangeLens
import kotlinx.coroutines.launch

/**
 * ORANGE LENS — cartão de inteligência real do número analisado.
 * Inteligência offline (Google), reputação comunitária sincronizada do
 * repositório e deep links para fontes públicas oficiais.
 */
@Composable
fun OrangeLensCard(e164: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = EngineProvider.database

    val intel = remember(e164) { OrangeLens.offlineIntel(e164) }
    var community by remember(e164) { mutableStateOf<com.orangeshield.app.data.CommunityReportEntity?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(e164) {
        community = db.communityReportDao().findByE164(e164)
    }

    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    OrangeGlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    "ORANGE LENS · INTELIGÊNCIA DE NÚMERO",
                    color = OrangePrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    "Fontes reais: datasets Google (offline), comunidade ORANGE e ferramentas públicas",
                    fontSize = 10.sp,
                    color = MutedOnDark
                )
            }

            LensRow("Tipo de linha", intel.lineType)
            LensRow("Praça / região", intel.region ?: "indisponível para este número")
            LensRow("Operadora do prefixo", intel.originCarrier ?: "não identificada (prefixo portado ou desconhecido)")
            LensRow("País", intel.country ?: "—")

            community?.let { report ->
                val label = when (report.classification) {
                    "fraud_confirmed" -> "GOLPE CONFIRMADO pela comunidade"
                    "spam_reports" -> "Denunciado pela comunidade"
                    "telemarketing" -> "Telemarketing (comunidade)"
                    "robocall" -> "Robocall (comunidade)"
                    "collector" -> "Cobrança (comunidade)"
                    "verified_business" -> "Empresa verificada (comunidade)"
                    else -> "Registrado no feed comunitário"
                }
                val color = when (report.classification) {
                    "verified_business" -> EmeraldOk
                    else -> RedBlock
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "COMUNIDADE ORANGE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MutedOnDark
                    )
                    Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
                    if (report.reportCount > 0) {
                        Text(
                            text = "${report.reportCount} denúncia(s) · confiança ${(report.confidence * 100).toInt()}%",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (report.notes.isNotBlank()) {
                        Text(text = report.notes, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = "Este registro alimenta o Passo 4 do detector e as decisões de chamadas reais.",
                        fontSize = 10.sp,
                        color = MutedOnDark
                    )
                }
            }

            syncMessage?.let {
                Text(it, fontSize = 11.sp, color = AmberWarn)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { open(OrangeLens.abrTelecomUrl()) },
                    modifier = Modifier.weight(1f)
                ) { Text("Portabilidade (ABR)", fontSize = 11.sp, maxLines = 1) }
                OutlinedButton(
                    onClick = { open(OrangeLens.tellowsUrl(e164)) },
                    modifier = Modifier.weight(1f)
                ) { Text("Tellows", fontSize = 11.sp, maxLines = 1) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { open(OrangeLens.webSearchUrl(e164)) },
                    modifier = Modifier.weight(1f)
                ) { Text("Buscar na web", fontSize = 11.sp, maxLines = 1) }
                OutlinedButton(
                    onClick = { open(OrangeLens.reportIssueUrl(e164, "Descreva o ocorrido: horário da chamada/mensagem, conteúdo e motivo do reporte.")) },
                    modifier = Modifier.weight(1f)
                ) { Text("Reportar número", fontSize = 11.sp, maxLines = 1) }
            }
            TextButton(
                onClick = {
                    syncing = true
                    syncMessage = null
                    scope.launch {
                        when (val result = OrangeLens.syncCommunityFeed(context, force = true)) {
                            is OrangeLens.SyncResult.Synced -> {
                                community = db.communityReportDao().findByE164(e164)
                                syncMessage = "Feed sincronizado: ${result.entries} número(s) na base comunitária."
                            }
                            OrangeLens.SyncResult.Cached -> syncMessage = "Feed em cache — sincronizado há menos de 6 h."
                            is OrangeLens.SyncResult.Unavailable -> syncMessage = result.reason
                        }
                        syncing = false
                    }
                },
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (syncing) "Sincronizando…" else "Sincronizar reputação da comunidade", fontSize = 12.sp)
            }

            Text(
                "Limitação honesta: a operadora mostrada é a de ORIGEM do prefixo (base offline). " +
                    "Para a operadora ATUAL (portabilidade), use a consulta oficial da ABR Telecom acima — " +
                    "ela é a base autoritativa e pública. Nenhum app pode rastrear a localização de uma linha.",
                fontSize = 10.sp,
                color = MutedOnDark
            )
        }
    }
}

@Composable
private fun LensRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MutedOnDark,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.58f)
        )
    }
}
