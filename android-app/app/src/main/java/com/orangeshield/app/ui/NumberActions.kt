package com.orangeshield.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orangeshield.app.EngineProvider
import com.orangeshield.app.data.AllowedEntity
import com.orangeshield.app.data.BlockedEntity
import kotlinx.coroutines.launch

/**
 * ORANGE SHIELD — ações REAIS de bloqueio/liberação sobre um número.
 *
 * Reflete o estado atual das listas locais (bloqueados/autorizados) e aplica
 * mudanças imediatas no banco que o Call Guard consulta a cada chamada.
 * Bloquear aqui = a próxima chamada deste número é RECUSADA pelo triage
 * (lista negra tem prioridade máxima no motor). Desbloquear remove da lista.
 * Sem simulação: cada botão grava/deleta no Room e confirma com Toast.
 */
@Composable
fun NumberActionButtons(e164: String, modifier: Modifier = Modifier) {
    if (e164.isBlank()) return
    val dao = EngineProvider.database.phoneListDao()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val blockedEntry by remember(e164) { dao.observeBlocked(e164) }
        .collectAsStateWithLifecycle(initialValue = null)
    val allowedEntry by remember(e164) { dao.observeAllowed(e164) }
        .collectAsStateWithLifecycle(initialValue = null)

    val isBlocked = blockedEntry != null
    val isAllowed = allowedEntry != null

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isBlocked) {
            Button(
                onClick = {
                    scope.launch {
                        dao.deleteBlocked(e164)
                        Toast.makeText(
                            context,
                            "Desbloqueado: $e164 — o número volta a tocar normalmente.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ZincNeutral,
                    contentColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("DESBLOQUEAR", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            if (isAllowed) {
                Button(
                    onClick = {
                        scope.launch {
                            dao.deleteAllowed(e164)
                            Toast.makeText(
                                context,
                                "Autorização removida: $e164 volta a ser triado pelo motor.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("REMOVER LIBERAÇÃO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        } else {
            Button(
                onClick = {
                    scope.launch {
                        // Bloquear vence autorização: remove da lista branca e grava na negra.
                        dao.deleteAllowed(e164)
                        dao.upsertBlocked(
                            BlockedEntity(
                                e164 = e164,
                                label = "Bloqueado pelo usuário",
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        // ORANGE CHAIN: enfileira denúncia assinada para a rede (se compartilhamento ativo)
                        if (EngineProvider.settingsStore.current.chainShare) {
                            runCatching { com.orangeshield.app.chain.ChainNode.onUserBlocked(context, e164) }
                        }
                        Toast.makeText(
                            context,
                            "BLOQUEADO: $e164 — as próximas chamadas deste número serão recusadas.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = RedBlock,
                    contentColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("BLOQUEAR", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            if (isAllowed) {
                Button(
                    onClick = {
                        scope.launch {
                            dao.deleteAllowed(e164)
                            Toast.makeText(
                                context,
                                "Autorização removida: $e164 volta a ser triado pelo motor.",
                                Toast.LENGTH_LONG
                            ).show()
                            }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("REMOVER LIBERAÇÃO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            dao.deleteBlocked(e164)
                            dao.upsertAllowed(
                                AllowedEntity(
                                    e164 = e164,
                                    name = null,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            Toast.makeText(
                                context,
                                "LIBERADO: $e164 — sempre atendido, com prioridade máxima.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = EmeraldOk
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("LIBERAR", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
