package com.orangeshield.app.chain

import android.content.Context
import android.content.SharedPreferences
import com.orangeshield.app.data.ChainBlockEntity
import com.orangeshield.app.data.ChainPendingEntity
import com.orangeshield.app.data.ReputationEntity
import com.orangeshield.app.engine.normalizePhone
import java.security.KeyPair

/**
 * ORANGE CHAIN — nó local completo.
 *
 * Cada aparelho é um nó da rede: mantém a cadeia inteira, valida cada bloco
 * (consenso em [OrangeChain.validateChain]) e espelha as denúncias aceitas na
 * reputação local — é isso que faz o número bloqueado por um usuário aparecer
 * restrito nos outros aparelhos.
 *
 * Chaves: par EC P-256 gerado no aparelho e persistido; reporterId é
 * pseudônimo (hash da chave pública) — nenhuma informação pessoal sobe.
 */
object ChainNode {

    private const val PREFS = "orange_chain_prefs"
    private const val KEY_LAST_SYNC = "lastSyncAt"
    private const val KEY_LAST_HEIGHT = "lastHeight"

    /** Ponto de entrada: usuário bloqueou um número de verdade. */
    suspend fun onUserBlocked(context: Context, e164: String) {
        val normalized = normalizePhone(e164)
        if (normalized.isBlank()) return
        val db = com.orangeshield.app.data.DatabaseProvider.getInstance(context)
        db.chainPendingDao().insert(
            ChainPendingEntity(e164 = normalized, classification = "blocked", createdAt = System.currentTimeMillis())
        )
    }

    /** Id pseudônimo do nó (8 hex) — mostrado na UI. */
    fun nodeId(context: Context): String =
        ChainCrypto.reporterIdOf(keyPair(context).let { ChainCrypto.pubKeyBase64(it) }).take(8)

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun keyPair(context: Context): KeyPair =
        ChainCrypto.loadOrCreateKeyPair(prefs(context))

    /** Monta um bloco local assinado com as denúncias pendentes (ou null se nada pendente). */
    suspend fun buildLocalBlock(context: Context): ChainBlock? {
        val db = com.orangeshield.app.data.DatabaseProvider.getInstance(context)
        val pending = db.chainPendingDao().listAll()
        if (pending.isEmpty()) return null

        val kp = keyPair(context)
        val pubB64 = ChainCrypto.pubKeyBase64(kp)
        val reporterId = ChainCrypto.reporterIdOf(pubB64)
        val now = System.currentTimeMillis()

        val entries = pending.map { p ->
            val e = ChainEntry(
                e164 = p.e164,
                classification = p.classification,
                reportedAt = p.createdAt,
                reporterId = reporterId,
                pubKey = pubB64,
                signature = ""
            )
            e.copy(signature = ChainCrypto.sign(kp, ChainCrypto.entryCanonical(e)))
        }

        val tip = localHeight(context)
        val prevHash = if (tip < 0) OrangeChain.GENESIS_HASH else localBlockByIndex(context, tip)!!.hash()
        return ChainBlock(
            index = tip + 1,
            timestamp = now,
            prevHash = prevHash,
            merkleRoot = ChainBlock.expectedMerkleRoot(entries),
            publisher = reporterId,
            entries = entries
        )
    }

    /** Consome os pendentes após publicação bem-sucedida. */
    suspend fun clearPending(context: Context) {
        com.orangeshield.app.data.DatabaseProvider.getInstance(context).chainPendingDao().clearAll()
    }

    suspend fun pendingCount(context: Context): Int =
        com.orangeshield.app.data.DatabaseProvider.getInstance(context).chainPendingDao().listAll().size

    // ------------------------------------------------------------------
    // Cadeia local (Room)
    // ------------------------------------------------------------------

    suspend fun localHeight(context: Context): Long =
        com.orangeshield.app.data.DatabaseProvider.getInstance(context).chainBlockDao().height()

    suspend fun localChain(context: Context): List<ChainBlock> {
        val rows = com.orangeshield.app.data.DatabaseProvider.getInstance(context).chainBlockDao().listByIndexAsc()
        if (rows.isEmpty()) return listOf(OrangeChain.genesis())
        return rows.mapNotNull { runCatching { decodeBlock(it.payloadJson) }.getOrNull() }
    }

