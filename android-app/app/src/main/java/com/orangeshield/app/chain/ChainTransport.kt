package com.orangeshield.app.chain

import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.Uri
import com.orangeshield.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume

/**
 * ORANGE CHAIN — transporte da cadeia (sem servidor próprio).
 *
 * Canais REAIS de distribuição:
 *  1. PULL: cadeia publicada em community/chain.json (raw do GitHub) — leitura
 *     anônima por qualquer aparelho, validação local completa antes de aceitar.
 *  2. PUSH: bloco local assinado é publicado como issue assinada; o CI
 *     comunitário ([community/chain_validator.py]) valida e empacota.
 *  3. MESH: troca direta aparelho↔aparelho na mesma rede Wi-Fi via NSD + TCP.
 */
object ChainTransport {

    private const val CHAIN_URL =
        "https://raw.githubusercontent.com/Astaroth0c/ORANGE-SHIELD/main/community/chain.json"
    private const val ISSUES_URL =
        "https://github.com/Astaroth0c/ORANGE-SHIELD/issues/new"
    private const val MESH_SERVICE_TYPE = "_orangeshield._tcp."
    private const val MESH_PORT = 47615

    // ------------------------------------------------------------------
    // 1. PULL — sincronizar com a cadeia publicada
    // ------------------------------------------------------------------

    data class PullResult(
        val ok: Boolean,
        val newBlocks: Int,
        val newEntries: Int,
        val height: Long,
        val message: String
    )

