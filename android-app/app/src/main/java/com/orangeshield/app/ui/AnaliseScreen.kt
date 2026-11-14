package com.orangeshield.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orangeshield.app.EngineProvider
import com.orangeshield.app.data.CallEventEntity
import com.orangeshield.app.engine.CallAnalysis
import com.orangeshield.app.engine.CallDecision
import com.orangeshield.app.engine.EngineEvidence
import com.orangeshield.app.engine.EngineStep
import com.orangeshield.app.engine.SpoofAnalysis
import com.orangeshield.app.engine.SpoofCheck
import com.orangeshield.app.engine.SpoofStatus
import com.orangeshield.app.engine.SpoofStepResult
import com.orangeshield.app.engine.riskBand
import com.orangeshield.app.engine.normalizePhoneDetailed
import com.orangeshield.app.engine.parseBrazilNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * ORANGE SHIELD — aba Análise.
 *
 * Roda o SmartCall Engine local (Call Guard + Spoof Detector) sobre um número
 * informado pelo usuário e exibe a decisão, o score de risco, a linha do tempo
 * de passos, as evidências e o pipeline de spoofing em 4 passos.
 *
 * IMPORTANTE: a análise é SOMENTE LEITURA (record = false) — nada é gravado
 * no histórico de triagens nem nas estatísticas do Painel. O histórico do
 * Painel contém apenas chamadas reais processadas pelo aparelho.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnaliseScreen(initialNumber: String = "") {
    val scope = rememberCoroutineScope()
    val store = EngineProvider.settingsStore

    var number by remember(initialNumber) { mutableStateOf(initialNumber) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<CallAnalysis?>(null) }
    var spoof by remember { mutableStateOf<SpoofAnalysis?>(null)
    var historicalEvents by remember { mutableStateOf<List<CallEventEntity>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Análise de número", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Digite um número e rode o motor local completo (4 passos). " +
                "A análise é somente leitura: nada é registrado no histórico de chamadas.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = number,
            onValueChange = { number = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Número do chamador") },
            enabled = !loading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { errorMessage?.let { } }
            )
        )

        Button(
            onClick = {
                if (number.isBlank()) {
                    errorMessage = "Informe um número para analisar."
                    return@Button
                }
                loading = true
                errorMessage = null
                scope.launch {
                    try {
                        val settings = store.load()
                        val analysis = withContext(Dispatchers.Default) {
                            EngineProvider.callEngine.analyzeIncomingCall(
                                number,
                                settings,
                                record = false
                            )
                        }
                        val spoofAnalysis = withContext(Dispatchers.Default) {
                            if (normalizePhoneDetailed(number).isValid) {
                                EngineProvider.spoofDetector.analyzeSpoofing(analysis.e164, settings.ownNumber)
                            } else {
                                null
                            }
                        }
                        historicalEvents = if (analysis.e164.isNotBlank()) {
                            withContext(Dispatchers.IO) { EngineProvider.database.callEventDao().findRecentByE164(analysis.e164) }
                        } else emptyList()
                        result = analysis
                        spoof = spoofAnalysis
                    } catch (e: Exception) {
                        errorMessage = "Falha ao analisar: ${e.message ?: "erro desconhecido"}"
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Analisando…" else "Analisar número")
        }

        errorMessage?.let { message ->
            Text(text = message, fontSize = 12.sp, color = RedBlock)
        }

        result?.let { analysis ->
            DecisionBadge(decision = analysis.decision)

            OrangeGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ORANGE ORCHESTRATOR · REGRAS EXPLICÁVEIS", color = OrangePrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(analysis.reason, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val recommendation = when (analysis.decision) {
                        CallDecision.ALLOW -> "Nenhum sinal atingiu o limiar atual. Isso não comprova a identidade; não compartilhe códigos ou dados sensíveis."
                        CallDecision.WARN -> "Atenda com cautela, se desejar. Não informe senhas, códigos ou dados bancários; confirme a empresa por um canal oficial."
                        CallDecision.SILENCE -> "A chamada foi silenciada pela política ativa. Revise o histórico antes de liberar o identificador."
                        CallDecision.BLOCK -> "A chamada foi bloqueada por regras ativas. Revise as evidências antes de adicionar exceções."
                    }
                    Text(recommendation, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    Text("Decisão determinística no aparelho: combina regras, reputação e sinais disponíveis; não usa IA generativa nem consulta uma nuvem.", color = MutedOnDark, fontSize = 10.sp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Risco: ${analysis.riskScore}/100 — ${riskBand(analysis.riskScore)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                LinearProgressIndicator(
                    progress = { analysis.riskScore / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    trackColor = DarkSurfaceVariant
                )
            }

            Text(
                text = "Número normalizado: ${analysis.e164.ifEmpty { "inválido" }}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            analysis.phoneNormalization?.let { identity ->
                OrangeGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("ORANGE IDENTITY SCAN", color = OrangePrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Recebido: ${identity.raw.ifBlank { "não informado" }}", fontSize = 12.sp)
                        Text("Canônico: ${identity.e164.ifBlank { "não identificado" }}", fontSize = 12.sp)
                        parseBrazilNumber(identity.e164)?.let { Text("DDD identificado: ${it.ddd}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(
                            if (identity.isValid) "Estrutura telefônica validada (${identity.source})"
                            else "Estrutura não validada — formato sozinho não confirma fraude",
                            color = if (identity.isValid) EmeraldOk else AmberWarn,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        identity.note?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        val networkLabel = when (analysis.callerVerificationStatus) {
                            "PASSED" -> "STIR/SHAKEN: origem validada pela rede; não garante que a chamada seja segura"
                            "FAILED" -> "STIR/SHAKEN: falha na validação da origem — sinal de possível spoofing"
                            "NOT_VERIFIED" -> "STIR/SHAKEN: não verificado — autenticidade desconhecida"
                            else -> "STIR/SHAKEN: não disponível nesta análise/rede"
                        }
                        Text(networkLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("IP, MAC, SIM remoto e processos instalados no aparelho de origem não são expostos ao app pelo Android.", fontSize = 10.sp, color = MutedOnDark)
                        Text("VoIP/central só será identificado quando houver metadado verificável; prefixo ou ausência de STIR não bastam.", fontSize = 10.sp, color = MutedOnDark)
                    }
                }
            }
            Text(text = analysis.reason, fontSize = 13.sp)
            if (analysis.category != "unknown") {
                CategoryChip(category = analysis.category)
            }


            if (historicalEvents.isNotEmpty()) {
                OrangeGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("HISTÓRICO DESTE IDENTIFICADOR", color = OrangePrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        historicalEvents.take(10).forEach { event ->
                            Text(
                                "${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(event.occurredAt))} · ${event.decision} · risco ${event.riskScore}/100",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (event.rawNumber.isNotBlank() && event.rawNumber != event.e164) {
                                Text("Original: ${event.rawNumber}", color = MutedOnDark, fontSize = 10.sp)
                            }
                            event.networkVerificationStatus?.let { Text("STIR/SHAKEN: $it", color = MutedOnDark, fontSize = 10.sp) }
                            Text(event.reason, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        }
                    }
                }
            }
            OrangeGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Linha do tempo da decisão", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    analysis.steps.forEach { step -> StepRow(step) }
                }
            }

            if (analysis.evidences.isNotEmpty()) {
                OrangeGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Evidências (${analysis.evidences.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            analysis.evidences.forEach { evidence -> EvidenceChip(evidence) }
                        }
                    }
                }
            }
        }

        spoof?.let { spoofAnalysis ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Detector de spoofing — ${spoofAnalysis.verdictLabel} (${spoofAnalysis.spoofScore}/100)",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            spoofAnalysis.steps.forEach { step -> SpoofStepCard(step) }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun DecisionBadge(decision: CallDecision, modifier: Modifier = Modifier) {
    val (color, label) = when (decision) {
        CallDecision.ALLOW -> EmeraldOk to "PERMITIR"
        CallDecision.WARN -> AmberWarn to "ALERTAR"
        CallDecision.SILENCE -> ZincNeutral to "SILENCIAR"
        CallDecision.BLOCK -> RedBlock to "BLOQUEAR"
    }
    Surface(
        color = color.copy(alpha = 0.18f),
        contentColor = color,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun CategoryChip(category: String) {
    Surface(
        color = OrangePrimary.copy(alpha = 0.15f),
        contentColor = OrangePrimary,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "Categoria: $category",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StepRow(step: EngineStep) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text = step.step, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = step.result,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EvidenceChip(evidence: EngineEvidence) {
    // Pontos negativos (ex.: empresa verificada) aparecem em verde.
    val addsRisk = evidence.points >= 0
    val color = if (addsRisk) RedBlock else EmeraldOk
    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "${evidence.label} ${if (evidence.points >= 0) "+" else ""}${evidence.points}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SpoofStepCard(step: SpoofStepResult) {
    val (statusColor, statusLabel) = when (step.status) {
        SpoofStatus.FLAGGED -> AmberWarn to "Alerta"
        SpoofStatus.PASS -> EmeraldOk to "OK"
        SpoofStatus.INFO -> ZincNeutral to "Neutro"
    }
    OrangeGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(OrangePrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${step.id}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrangePrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = step.description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.18f),
                    contentColor = statusColor,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (step.points > 0) {
                Text(
                    text = "+${step.points} pts no passo",
                    fontSize = 12.sp,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            step.checks.forEach { check -> CheckRow(check) }
        }
    }
}

@Composable
private fun CheckRow(check: SpoofCheck) {
    val (color, icon) = when (check.status) {
        SpoofStatus.FLAGGED -> RedBlock to Icons.Filled.Warning
        SpoofStatus.PASS -> EmeraldOk to Icons.Filled.CheckCircle
        SpoofStatus.INFO -> ZincNeutral to Icons.Filled.Info
    }
    val iconDescription = when (check.status) {
        SpoofStatus.FLAGGED -> "Alerta"
        SpoofStatus.PASS -> "OK"
        SpoofStatus.INFO -> "Neutro"
    }
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconDescription,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = check.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (check.status == SpoofStatus.FLAGGED) {
                    Text(
                        text = "+${check.points} pts",
                        fontSize = 12.sp,
                        color = RedBlock,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            Text(
                text = check.detail,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
