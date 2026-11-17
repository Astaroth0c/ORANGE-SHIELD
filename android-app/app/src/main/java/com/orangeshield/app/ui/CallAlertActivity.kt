package com.orangeshield.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.orangeshield.app.EngineProvider
import com.orangeshield.app.data.BlockedEntity
import com.orangeshield.app.voice.ContactLookup
import com.orangeshield.app.voice.RingingCallState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TELA DE CHAMADA (full-screen intent) — aparece durante o toque, sobre a
 * tela de bloqueio ou como heads-up. Mostra veredito da triagem em tempo
 * real, nome do contato da agenda e botões REAIS: Atender (viva-voz),
 * Recusar e Bloquear (encerra + lista negra).
 */
class CallAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EngineProvider.init(this)
        setContent {
            OrangeShieldTheme {
                Surface(color = DarkBackground, modifier = Modifier.fillMaxSize()) {
                    CallAlertContent(
                        initialNumber = RingingCallState.ringingNumber
                            ?: intent.getStringExtra(com.orangeshield.app.notify.Notifier.EXTRA_NUMBER)
                    )
                }
            }
        }
    }
}

@Composable
private fun CallAlertContent(initialNumber: String?) {
    val context = LocalContext.current
    val activity = remember { context as? android.app.Activity }
    val scope = rememberCoroutineScope()
    var number by remember { mutableStateOf(initialNumber) }
    var decision by remember { mutableStateOf(RingingCallState.screeningDecision) }
    var flagged by remember { mutableStateOf(RingingCallState.reputationFlagged) }
    val contact = remember(number) { ContactLookup.name(context, number) }

    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val telecom = remember { context.getSystemService(TelecomManager::class.java) }

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingAction?.invoke()
        else Toast.makeText(context, "Conceda a permissão de discador no Painel.", Toast.LENGTH_LONG).show()
        pendingAction = null
    }

    fun withAnswerPermission(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
            == PackageManager.PERMISSION_GRANTED
        ) action()
        else {
            pendingAction = action
            permissionLauncher.launch(Manifest.permission.ANSWER_PHONE_CALLS)
        }
    }

    fun enableSpeakerphone() {
        scope.launch {
            delay(1_200)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val speaker = audioManager?.availableCommunicationDevices
                        ?.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) audioManager.setCommunicationDevice(speaker)
                    else @Suppress("DEPRECATION") audioManager.setSpeakerphoneOn(true)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.setSpeakerphoneOn(true)
                }
            }
            delay(1_800)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && audioManager != null) {
                    val speaker = audioManager.availableCommunicationDevices
                        ?.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) audioManager.setCommunicationDevice(speaker)
                }
            }
        }
    }

    // Encerra a tela quando a chamada sai do toque (receiver limpa o estado).
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (isActive) {
            delay(500)
            number = RingingCallState.ringingNumber ?: number
            decision = RingingCallState.screeningDecision
            flagged = RingingCallState.reputationFlagged
            if (RingingCallState.ringingNumber == null) break
            if (System.currentTimeMillis() - start > 60_000) break
        }
        activity?.finish()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = when {
                flagged -> "SPAM · PROVÁVEL TENTATIVA DE GOLPE"
                decision == "WARN" -> "SUSPEITA DETECTADA"
                !contact.isNullOrBlank() -> "CHAMADA"
                else -> "NÚMERO DESCONHECIDO"
            },
            color = when {
                flagged -> RedBlock
                decision == "WARN" -> AmberWarn
                else -> OrangePrimary
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = contact ?: "Número desconhecido",
            color = LightOnDark,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (!contact.isNullOrBlank()) {
            Text(
                text = "Contato da agenda",
                color = EmeraldOk,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = number ?: "",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        if (flagged || decision == "WARN") {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Este número está marcado na base de reputação. Recomendado: recusar ou bloquear.",
                color = AmberWarn,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(36.dp))

        Button(
            onClick = {
                withAnswerPermission {
                    runCatching { telecom?.acceptRingingCall() }
                    enableSpeakerphone()
                    Toast.makeText(context, "Atendendo em viva voz.", Toast.LENGTH_SHORT).show()
                    activity?.finish()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldOk, contentColor = Color.Black),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("ATENDER (VIVA VOZ)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    withAnswerPermission {
                        runCatching { telecom?.endCall() }
                        activity?.finish()
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("RECUSAR", color = LightOnDark, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    withAnswerPermission {
                        runCatching { telecom?.endCall() }
                        val e164 = number
                        if (!e164.isNullOrBlank()) {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    EngineProvider.database.phoneListDao().upsertBlocked(
                                        BlockedEntity(
                                            e164 = e164,
                                            label = "Bloqueado na tela de chamada",
                                            createdAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        }
                        Toast.makeText(context, "Número bloqueado.", Toast.LENGTH_SHORT).show()
                        activity?.finish()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = RedBlock, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("BLOQUEAR", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = { activity?.finish() }) {
            Text("Ocultar (a chamada continua tocando)", color = ZincNeutral, fontSize = 12.sp)
        }
    }
}
