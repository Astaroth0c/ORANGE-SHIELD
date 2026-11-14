package com.orangeshield.app.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orangeshield.app.EngineProvider
import com.orangeshield.app.notify.Notifier
import kotlinx.coroutines.launch

/**
 * ORANGE SHIELD — aba Painel.
 * Estatísticas locais, instruções do detector de 4 passos, configuração do
 * número do usuário (neighbor spoofing) e os fluxos de permissão do aparelho:
 * ROLE_CALL_SCREENING (triagem) e acesso a notificações (Guardião WhatsApp).
 */
@Composable
fun PainelScreen() {
    val context = LocalContext.current
    var showCallDisclosure by remember { mutableStateOf(false) }
    var showWhatsAppDisclosure by remember { mutableStateOf(false) }
    var showNotificationDisclosure by remember { mutableStateOf(false) }
    val dao = EngineProvider.database.callEventDao()

    val total by dao.countTotal().collectAsStateWithLifecycle(initialValue = 0)
    val blocked by dao.countByDecision("BLOCK").collectAsStateWithLifecycle(initialValue = 0)
    val warned by dao.countByDecision("WARN").collectAsStateWithLifecycle(initialValue = 0)
    val spoofFlagged by dao.countByCategory("spoofing").collectAsStateWithLifecycle(initialValue = 0)
    val historyDao = EngineProvider.database.historyCandidateDao()
    val historyCandidates by historyDao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val historyCalls = historyCandidates.sumOf { it.totalCalls }
    val historyNumbers = historyCandidates.size
    val historyBlocked = historyCandidates.sumOf { it.blockedCalls }
    val historyReview = historyCandidates.count {
        it.totalCalls >= 4 || it.blockedCalls > 0 || it.e164.startsWith("+550303")
    }
    val historyFormatFlags = historyCandidates.count { candidate ->
        EngineProvider.spoofDetector.formatStepChecks(candidate.e164).any {
            it.status == com.orangeshield.app.engine.SpoofStatus.FLAGGED
        }
    }

    // Recalcula os status de permissão quando a tela volta ao primeiro plano.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }

    val roleManager = remember(resumeTick) { context.getSystemService(RoleManager::class.java) }
    val triagemSuportada = remember(resumeTick, roleManager) {
        roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true
    }
    val triagemAtiva = remember(resumeTick, roleManager) {
        roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
    }
    var roleStatusMessage by remember { mutableStateOf<String?>(null) }
    val callScreeningRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val granted = context.getSystemService(RoleManager::class.java)
            ?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        roleStatusMessage = if (granted) {
            "Triagem autorizada pelo Android. O ORANGE-SHIELD já pode receber chamadas para análise."
        } else if (result.resultCode == android.app.Activity.RESULT_OK) {
            "O Android retornou confirmação, mas o papel não foi concedido. Confira o app de triagem nas configurações de chamadas."
        } else {
            "Autorização não concedida. Nenhuma chamada será interceptada pelo ORANGE-SHIELD."
        }
        resumeTick++
    }
    val listenerAtivo = remember(resumeTick) { Notifier.isNotificationListenerEnabled(context) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                "Sem essa permissão os alertas de bloqueio não aparecem",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    if (showNotificationDisclosure) {
        AlertDialog(
            onDismissRequest = { showNotificationDisclosure = false },
            title = { Text("Notificações do ORANGE SHIELD") },
            text = {
                Text(
                    "Esta permissão permite exibir alertas locais quando uma chamada for bloqueada ou identificada como suspeita. " +
                    "Ela não dá acesso ao conteúdo das notificações de outros aplicativos. Você pode continuar sem conceder; a triagem de chamadas continuará ativa, mas os avisos do ORANGE SHIELD poderão não aparecer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationDisclosure = false
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Permitir notificações") }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationDisclosure = false }) { Text("Agora não") }
            }
        )
    }

    if (showWhatsAppDisclosure) {
        AlertDialog(
            onDismissRequest = { showWhatsAppDisclosure = false },
            title = { Text("Guardião WhatsApp: acesso às notificações") },
            text = {
                Text(
                    "Para detectar mensagens suspeitas, o app lê o título e o texto das notificações somente dos pacotes WhatsApp e WhatsApp Business. " +
                    "A análise ocorre localmente; o texto original não é armazenado nem enviado a servidores. O histórico guarda remetente, decisão, pontuação e motivo. " +
                    "Você pode recusar e continuar usando a proteção de chamadas."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSharedPreferences("orange_shield_privacy", Context.MODE_PRIVATE)
                        .edit().putBoolean("whatsapp_notification_access_consent", true).apply()
                    showWhatsAppDisclosure = false
                    try {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } catch (_: Exception) {
                        Toast.makeText(context, "Abra manualmente Acesso a notificações nas configurações.", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Concordo e abrir configurações") }
            },
            dismissButton = {
                TextButton(onClick = { showWhatsAppDisclosure = false }) { Text("Agora não") }
            }
        )
    }

    if (showCallDisclosure) {
        AlertDialog(
            onDismissRequest = { showCallDisclosure = false },
            title = { Text("Triagem inteligente de chamadas") },
            text = {
                Text(
                    "Ao ativar a função, o Android entrega ao ORANGE SHIELD os números das chamadas recebidas para análise local de formato, frequência e sinais de spoofing. " +
                    "O resultado pode permitir, alertar ou bloquear conforme as regras que você configurar. O histórico guarda o número, pontuação, decisão e evidências no aparelho. " +
                    "O app não lê seu histórico telefônico nem envia esses dados a servidores. Você pode não ativar ou retirar o papel de triagem nas configurações do Android."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCallDisclosure = false
                    val manager = context.getSystemService(RoleManager::class.java)
                    when {
                        manager == null -> roleStatusMessage = "Este aparelho não disponibilizou o gerenciador de funções do Android."
                        !manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) ->
                            roleStatusMessage = "Este aparelho não oferece a função de triagem de chamadas do Android."
                        manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) ->
                            roleStatusMessage = "A triagem de chamadas já está autorizada neste aparelho."
                        else -> try {
                            callScreeningRoleLauncher.launch(
                                manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                            )
                        } catch (error: Exception) {
                            roleStatusMessage = "Não foi possível abrir a autorização do Android: ${error.message ?: "erro desconhecido"}"
                        }
                    }
                    resumeTick++
                }) { Text("Concordo e continuar") }
            },
            dismissButton = {
                TextButton(onClick = { showCallDisclosure = false }) { Text("Agora não") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Painel de proteção", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        // ---- Estatísticas ----
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(label = "Triagens", value = total.toString(), modifier = Modifier.weight(1f))
            StatCard(label = "Bloqueadas", value = blocked.toString(), modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(label = "Alertadas", value = warned.toString(), modifier = Modifier.weight(1f))
            StatCard(label = "Spoofing em triagens", value = spoofFlagged.toString(), modifier = Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(label = "Sinais para revisar", value = historyReview.toString(), modifier = Modifier.weight(1f))
            StatCard(label = "Anomalias de formato", value = historyFormatFlags.toString(), modifier = Modifier.weight(1f))
        }

        OrangeGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("INTELIGÊNCIA IMPORTADA DO RADAR", color = OrangePrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Dados reais do histórico do aparelho", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(label = "Chamadas lidas", value = historyCalls.toString(), modifier = Modifier.weight(1f))
                    StatCard(label = "Números", value = historyNumbers.toString(), modifier = Modifier.weight(1f))
                    StatCard(label = "Marcadas bloqueadas", value = historyBlocked.toString(), modifier = Modifier.weight(1f))
                }
                Text(
                    "As triagens acima contam chamadas processadas pelo ORANGE SHIELD. Estes indicadores mostram separadamente o histórico importado pelo Radar; registros nativos de bloqueio variam conforme o fabricante.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---- Configuração do aparelho ----
        OrangeGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Configuração do aparelho",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                StatusRow(
                    label = "Triagem de chamadas (ROLE_CALL_SCREENING)",
                    status = when {
                        !triagemSuportada -> "não suportado"
                        triagemAtiva -> "ativa"
                        else -> "pendente"
                    },
                    active = triagemAtiva
                )
                Button(
                    onClick = {
                        roleStatusMessage = null
                        showCallDisclosure = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = triagemSuportada && !triagemAtiva
                ) {
                    Text(if (triagemAtiva) "Triagem já autorizada" else "Ativar triagem de chamadas")
                }
                roleStatusMessage?.let { message ->
                    Text(
                        text = message,
                        fontSize = 12.sp,
                        color = if (triagemAtiva) EmeraldOk else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusRow(
                    label = "Acesso a notificações (Guardião WhatsApp)",
                    status = if (listenerAtivo) "concedido" else "pendente",
                    active = listenerAtivo
                )
                Button(
                    onClick = { showWhatsAppDisclosure = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Permitir acesso a notificações")
                }
                TextButton(
                    onClick = { showNotificationDisclosure = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Conceder permissão de notificações (Android 13+)", fontSize = 12.sp)
                }
            }
        }

        // ---- Número do usuário (Passo 2 · neighbor spoofing) ----
        OwnNumberCard()

        // ---- Os 4 passos do detector ----
        OrangeGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Os 4 passos do detector", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                StepLine(
                    title = "1 · FORMATO",
                    description = "Valida DDD da ANATEL, regra do nono dígito e padrões sintéticos de dígitos."
                )
                StepLine(
                    title = "2 · APRESENTAÇÃO",
                    description = "Detecta formato internacional inválido, excesso de dígitos e neighbor spoofing."
                )
                StepLine(
                    title = "3 · FREQUÊNCIA",
                    description = "Rediscagem rápida (Wangiri) e ondas de disparo no mesmo DDD."
                )
                StepLine(
                    title = "4 · REPUTAÇÃO",
                    description = "Golpe confirmado, denúncias coletivas e classificação da base local."
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    OrangeGlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = value,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = OrangePrimary,
                maxLines = 1
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, status: String, active: Boolean) {
    val statusColor = if (active) EmeraldOk else AmberWarn
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Text(
            text = label,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = status,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = statusColor,
            maxLines = 1
        )
    }
}

@Composable
private fun StepLine(title: String, description: String) {
    Column {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = OrangePrimary
        )
        Text(
            text = description,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OwnNumberCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = EngineProvider.settingsStore
    val settings by store.state.collectAsStateWithLifecycle()
    var ownNumber by remember(settings.ownNumber) { mutableStateOf(settings.ownNumber) }

    OrangeGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Seu número (neighbor spoofing)",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Usado no Passo 2 para detectar chamadas que imitam o seu número. Ex.: +55 11 99999-9999",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = ownNumber,
                onValueChange = { ownNumber = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Seu número") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val normalized = com.orangeshield.app.engine.normalizePhone(ownNumber)
                        if (normalized.isNotBlank()) {
                            scope.launch {
                                store.save(settings.copy(ownNumber = normalized))
                                ownNumber = normalized
                                Toast.makeText(context, "Número salvo.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Informe um número válido com DDD.", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val normalized = com.orangeshield.app.engine.normalizePhone(ownNumber)
                        if (normalized.isBlank()) {
                            Toast.makeText(context, "Informe um número válido com DDD.", Toast.LENGTH_LONG).show()
                        } else {
                            scope.launch {
                                store.save(settings.copy(ownNumber = normalized))
                                ownNumber = normalized
                                Toast.makeText(context, "Número salvo. A comparação de vizinhança será usada nas próximas análises.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Salvar número") }
                TextButton(
                    onClick = {
                        scope.launch {
                            store.save(settings.copy(ownNumber = ""))
                            ownNumber = ""
                            Toast.makeText(context, "Número removido da análise de vizinhança.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Remover número") }
            }
        }
    }
}

