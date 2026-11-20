package com.orangeshield.app.ui

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orangeshield.app.EngineProvider
import com.orangeshield.app.engine.EngineSettings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val store = EngineProvider.settingsStore
    val settings by store.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var resumeTick by remember { mutableIntStateOf(0) }
    var ownNumber by remember(settings.ownNumber) { mutableStateOf(settings.ownNumber) }
    var voiceTest by remember { mutableStateOf<String?>(null) }
    var roleMessage by remember { mutableStateOf<String?>(null) }
    var showPrivacy by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val roleManager = remember(resumeTick) { context.getSystemService(RoleManager::class.java) }
    val screeningSupported = remember(resumeTick) { roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true }
    val screeningActive = remember(resumeTick) { roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true }
    val callRoleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val active = context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        roleMessage = when {
            active -> "Triagem autorizada. O Android pode encaminhar chamadas ao ORANGE-SHIELD."
            result.resultCode == Activity.RESULT_OK -> "O Android não confirmou o papel de triagem."
            else -> "Autorização não concedida. A proteção de chamadas não foi ativada."
        }
        resumeTick++
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(context, if (granted) "Permissão concedida." else "Permissão não concedida.", Toast.LENGTH_SHORT).show()
        resumeTick++
    }

    fun save(next: EngineSettings) { scope.launch { store.save(next) } }
    fun requestPermission(permission: String) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Essa permissão já está concedida.", Toast.LENGTH_SHORT).show()
        } else permissionLauncher.launch(permission)
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("Permissões e privacidade") },
            text = { Text("O ORANGE-SHIELD só executa a triagem de chamadas quando o Android concede ROLE_CALL_SCREENING. A leitura do registro telefônico, contatos, microfone e notificações depende de permissões separadas. As análises e o histórico de triagem são processados localmente. Você pode revogar permissões nas configurações do Android.") },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Entendi") } }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("CENTRAL DE CONTROLE", color = OrangePrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Configurações", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Tudo que altera o comportamento do escudo fica reunido aqui.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }

        SettingsGroup("Proteção principal", "Controle geral e comportamento do bloqueio") {
            SettingSwitch("Proteção ativa", "Liga ou pausa a análise de chamadas.", settings.protectionActive) { save(settings.copy(protectionActive = it)) }
            SettingSwitch("Modo silencioso", "Silencia alertas sonoros gerados pelo aplicativo.", settings.silenceMode) { save(settings.copy(silenceMode = it)) }
            SettingSwitch("Proteção contra insistência", "Considera repetição e frequência das chamadas.", settings.insistenceProtection) { save(settings.copy(insistenceProtection = it)) }
            SettingSwitch("Bloqueio automático de incômodo", "Aplica as regras automáticas para números reincidentes.", settings.autoBlockNuisance) { save(settings.copy(autoBlockNuisance = it)) }
            SettingSwitch("Modo agressivo", "Bloqueia números que ligam repetidamente. Contatos autorizados continuam protegidos.", settings.aggressiveBlock) { save(settings.copy(aggressiveBlock = it)) }
            SettingSwitch("Bloquear números desconhecidos", "Bloqueia números sem reputação local.", settings.blockUnknownNumbers) { save(settings.copy(blockUnknownNumbers = it)) }
            SettingSwitch("Bloquear chamadas internacionais", "Aplica bloqueio a chamadas internacionais.", settings.blockInternational) { save(settings.copy(blockInternational = it)) }
        }

        SettingsGroup("Categorias de chamadas", "Escolha quais categorias entram nas regras de bloqueio") {
            SettingSwitch("Telemarketing", "Ofertas comerciais não solicitadas.", settings.blockTelemarketing) { save(settings.copy(blockTelemarketing = it)) }
            SettingSwitch("Robocalls", "Chamadas automatizadas e gravações.", settings.blockRobocalls) { save(settings.copy(blockRobocalls = it)) }
            SettingSwitch("Cobranças", "Chamadas classificadas como cobrança.", settings.blockCollectors) { save(settings.copy(blockCollectors = it)) }
            SettingSwitch("Publicidade de operadoras", "Ofertas e campanhas de operadoras.", settings.blockOperatorAds) { save(settings.copy(blockOperatorAds = it)) }
        }

        SettingsGroup("Sensibilidade do detector", "Ajuste os limites de pontuação de risco") {
            Text("Limite para bloquear: ${settings.blockThreshold}", color = LightOnDark, fontWeight = FontWeight.SemiBold)
            Slider(value = settings.blockThreshold.toFloat(), onValueChange = { save(settings.copy(blockThreshold = it.toInt().coerceIn(1, 100))) }, valueRange = 1f..100f)
            Text("Limite para alertar: ${settings.warnThreshold}", color = LightOnDark, fontWeight = FontWeight.SemiBold)
            Slider(value = settings.warnThreshold.toFloat(), onValueChange = { save(settings.copy(warnThreshold = it.toInt().coerceIn(0, 99))) }, valueRange = 0f..99f)
            Text("Os limites são aplicados pelo motor local. Valores mais baixos tornam a triagem mais sensível.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }

        SettingsGroup("ORANGE VOICE", "Assistente durante o toque da chamada") {
            SettingSwitch("Assistente de voz", "Anuncia o veredito e escuta comandos durante o toque.", settings.voiceAssistant) {
                save(settings.copy(voiceAssistant = it))
                com.orangeshield.app.voice.RingingCallState.voiceEnabled = it
                if (it) requestPermission(Manifest.permission.RECORD_AUDIO)
            }
            SettingSwitch("Proteção contra spoofing", "Ativa os sinais de falsificação de número no motor.", settings.spoofProtection) { save(settings.copy(spoofProtection = it)) }
            Button(onClick = { requestPermission(Manifest.permission.ANSWER_PHONE_CALLS) }, modifier = Modifier.fillMaxWidth()) { Text("Verificar permissão para atender/recusar") }
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermission(Manifest.permission.RECORD_AUDIO)
                    voiceTest = "Conceda o microfone e tente novamente."
                } else {
                    voiceTest = "Escutando… fale uma frase."
                    com.orangeshield.app.voice.VoiceTest.run(context) { heard, error ->
                        voiceTest = heard?.let { "Reconhecimento: \"$it\"" } ?: error
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Testar reconhecimento de voz") }
            voiceTest?.let { Text(it, color = OrangeSecondary, fontSize = 12.sp) }
        }

        SettingsGroup("ORANGE LENS · identidade do usuário", "Número usado para detectar tentativas de imitar sua linha") {
            OutlinedTextField(value = ownNumber, onValueChange = { ownNumber = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Seu número com DDD") }, placeholder = { Text("+55 11 99999-9999") })
            Button(onClick = {
                val normalized = com.orangeshield.app.engine.normalizePhone(ownNumber)
                if (normalized.isBlank()) Toast.makeText(context, "Informe um número válido com DDD.", Toast.LENGTH_LONG).show()
                else { save(settings.copy(ownNumber = normalized)); ownNumber = normalized; Toast.makeText(context, "Número salvo.", Toast.LENGTH_SHORT).show() }
            }, modifier = Modifier.fillMaxWidth()) { Text("Salvar número") }
            TextButton(onClick = { ownNumber = ""; save(settings.copy(ownNumber = "")) }) { Text("Remover número cadastrado") }
        }

        SettingsGroup("ORANGE CHAIN · rede comunitária", "Compartilhamento assinado e sincronização local") {
            SettingSwitch("Compartilhar bloqueios", "Publica denúncias pseudônimas e assinadas na rede comunitária.", settings.chainShare) { save(settings.copy(chainShare = it)) }
            SettingSwitch("Mesh local", "Permite sincronização direta entre aparelhos na mesma rede Wi-Fi.", settings.chainMesh) { save(settings.copy(chainMesh = it)) }
            Button(onClick = {
                scope.launch {
                    runCatching {
                        com.orangeshield.app.chain.ChainNode.ensureGenesis(context)
                        val result = com.orangeshield.app.chain.ChainTransport.pull(context)
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    }.onFailure { Toast.makeText(context, "Falha na sincronização: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Sincronizar e validar cadeia") }
        }

        SettingsGroup("WhatsApp Guard", "Preferências de análise das notificações do WhatsApp") {
            SettingSwitch("Proteção WhatsApp", "Ativa a análise local das notificações compatíveis.", settings.waProtect) { save(settings.copy(waProtect = it)) }
            SettingSwitch("Bloquear remetentes desconhecidos", "Aplica a regra para remetentes não reconhecidos.", settings.waBlockUnknown) { save(settings.copy(waBlockUnknown = it)) }
            SettingSwitch("Bloquear links suspeitos", "Eleva o risco de mensagens com links.", settings.waBlockLinks) { save(settings.copy(waBlockLinks = it)) }
            SettingSwitch("Mensagens encaminhadas", "Considera encaminhamentos como sinal de risco.", settings.waBlockForwarded) { save(settings.copy(waBlockForwarded = it)) }
            SettingSwitch("Tentativas de roubo de código", "Detecta solicitações suspeitas de códigos de verificação.", settings.waBlockCodePhish) { save(settings.copy(waBlockCodePhish = it)) }
            SettingSwitch("Convites de grupo", "Aplica a regra de análise a convites de grupo.", settings.waBlockGroupInvite) { save(settings.copy(waBlockGroupInvite = it)) }
            Button(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    .onFailure { Toast.makeText(context, "Não foi possível abrir as configurações de notificações.", Toast.LENGTH_LONG).show() }
            }, modifier = Modifier.fillMaxWidth()) { Text("Gerenciar acesso às notificações") }
        }

        SettingsGroup("Permissões e diagnóstico", "Autorizações controladas pelo Android") {
            SettingStatus("Triagem de chamadas", if (!screeningSupported) "Não suportada neste aparelho" else if (screeningActive) "Ativa" else "Pendente", screeningActive)
            Button(onClick = {
                when {
                    !screeningSupported -> roleMessage = "Este aparelho não oferece ROLE_CALL_SCREENING."
                    screeningActive -> roleMessage = "A triagem já está autorizada."
                    else -> callRoleLauncher.launch(roleManager!!.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = screeningSupported && !screeningActive) { Text(if (screeningActive) "Triagem autorizada" else "Ativar triagem de chamadas") }
            SettingStatus("Registro de chamadas", permissionGranted(context, Manifest.permission.READ_CALL_LOG), "Concedida")
            Button(onClick = { requestPermission(Manifest.permission.READ_CALL_LOG) }, modifier = Modifier.fillMaxWidth()) { Text("Permitir leitura do histórico (opcional)") }
            SettingStatus("Contatos", permissionGranted(context, Manifest.permission.READ_CONTACTS), "Concedida")
            Button(onClick = { requestPermission(Manifest.permission.READ_CONTACTS) }, modifier = Modifier.fillMaxWidth()) { Text("Permitir identificação de contatos") }
            SettingStatus("Microfone", permissionGranted(context, Manifest.permission.RECORD_AUDIO), "Concedida")
            Button(onClick = { requestPermission(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.fillMaxWidth()) { Text("Permitir microfone para ORANGE VOICE") }
            if (Build.VERSION.SDK_INT >= 33) {
                Button(onClick = { requestPermission(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) { Text("Permitir notificações") }
            }
            roleMessage?.let { Text(it, color = OrangeSecondary, fontSize = 12.sp) }
            TextButton(onClick = { showPrivacy = true }) { Text("Sobre permissões e privacidade") }
        }

        Text("ORANGE-SHIELD · Configurações centralizadas · v2.11", color = MutedOnDark, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp))
    }
}

@Composable
private fun SettingsGroup(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = OrangePrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            content()
        }
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingStatus(title: String, granted: Boolean, grantedText: String) {
    SettingStatus(title, if (granted) grantedText else "Pendente", granted)
}

@Composable
private fun SettingStatus(title: String, value: String, active: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontSize = 12.sp)
        Text(value, color = if (active) EmeraldOk else AmberWarn, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun permissionGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
