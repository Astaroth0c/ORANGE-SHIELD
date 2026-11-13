package com.orangeshield.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orangeshield.app.EngineProvider
import com.orangeshield.app.data.WhatsAppEventEntity
import com.orangeshield.app.engine.formatPhone
import com.orangeshield.app.notify.Notifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ORANGE SHIELD — aba WhatsApp.
 * Status do acesso a notificações, nota de limitação (bloqueio manual via
 * wa.me) e o histórico das mensagens analisadas pelo Guardião.
 *
 * Layout: um único LazyColumn rola o cartão de status junto com o histórico,
 * garantindo que nada fique cortado em telas pequenas.
 */
@Composable
fun WhatsAppScreen() {
    val context = LocalContext.current
    var showDisclosure by remember { mutableStateOf(false) }
    val dao = EngineProvider.database.whatsappEventDao()
    val events by dao.listRecent(50).collectAsStateWithLifecycle(initialValue = emptyList())

    // Recalcula o status do listener quando a tela volta ao primeiro plano.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val listenerEnabled = remember(resumeTick) { Notifier.isNotificationListenerEnabled(context) }
    val timeFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "guardiao-header") {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Guardião WhatsApp",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Acesso a notificações:",
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = if (listenerEnabled) "concedido" else "pendente",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (listenerEnabled) EmeraldOk else AmberWarn
                        )
                    }
                    Text(
                        text = "O guardião analisa as notificações do WhatsApp e do WhatsApp Business com o catálogo determinístico de golpes e registra o histórico abaixo.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { showDisclosure = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Revisar acesso e consentir")
                    }
                    TextButton(
                        onClick = {
                            context.getSharedPreferences("orange_shield_privacy", android.content.Context.MODE_PRIVATE)
                                .edit().putBoolean("whatsapp_notification_access_consent", false).apply()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Revogar consentimento de análise")
                    }
                    Text(
                        text = "Limitação: o WhatsApp não oferece API pública para bloqueio automático. O app classifica a mensagem, alerta e abre o chat em wa.me para o bloqueio manual.",
                        fontSize = 11.sp,
                        color = AmberWarn
                    )
                }
            }
        }

        item(key = "historico-titulo") {
            Text(
                text = "Histórico recente (${events.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (events.isEmpty()) {
            item(key = "historico-vazio") {
                Text(
                    text = "Nenhuma mensagem analisada ainda. Conceda o acesso a notificações e aguarde mensagens do WhatsApp.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(events, key = { it.id }) { event ->
            WhatsAppEventRow(event = event, timeFormat = timeFormat)
        }

        item(key = "espaco-final") {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("Acesso às notificações do WhatsApp") },
            text = {
                Text(
                    "O ORANGE SHIELD precisa ler o título e o texto das notificações do WhatsApp e WhatsApp Business para identificar padrões de golpe. " +
                    "A análise acontece no aparelho. O texto original da mensagem não será guardado; o histórico registra remetente, resultado, pontuação e motivo. " +
                    "O app não envia essas notificações para servidores. Você pode cancelar agora ou revogar o consentimento depois."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSharedPreferences("orange_shield_privacy", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("whatsapp_notification_access_consent", true).apply()
                    showDisclosure = false
                    try {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } catch (_: Exception) {
                        // O acesso pode ser habilitado manualmente nas configurações do Android.
                    }
                }) { Text("Concordo e abrir configurações") }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) { Text("Agora não") }
            }
        )
    }
}

@Composable
private fun WhatsAppEventRow(
    event: WhatsAppEventEntity,
    timeFormat: SimpleDateFormat
) {
    val (color, label) = when (event.decision) {
        "BLOCK" -> RedBlock to "BLOQUEADA"
        "QUARANTINE" -> AmberWarn to "QUARENTENA"
        else -> EmeraldOk to "LIBERADA"
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatPhone(event.sender),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    color = color.copy(alpha = 0.18f),
                    contentColor = color,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = event.content.take(120) + if (event.content.length > 120) "…" else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Risco ${event.riskScore}/100",
                    fontSize = 11.sp,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = timeFormat.format(Date(event.occurredAt)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = event.reason,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