    suspend fun pull(context: Context): PullResult = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(CHAIN_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "ORANGE-SHIELD-Node")
            if (conn.responseCode != 200) {
                return@withContext PullResult(false, 0, 0, ChainNode.localHeight(context), "Cadeia remota indisponível (HTTP ${conn.responseCode}).")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val remote = parseChainJson(body)
            val applied = ChainNode.applyRemoteChain(context, remote)
            if (applied.accepted) {
                PullResult(true, applied.newBlocks, applied.newEntries, applied.height,
                    "Cadeia validada: ${applied.height} blocos · +${applied.newEntries} denúncia(s) aceita(s).")
            } else {
                PullResult(false, 0, 0, applied.height, "Cadeia remota REJEITADA pelo consenso local: ${applied.error}")
            }
        }.getOrElse { PullResult(false, 0, 0, ChainNode.localHeight(context), "Falha de rede: ${it.message ?: "erro"}") }
    }

    fun parseChainJson(body: String): List<ChainBlock> = ChainCodec.decodeChain(body)

    // ------------------------------------------------------------------
    // 2. PUSH — publicar bloco local assinado (issue para o CI comunitário)
    // ------------------------------------------------------------------

    data class PublishResult(val block: ChainBlock?, val issueUrl: String?, val message: String)

    /**
     * Monta o bloco com as denúncias pendentes, assina com a chave do nó e
     * abre a issue pré-preenchida. O usuário confirma no GitHub (sem PAT no
     * app); o validador comunitário empacota o bloco em chain.json.
     */
    suspend fun publishLocalBlock(context: Context): PublishResult {
        ChainNode.ensureGenesis(context)
        val block = ChainNode.buildLocalBlock(context)
            ?: return PublishResult(null, null, "Nenhuma denúncia pendente para publicar.")
        val payload = "{\"chainId\":\"${OrangeChain.CHAIN_ID}\",\"nodeId\":\"${ChainNode.nodeId(context)}\",\"block\":${ChainCodec.encodeBlock(block)}}"
        val body = "Bloco assinado por nó ORANGE SHIELD para inclusão na cadeia comunitária.\n\n" +
            "```json\n$payload\n```\n\n" +
            "- chainId: ${OrangeChain.CHAIN_ID}\n" +
            "- index: ${block.index}\n" +
            "- prevHash: ${block.prevHash}\n" +
            "- entradas: ${block.entries.size} (assinadas com ECDSA P-256)\n\n" +
            "O validador comunitário (chain_validator.py) verificará encadeamento, Merkle e assinaturas antes de anexar."
        val title = URLEncoder.encode("[ORANGE CHAIN] Bloco ${block.index} do nó ${ChainNode.nodeId(context)}", "UTF-8")
        val url = "$ISSUES_URL?title=$title&body=${URLEncoder.encode(body, "UTF-8")}"
        return PublishResult(block, url, "Bloco ${block.index} montado com ${block.entries.size} denúncia(s) assinada(s). Confirme a publicação no GitHub.")
    }

    fun openIssuePage(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ------------------------------------------------------------------
    // 3. MESH — troca direta entre aparelhos na mesma rede Wi-Fi
    // ------------------------------------------------------------------

    data class MeshResult(val peers: Int, val newBlocks: Int, val message: String)

    /**
     * Sincronização mesh real: abre socket servidor, anuncia via NSD,
     * descobre outros nós ORANGE SHIELD, troca cadeias e aplica a maior
     * cadeia válida (sempre passando pelo consenso local).
     */
    suspend fun meshSync(context: Context): MeshResult = withContext(Dispatchers.IO) {
        ChainNode.ensureGenesis(context)
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: return@withContext MeshResult(0, 0, "NSD indisponível neste aparelho.")

        var newBlocksTotal = 0
        var peersSeen = 0

        // Servidor: aceita um ciclo de conexões de outros nós durante a janela.
        val server = runCatching { ServerSocket(MESH_PORT) }.getOrNull()
        val serverJob: Job? = server?.let { srv ->
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    srv.soTimeout = 12_000
                    val client = srv.accept()
                    exchange(context, client)
                }
            }
        }

        // Registro NSD para que outros nós nos descubram.
        val regListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "orangeshield-${ChainNode.nodeId(context)}"
            serviceType = MESH_SERVICE_TYPE
            port = MESH_PORT
        }
        runCatching { nsd.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, regListener) }

        // Descoberta de pares.
        val peers = mutableListOf<Pair<String, Int>>()
        val discoverListener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType == MESH_SERVICE_TYPE && !info.serviceName.contains(ChainNode.nodeId(context))) {
                    runCatching {
                        nsd.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                synchronized(peers) { peers.add(resolved.host.hostAddress to resolved.port) }
                            }
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                        })
                    }
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        runCatching { nsd.discoverServices(MESH_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoverListener) }

        // Janela de descoberta/resolução.
        withTimeoutOrNull(6_000) {
            while (synchronized(peers) { peers.isEmpty() }) delay(300)
        }
        delay(800)

        // Troca com cada par descoberto.
        synchronized(peers) { peers.toList() }.forEach { (host, port) ->
            runCatching {
                Socket().apply { connect(java.net.InetSocketAddress(host, port), 4_000) }.use { client ->
                    client.soTimeout = 6_000
                    val remoteJson = exchange(context, client)
                    if (remoteJson != null) {
                        peersSeen++
                        val remote = parseChainJson(remoteJson)
                        val applied = ChainNode.applyRemoteChain(context, remote)
                        if (applied.accepted) newBlocksTotal += applied.newBlocks
                    }
                }
            }
        }

        runCatching { nsd.unregisterService(regListener) }
        runCatching { nsd.stopServiceDiscovery(discoverListener) }
        serverJob?.cancel()
        runCatching { server?.close() }

        MeshResult(peersSeen, newBlocksTotal,
            if (peersSeen == 0) "Nenhum outro nó ORANGE SHIELD na rede local. A sincronização via repositório continua ativa."
            else "$peersSeen nó(s) na rede local · +$newBlocksTotal bloco(s) aceito(s) pelo consenso.")
    }

    /** Protocolo de troca: recebe JSON da cadeia do par, aplica e responde com a nossa. */
    private suspend fun exchange(context: Context, socket: Socket): String? {
        val reader = BufferedReader(InputStreamReader(socket.inputStream))
        val writer = PrintWriter(socket.getOutputStream(), true)
        val line = reader.readLine() ?: return null
        val applied = runCatching {
            val remote = parseChainJson(line)
            ChainNode.applyRemoteChain(context, remote)
        }.getOrNull()
        val mine = ChainNode.localChain(context).joinToString(",") { ChainCodec.encodeBlock(it) }
        writer.println("{\"blocks\":[$mine]}")
        return if (applied != null) line else null
    }
}