    private suspend fun localBlockByIndex(context: Context, index: Long): ChainBlock? {
        val chain = localChain(context)
        return chain.firstOrNull { it.index == index }
    }

    /** Garante o gênese local (idempotente). */
    suspend fun ensureGenesis(context: Context) {
        val db = com.orangeshield.app.data.DatabaseProvider.getInstance(context)
        if (db.chainBlockDao().height() < 0) {
            db.chainBlockDao().insertBlock(toEntity(OrangeChain.genesis()))
        }
    }

    // ------------------------------------------------------------------
    // Aplicação de cadeia remota (com validação completa antes de aceitar)
    // ------------------------------------------------------------------

    data class ApplyResult(
        val accepted: Boolean,
        val newBlocks: Int,
        val newEntries: Int,
        val height: Long,
        val error: String?
    )

    /**
     * Valida a cadeia remota inteira e aplica apenas blocos novos.
     * Nada entra na reputação sem passar pelo consenso local.
     */
    suspend fun applyRemoteChain(context: Context, remote: List<ChainBlock>): ApplyResult {
        val validation = OrangeChain.validateChain(remote)
        if (!validation.valid) {
            return ApplyResult(false, 0, 0, localHeight(context), validation.firstError)
        }
        ensureGenesis(context)
        val db = com.orangeshield.app.data.DatabaseProvider.getInstance(context)
        val localTip = db.chainBlockDao().height()
        val fresh = remote.filter { it.index > localTip }
        var appliedEntries = 0
        for (block in fresh) {
            db.chainBlockDao().insertBlock(toEntity(block))
            appliedEntries += block.entries.size
            block.entries.forEach { mirrorIntoReputation(db, it) }
        }
        prefs(context).edit()
            .putLong(KEY_LAST_HEIGHT, validation.height)
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
        return ApplyResult(true, fresh.size, appliedEntries, validation.height, null)
    }

    /**
     * Espelha uma denúncia validada na reputação local — efeito real na triagem:
     * reportCount acumula (peso REPORTS do motor), fraude confirmada bloqueia.
     */
    private suspend fun mirrorIntoReputation(
        db: com.orangeshield.app.data.AppDatabase,
        entry: ChainEntry
    ) {
        val dao = db.reputationDao()
        val existing = dao.findByE164(entry.e164)
        val fraud = entry.classification == "fraud_confirmed"
        val riskBoost = when (entry.classification) {
            "fraud_confirmed" -> 100
            "spam" -> 55
            "nuisance" -> 45
            else -> 40
        }
        val newCount = (existing?.reportCount ?: 0) + 1
        val newScore = maxOf(existing?.riskScore ?: 0, riskBoost)
        dao.upsert(
            ReputationEntity(
                e164 = entry.e164,
                riskScore = newScore,
                classification = when {
                    fraud -> "golpe_confirmado"
                    existing?.classification in setOf("robocall", "telemarketing", "collector", "verified_business", "nuisance") -> existing!!.classification
                    else -> entry.classification
                },
                reportCount = newCount,
                isConfirmedFraud = fraud || (existing?.isConfirmedFraud ?: false),
                confidence = maxOf(existing?.confidence ?: 0.5, 0.7),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    // ------------------------------------------------------------------
    // Codec JSON (ChainCodec — JVM-safe, compartilhado com os testes)
    // ------------------------------------------------------------------

    fun encodeBlock(block: ChainBlock): String = ChainCodec.encodeBlock(block)

    fun decodeBlock(json: String): ChainBlock = ChainCodec.decodeBlock(json)

    fun toEntity(block: ChainBlock): ChainBlockEntity = ChainBlockEntity(
        index = block.index,
        hash = block.hash(),
        prevHash = block.prevHash,
        merkleRoot = block.merkleRoot,
        timestamp = block.timestamp,
        publisher = block.publisher,
        payloadJson = encodeBlock(block),
        receivedAt = System.currentTimeMillis()
    )

    fun lastSyncAt(context: Context): Long = prefs(context).getLong(KEY_LAST_SYNC, 0L)
}
