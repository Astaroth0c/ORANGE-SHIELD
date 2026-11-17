package com.orangeshield.app.chain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair

/**
 * ORANGE CHAIN — testes do consenso local.
 * Provam de verdade: gênese determinístico, encadeamento por hash, Merkle,
 * assinatura ECDSA por nó e rejeição de cadeia adulterada.
 */
class OrangeChainTest {

    private fun signedEntry(kp: KeyPair, e164: String, classification: String = "blocked"): ChainEntry {
        val pubB64 = ChainCrypto.pubKeyBase64(kp)
        val base = ChainEntry(
            e164 = e164,
            classification = classification,
            reportedAt = 1794873600000L + 60_000,
            reporterId = ChainCrypto.reporterIdOf(pubB64),
            pubKey = pubB64,
            signature = ""
        )
        return base.copy(signature = ChainCrypto.sign(kp, ChainCrypto.entryCanonical(base)))
    }

    private fun buildChain(kp: KeyPair, entriesBlock1: List<ChainEntry>, entriesBlock2: List<ChainEntry> = emptyList()): List<ChainBlock> {
        val genesis = OrangeChain.genesis()
        val b1 = ChainBlock(
            index = 1,
            timestamp = genesis.timestamp + 1,
            prevHash = genesis.hash(),
            merkleRoot = ChainBlock.expectedMerkleRoot(entriesBlock1),
            publisher = "ORANGE-CI",
            entries = entriesBlock1
        )
        val b2 = ChainBlock(
            index = 2,
            timestamp = genesis.timestamp + 2,
            prevHash = b1.hash(),
            merkleRoot = ChainBlock.expectedMerkleRoot(entriesBlock2),
            publisher = "ORANGE-CI",
            entries = entriesBlock2
        )
        return listOf(genesis, b1, b2)
    }

    @Test
    fun `gênese é determinístico e idêntico em qualquer nó`() {
        val g1 = OrangeChain.genesis()
        val g2 = OrangeChain.genesis()
        assertEquals(OrangeChain.GENESIS_HASH, g1.hash())
        assertEquals(g1.hash(), g2.hash())
        assertEquals(64, g1.hash().length)
        assertEquals(0L, g1.index)
        assertEquals("0".repeat(64), g1.prevHash)
    }

    @Test
    fun `merkle root é determinística e sensível ao conteúdo`() {
        val a = ChainCrypto.merkleRoot(listOf("x"))
        val b = ChainCrypto.merkleRoot(listOf("x"))
        val c = ChainCrypto.merkleRoot(listOf("y"))
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertEquals(ChainCrypto.sha256Hex("ORANGE-CHAIN-GENESIS"), ChainCrypto.merkleRoot(emptyList()))
    }

    @Test
    fun `cadeia válida com entradas assinadas passa no consenso`() {
        val kp = ChainCrypto.generateKeyPair()
        val chain = buildChain(kp, listOf(signedEntry(kp, "+5586312345678")))
        val validation = OrangeChain.validateChain(chain)
        assertTrue(validation.firstError ?: "deveria ser válida", validation.valid)
        assertEquals(2L, validation.height)
        assertEquals(1, validation.checkedSignatures)
    }

    @Test
    fun `entrada adulterada após assinatura é rejeitada`() {
        val kp = ChainCrypto.generateKeyPair()
        val entry = signedEntry(kp, "+5586312345678")
        val tampered = entry.copy(e164 = "+5511999999999") // troca do número invalida a assinatura
        val chain = buildChain(kp, listOf(tampered))
        val validation = OrangeChain.validateChain(chain)
        assertFalse(validation.valid)
        assertNotNull(validation.firstError)
    }

    @Test
    fun `assinatura forjada com outra chave é rejeitada`() {
        val kpAuthor = ChainCrypto.generateKeyPair()
        val kpFake = ChainCrypto.generateKeyPair()
        val entry = signedEntry(kpAuthor, "+5586312345678")
        // assina com a chave errada mas mantém reporterId da chave certa
        val forged = entry.copy(signature = ChainCrypto.sign(kpFake, ChainCrypto.entryCanonical(entry)))
        val chain = buildChain(kpAuthor, listOf(forged))
        val validation = OrangeChain.validateChain(chain)
        assertFalse(validation.valid)
    }

    @Test
    fun `bloco com merkleRoot divergente é rejeitado`() {
        val kp = ChainCrypto.generateKeyPair()
        val entries = listOf(signedEntry(kp, "+5586312345678"))
        val genesis = OrangeChain.genesis()
        val bad = ChainBlock(
            index = 1,
            timestamp = genesis.timestamp + 1,
            prevHash = genesis.hash(),
            merkleRoot = ChainCrypto.sha256Hex("merkle-falso"),
            publisher = "ORANGE-CI",
            entries = entries
        )
        val validation = OrangeChain.validateChain(listOf(genesis, bad))
        assertFalse(validation.valid)
        assertTrue(validation.firstError!!.contains("merkleRoot"))
    }

    @Test
    fun `encadeamento quebrado é rejeitado`() {
        val kp = ChainCrypto.generateKeyPair()
        val chain = buildChain(kp, listOf(signedEntry(kp, "+5586312345678")))
        val broken = chain.toMutableList()
        broken[2] = broken[2].copy(prevHash = "f".repeat(64))
        val validation = OrangeChain.validateChain(broken)
        assertFalse(validation.valid)
        assertTrue(validation.firstError!!.contains("prevHash"))
    }

    @Test
    fun `gênese errado no bloco 0 é rejeitado`() {
        val kp = ChainCrypto.generateKeyPair()
        val fakeGenesis = OrangeChain.genesis().copy(timestamp = 12345L)
        val validation = OrangeChain.validateChain(listOf(fakeGenesis))
        assertFalse(validation.valid)
        assertTrue(validation.firstError!!.contains("gênese"))
    }

    @Test
    fun `round-trip de codificacao de bloco preserva hash e assinaturas`() {
        val kp = ChainCrypto.generateKeyPair()
        val chain = buildChain(kp, listOf(signedEntry(kp, "+5586312345678"), signedEntry(kp, "+5511987654321", "spam")))
        val json = ChainCodec.encodeBlock(chain[1])
        val decoded = ChainCodec.decodeBlock(json)
        assertEquals(chain[1].hash(), decoded.hash())
        assertEquals(chain[1].entries, decoded.entries)
    }

    @Test
    fun `codec decodifica chain json completo`() {
        val kp = ChainCrypto.generateKeyPair()
        val chain = buildChain(kp, listOf(signedEntry(kp, "+5586312345678")))
        val body = "{\"version\":1,\"blocks\":[" + chain.joinToString(",") { ChainCodec.encodeBlock(it) } + "]}"
        val decoded = ChainCodec.decodeChain(body)
        assertEquals(3, decoded.size)
        assertEquals(OrangeChain.GENESIS_HASH, decoded[0].hash())
        assertEquals(chain[2].hash(), decoded[2].hash())
        val validation = OrangeChain.validateChain(decoded)
        assertTrue(validation.valid)
    }
}
