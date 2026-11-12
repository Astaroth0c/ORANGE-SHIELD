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
    val dao = EngineProvider.database.callEventDao()

    val total by dao.countTotal().collectAsStateWithLifecycle(initialValue = 0)
    val blocked by dao.countByDecision("BLOCK").collectAsStateWithLifecycle(initialValue = 0)
    val warned by dao.countByDecision("WARN").collectAsStateWithLifecycle(initialValue = 0)
    val spoofFlagged by dao.countByCategory("spoofing").collectAsStateWithLifecycle(initialValue = 0)

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
            StatCard(label = "Com spoofing", value = spoofFlagged.toString(), modifier = Modifier.weight(1f))
        }

        // ---- Configuração do aparelho ----
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
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
                    onClick = { requestCallScreeningRole(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ativar triagem de chamadas")
                }
                StatusRow(
                    label = "Acesso a notificações (Guardião WhatsApp)",
                    status = if (listenerAtivo) "concedido" else "pendente",
                    active = listenerAtivo
                )
                Button(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            )
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "Não foi possível abrir as configurações de acesso",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Permitir acesso a notificações")
                }
                TextButton(
                    onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Conceder permissão de notificações (Android 13+)", fontSize = 12.sp)
                }
            }
        }

        // ---- Número do usuário (Passo 2 · neighbor spoofing) ----
        OwnNumberCard()

        // ---- Os 4 passos do detector ----
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
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
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = value,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = OrangePrimary
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            modifier = Modifier.weight(1f)
        )
        Text(
            text = status,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = statusColor
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
    val scope = rememberCoroutineScope()
    val store = EngineProvider.settingsStore
    val settings by store.state.collectAsStateWithLifecycle()
    var ownNumber by remember(settings.ownNumber) { mutableStateOf(settings.ownNumber) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
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
                        scope.launch {
                            store.save(settings.copy(ownNumber = ownNumber))
                        }
                    }
                )
            )
        }
    }
}

/** Abre o diálogo nativo do RoleManager para ROLE_CALL_SCREENING (Android 10+). */
private fun requestCallScreeningRole(context: Context) {
    val roleManager = context.getSystemService(RoleManager::class.java)
    if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
        Toast.makeText(
            context,
            "Triagem de chamadas não é suportada neste aparelho",
            Toast.LENGTH_LONG
        ).show()
        return
    }
    if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
        Toast.makeText(
            context,
            "A triagem de chamadas já está ativa",
            Toast.LENGTH_SHORT
        ).show()
        return
    }
    try {
        context.startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "Não foi possível abrir o diálogo de permissão",
            Toast.LENGTH_LONG
        ).show()
    }
}
