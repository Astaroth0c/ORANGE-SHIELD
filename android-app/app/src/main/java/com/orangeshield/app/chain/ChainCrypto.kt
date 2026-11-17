package com.orangeshield.app.chain

import android.content.SharedPreferences
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * ORANGE CHAIN — criptografia da cadeia comunitária.
 *
 * Cada aparelho é um nó com par de chaves EC P-256 gerado localmente.
 * Cada denúncia é assinada pela chave do aparelho; qualquer nó (e o
 * validador comunitário em CI) consegue verificar a autoria sem revelar
 * identidade (reporterId = SHA-256 da chave pública, truncado).
 * Determinístico e auditável: nada de aleatoriedade nas estruturas.
 */
object ChainCrypto {

    private const val EC_CURVE = "secp256r1"
    private const val SIGN_ALG = "SHA256withECDSA"

    fun sha256Hex(data: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * Merkle root determinística: folhas = SHA-256 do item; sobe por pares
     * concatenando hex e re-hasheando; último duplicado quando ímpar.
     * Lista vazia → hash do marcador de gênese.
     */
    fun merkleRoot(items: List<String>): String {
        if (items.isEmpty()) return sha256Hex("ORANGE-CHAIN-GENESIS")
        var level = items.map { sha256Hex(it) }
        while (level.size > 1) {
            val next = ArrayList<String>(level.size / 2 + 1)
            var i = 0
            while (i < level.size) {
                val left = level[i]
                val right = if (i + 1 < level.size) level[i + 1] else level[i]
                next.add(sha256Hex(left + right))
                i += 2
            }
            level = next
        }
        return level[0]
    }

    /** String canônica assinada de uma denúncia (idêntica no app e no validador CI). */
    fun entryCanonical(e: ChainEntry): String =
        "v1|${e.e164}|${e.classification}|${e.reportedAt}|${e.reporterId}"

    fun generateKeyPair(): KeyPair {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec(EC_CURVE))
        return gen.generateKeyPair()
    }

    fun pubKeyBase64(kp: KeyPair): String =
        Base64.getEncoder().encodeToString(kp.public.encoded)

    fun privKeyBase64(kp: KeyPair): String =
        Base64.getEncoder().encodeToString(kp.private.encoded)

    /** Identificador pseudônimo do nó: primeiros 16 hex do SHA-256 da chave pública. */
    fun reporterIdOf(pubKeyB64: String): String =
        sha256Hex(pubKeyB64).take(16)

    fun sign(kp: KeyPair, data: String): String {
        val sig = Signature.getInstance(SIGN_ALG)
        sig.initSign(kp.private)
        sig.update(data.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    fun verify(pubKeyB64: String, data: String, sigB64: String): Boolean = runCatching {
        val factory = KeyFactory.getInstance("EC")
        val pub = factory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(pubKeyB64)))
        val sig = Signature.getInstance(SIGN_ALG)
        sig.initVerify(pub)
        sig.update(data.toByteArray(Charsets.UTF_8))
        sig.verify(Base64.getDecoder().decode(sigB64))
    }.getOrDefault(false)

    /** Restaura o par de chaves persistido (ou cria um novo na primeira execução). */
    fun loadOrCreateKeyPair(prefs: SharedPreferences): KeyPair {
        val pub = prefs.getString(KEY_PUB, null)
        val priv = prefs.getString(KEY_PRIV, null)
        if (pub != null && priv != null) {
            runCatching {
                val factory = KeyFactory.getInstance("EC")
                val publicKey = factory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(pub)))
                val privateKey = factory.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(priv)))
                return KeyPair(publicKey, privateKey)
            }
        }
        val kp = generateKeyPair()
        prefs.edit()
            .putString(KEY_PUB, pubKeyBase64(kp))
            .putString(KEY_PRIV, privKeyBase64(kp))
            .apply()
        return kp
    }

    const val KEY_PUB = "chain_node_pub"
    const val KEY_PRIV = "chain_node_priv"
}
