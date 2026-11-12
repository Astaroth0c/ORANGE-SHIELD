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
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
 */
@Composable
fun WhatsAppScreen() {
    val context = LocalContext.current
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

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                            fontSize = 13.sp
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
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                )
                            } catch (_: Exception) {
                                // Sem handler para as configurações de listener neste aparelho.
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Abrir configurações de acesso")
                    }
                    Text(
                        text = "Limitação: o WhatsApp não oferece API pública para bloqueio automático. O app classifica a mensagem, alerta e abre o chat em wa.me para o bloqueio manual.",
                        fontSize = 11.sp,
                        color = AmberWarn
                    )
                }
            }
        }

        Text(
            text = "Histórico recente (${events.size})",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (events.isEmpty()) {
            Text(
                text = "Nenhuma mensagem analisada ainda. Conceda o acesso a notificações e aguarde mensagens do WhatsApp.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(events, key = { it.id }) { event ->
                WhatsAppEventRow(event = event, timeFormat = timeFormat)
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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
                        fontWeight = FontWeight.Bold
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
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = timeFormat.format(Date(event.occurredAt)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = event.reason,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
