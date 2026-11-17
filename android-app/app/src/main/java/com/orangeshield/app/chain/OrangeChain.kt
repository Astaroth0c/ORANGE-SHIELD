package com.orangeshield.app.chain

/**
 * ORANGE CHAIN — modelos da cadeia comunitária de números restritos.
 *
 * Estrutura real de blockchain: cada bloco referencia o hash do anterior,
 * compacta denúncias assinadas por aparelhos (Merkle root) e é validado
 * por TODOS os nós antes de afetar a triagem local.
 *
 * Fluxo real sem servidor:
 *  1. Usuário bloqueia um número → entrada assinada (ECDSA P-256) na fila.
 *  2. Bloco local é montado e publicado (issue assinada → CI comunitário
 *     empacota em community/chain.json) e/ou trocado direto por mesh local.
 *  3. Todo nó baixa a cadeia, valida (gênese, encadeamento, Merkle,
 *     assinaturas) e só então espelha na reputação local — o número passa
 *     a ser bloqueado/alertado nos outros aparelhos.
 */
data class ChainEntry(
    val e164: String,
    val classification: String,   // blocked | spam | fraud_confirmed | nuisance
    val reportedAt: Long,
    val reporterId: String,       // SHA-256 da pubkey, 16 hex
    val pubKey: String,           // Base64 X.509
    val signature: String         // Base64 DER (ECDSA SHA256withECDSA)
)

data class ChainBlock(
    val index: Long,
    val timestamp: Long,
    val prevHash: String,
    val merkleRoot: String,
    val publisher: String,        // ORANGE-GENESIS | ORANGE-CI | reporterId (bloco local)
    val entries: List<ChainEntry>
) {
    /** String canônica do cabeçalho do bloco. */
    fun canonical(): String = "v1|$index|$timestamp|$prevHash|$merkleRoot|$publisher"

    /** Hash SHA-256 do cabeçalho — é este que encadeia a cadeia. */
    fun hash(): String = ChainCrypto.sha256Hex(canonical())

    companion object {
        /** Recompõe o merkle root real das entradas (usado na validação). */
        fun expectedMerkleRoot(entries: List<ChainEntry>): String =
            ChainCrypto.merkleRoot(entries.map { ChainCrypto.entryCanonical(it) })
    }
}

/** Resultado da validação completa da cadeia por um nó. */
data class ChainValidation(
    val valid: Boolean,
    val height: Long,
    val checkedBlocks: Int,
    val checkedSignatures: Int,
    val firstError: String?
)

/**
 * ORANGE CHAIN — regras de consenso locais (validação por qualquer nó).
 */
object OrangeChain {

    const val CHAIN_VERSION = 1
    const val CHAIN_ID = "orange-chain-cambira-pr"

    /** Timestamp fixo do gênese (2026-11-17T00:00:00Z) — determinístico. */
    const val GENESIS_TIMESTAMP = 1794873600000L

    /** Bloco gênese: determinístico, idêntico em todos os nós e no repositório. */
    fun genesis(): ChainBlock = ChainBlock(
        index = 0,
        timestamp = GENESIS_TIMESTAMP,
        prevHash = "0".repeat(64),
        merkleRoot = ChainCrypto.merkleRoot(emptyList()),
        publisher = "ORANGE-GENESIS",
        entries = emptyList()
    )

    /** Hash canônico do gênese — todo nó e o CI exigem este valor no bloco 0. */
    val GENESIS_HASH: String by lazy { genesis().hash() }

    /** Classificações aceitas em entradas da cadeia. */
    val ACCEPTED_CLASSIFICATIONS = setOf("blocked", "spam", "fraud_confirmed", "nuisance")

    /**
     * Validação completa (consenso do nó):
     *  1. bloco 0 = gênese canônica
     *  2. index contíguo e prevHash = hash do anterior
     *  3. merkleRoot confere com as entradas
     *  4. cada entrada: formato válido + reporterId = hash da pubkey + assinatura ECDSA válida
     */
    fun validateChain(blocks: List<ChainBlock>): ChainValidation {
        if (blocks.isEmpty()) return ChainValidation(false, -1, 0, 0, "cadeia vazia")
        val genesisBlock = blocks.first()
        if (genesisBlock.index != 0L || genesisBlock.hash() != GENESIS_HASH) {
            return ChainValidation(false, -1, 1, 0, "bloco 0 não é o gênese canônico")
        }
        var prevHash = genesisBlock.hash()
        var signatures = 0
        for (i in 1 until blocks.size) {
            val block = blocks[i]
            if (block.index != i.toLong()) {
                return ChainValidation(false, prevHeight(i), i, signatures, "índice descontínuo no bloco ${block.index}")
            }
            if (block.prevHash != prevHash) {
                return ChainValidation(false, prevHeight(i), i, signatures, "prevHash divergente no bloco ${block.index}")
            }
            if (block.merkleRoot != ChainBlock.expectedMerkleRoot(block.entries)) {
                return ChainValidation(false, prevHeight(i), i, signatures, "merkleRoot inválido no bloco ${block.index}")
            }
            for (entry in block.entries) {
                if (entry.e164.isBlank() || entry.classification !in ACCEPTED_CLASSIFICATIONS) {
                    return ChainValidation(false, prevHeight(i), i, signatures, "entrada malformada no bloco ${block.index}")
                }
                if (ChainCrypto.reporterIdOf(entry.pubKey) != entry.reporterId) {
                    return ChainValidation(false, prevHeight(i), i, signatures, "reporterId divergente no bloco ${block.index}")
                }
                if (!ChainCrypto.verify(entry.pubKey, ChainCrypto.entryCanonical(entry), entry.signature)) {
                    return ChainValidation(false, prevHeight(i), i, signatures, "assinatura inválida no bloco ${block.index}")
                }
                signatures++
            }
            prevHash = block.hash()
        }
        return ChainValidation(true, blocks.last().index, blocks.size, signatures, null)
    }

    private fun prevHeight(i: Int): Long = if (i == 0) 0L else (i - 1).toLong()
}
