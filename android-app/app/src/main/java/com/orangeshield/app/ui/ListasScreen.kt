package com.orangeshield.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orangeshield.app.EngineProvider
import com.orangeshield.app.data.AllowedEntity
import com.orangeshield.app.data.BlockedEntity
import com.orangeshield.app.engine.formatPhone
import com.orangeshield.app.engine.normalizePhone
import kotlinx.coroutines.launch

/**
 * ORANGE SHIELD — aba Listas.
 * Duas abas: Bloqueados (lista negra pessoal, prioridade de bloqueio) e
 * Permitidos (lista branca, prioridade máxima de liberação). As duas listas
 * são compartilhadas pelo Call Guard e pelo Guardião WhatsApp.
 */
@Composable
fun ListasScreen() {
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Bloqueados") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Permitidos") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Sugestões") })
        }
        if (tab == 0) {
            BlockedList()
        } else if (tab == 1) {
            AllowedList()
        } else {
            HistorySuggestions()
        }
    }
}

@Composable
private fun BlockedList() {
    val dao = EngineProvider.database.phoneListDao()
    val scope = rememberCoroutineScope()
    val entries by dao.listBlocked().collectAsStateWithLifecycle(initialValue = emptyList())
    var number by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = number,
            onValueChange = { number = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Número") }
        )
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Rótulo (opcional)") }
        )
        Button(
            onClick = {
                val e164 = normalizePhone(number)
                if (e164.isNotEmpty()) {
                    scope.launch {
                        dao.upsertBlocked(
                            BlockedEntity(
                                e164 = e164,
                                label = label.ifBlank { null },
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        number = ""
                        label = ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Adicionar à lista de bloqueio")
        }
        if (entries.isEmpty()) {
            EmptyHint("Nenhum número bloqueado manualmente.")
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(entries, key = { it.e164 }) { entry ->
                ListRow(
                    title = formatPhone(entry.e164),
                    subtitle = entry.label ?: "Bloqueio manual",
                    deleteDescription = "Remover da lista de bloqueio",
                    onDelete = { scope.launch { dao.deleteBlocked(entry.e164) } }
                )
            }
        }
    }
}

@Composable
private fun AllowedList() {
    val dao = EngineProvider.database.phoneListDao()
    val scope = rememberCoroutineScope()
    val entries by dao.listAllowed().collectAsStateWithLifecycle(initialValue = emptyList())
    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = number,
            onValueChange = { number = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Número") }
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome (opcional)") }
        )
        Button(
            onClick = {
                val e164 = normalizePhone(number)
                if (e164.isNotEmpty()) {
                    scope.launch {
                        dao.upsertAllowed(
                            AllowedEntity(
                                e164 = e164,
                                name = name.ifBlank { null },
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        number = ""
                        name = ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Adicionar à lista de autorizados")
        }
        if (entries.isEmpty()) {
            EmptyHint("Nenhum número autorizado.")
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(entries, key = { it.e164 }) { entry ->
                ListRow(
                    title = formatPhone(entry.e164),
                    subtitle = entry.name ?: "Contato autorizado",
                    deleteDescription = "Remover da lista de autorizados",
                    onDelete = { scope.launch { dao.deleteAllowed(entry.e164) } }
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(message: String) {
    Text(
        text = message,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ListRow(
    title: String,
    subtitle: String,
    deleteDescription: String,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = deleteDescription
                )
            }
        }
    }
}


@Composable
private fun HistorySuggestions() {
    val dao = EngineProvider.database.historyCandidateDao()
    val phoneDao = EngineProvider.database.phoneListDao()
    val scope = rememberCoroutineScope()
    val candidates by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Sugestões da varredura", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Números importados do histórico do aparelho. Nenhum deles é bloqueado automaticamente.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (candidates.isEmpty()) {
            item { EmptyHint("Faça a varredura na aba Radar para preencher esta lista.") }
        }
        items(candidates, key = { it.e164 }) { item ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(formatPhone(item.e164), fontWeight = FontWeight.SemiBold)
                    Text(item.suggestedLabel, color = OrangePrimary, fontSize = 12.sp)
                    Text(
                        "${item.totalCalls} chamadas · ${item.missedCalls} perdidas · ${item.blockedCalls} já marcadas como bloqueadas no aparelho",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    phoneDao.upsertBlocked(BlockedEntity(item.e164, item.suggestedLabel, System.currentTimeMillis()))
                                    dao.markReviewed(item.e164)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Bloquear") }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    phoneDao.upsertAllowed(AllowedEntity(item.e164, null, System.currentTimeMillis()))
                                    dao.markReviewed(item.e164)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Permitir") }
                    }
                }
            }
        }
    }
}
