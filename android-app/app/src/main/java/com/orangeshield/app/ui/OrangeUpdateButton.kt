package com.orangeshield.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.orangeshield.app.update.OrangeRelease
import com.orangeshield.app.update.OrangeUpdateManager
import com.orangeshield.app.update.UpdateCheck
import kotlinx.coroutines.launch

@Composable
fun OrangeUpdateButton() {
    val context = LocalContext.current
    val manager = remember { OrangeUpdateManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<OrangeRelease?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var dialogOpen by remember { mutableStateOf(false) }

    suspend fun checkForUpdates(showDialog: Boolean) {
        checking = true
        downloading = false
        release = null
        if (showDialog) {
            message = "Consultando as versões publicadas no GitHub…"
            dialogOpen = true
        }
        runCatching { manager.check() }
            .onSuccess { result ->
                when (result) {
                    UpdateCheck.UpToDate -> {
                        if (showDialog) message = "Você já está usando a versão mais recente disponível."
                    }
                    is UpdateCheck.Available -> {
                        release = result.release
                        message = "Nova versão ${result.release.version} disponível."
                        dialogOpen = true
                    }
                }
            }
            .onFailure {
                if (showDialog) message = it.message ?: "Não foi possível verificar atualizações."
            }
        checking = false
    }

    LaunchedEffect(Unit) {
        checkForUpdates(showDialog = false)
    }

    androidx.compose.material3.TextButton(
        onClick = { scope.launch { checkForUpdates(showDialog = true) } },
        enabled = !checking && !downloading
    ) {
        Text(if (checking) "VERIFICANDO" else "ATUALIZAR")
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { if (!checking && !downloading) dialogOpen = false },
            title = { Text("ORANGE UPDATE") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (checking || downloading) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator()
                            Text(if (checking) "Verificando versão…" else "Baixando e validando o APK…")
                        }
                    }
                    Text(message.orEmpty())
                    release?.let {
                        Text("Versão: ${it.version}")
                        if (it.notes.isNotBlank()) {
                            Text("Notas da versão:")
                            Text(it.notes.take(1800))
                        }
                        Text("O arquivo será conferido por SHA-256 antes de abrir o instalador do Android.")
                    }
                }
            },
            confirmButton = {
                if (release != null && !checking && !downloading) {
                    Button(onClick = {
                        val selected = release ?: return@Button
                        downloading = true
                        message = "Baixando a versão ${selected.version}…"
                        scope.launch {
                            runCatching { manager.downloadAndPromptInstall(selected) }
                                .onSuccess { dialogOpen = false }
                                .onFailure { message = it.message ?: "Não foi possível iniciar a instalação." }
                            downloading = false
                        }
                    }) { Text("BAIXAR E INSTALAR") }
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }, enabled = !checking && !downloading) {
                    Text("FECHAR")
                }
            }
        )
    }
}
