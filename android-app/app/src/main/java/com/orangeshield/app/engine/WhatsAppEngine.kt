package com.orangeshield.app.engine

import com.orangeshield.app.data.AppDatabase
import java.util.Locale

/**
 * ORANGE SHIELD - ENGINE 03 · WHATSAPP GUARD
 *
 * Catálogo determinístico de golpes de WhatsApp (pt-BR), avaliado sobre o
 * texto da notificação capturada pelo NotificationListenerService.
 * Regras (determinísticas, sem rede, sem aleatoriedade):
 *
 *   (a) Phishing do código: mensagem contém "código" E algum número de 6 dígitos -> +60 (phishing_code)
 *   (b) Falso parente: ("troquei" ou "novo número") E ("pix" ou "transferi")      -> +70 (fraud)
 *   (c) Links suspeitos: http/bit.ly/tinyurl/.xyz/wa.me E ("clique"/"cadastre"/"acesse") -> +50
 *   (d) Prêmio: ("ganhou" ou "prêmio" ou "sorteio") E ("clique" ou "cadastre")    -> +55 (prize_scam)
 *   (e) Cadeia "encaminhada muitas vezes"                                         -> +20
 *   (f) PIX com pressão ("urgente"/"prazo")                                       -> +40
 *
 * Decisão: score >= 70 -> BLOCK · score >= 40 -> QUARANTINE · senão ALLOW.
 * Remetentes numéricos também passam pelo Spoof Detector (pontos sinalizados
 * são somados ao score). As chaves waBlockCodePhish / waBlockLinks /
 * waBlockForwarded desativam as regras (a), (c) e (e) respectivamente.
 */
class WhatsAppEngine(
    private val db: AppDatabase,
    private val spoofDetector: SpoofDetector
) {

    suspend fun analyze(
        rawSender: String,
        content: String,
        forwardedMany: Boolean,
        settings: EngineSettings
    ): WhatsAppAnalysis {
        val sender = normalizePhone(rawSender)
        val lower = content.lowercase(Locale.ROOT)
        val evidences = mutableListOf<EngineEvidence>()
        var score = 0
        var category = "unknown"

        fun add(label: String, points: Int) {
            if (points == 0) return
            evidences += EngineEvidence(label, points)
            score += points
        }

        // Chave mestra do Guardião WhatsApp
        if (!settings.protectionActive || !settings.waProtect) {
            return WhatsAppAnalysis(
                sender = sender,
                decision = SmsDecision.ALLOW,
                riskScore = 0,
                category = "unknown",
                evidences = emptyList(),
                reason = "Proteção WhatsApp desativada pelo usuário"
            )
        }

        // Remetente autorizado tem prioridade máxima (lista de permissões compartilhada com chamadas)
        val allowed = db.phoneListDao().findAllowed(sender)
        if (allowed != null) {
            return WhatsAppAnalysis(
                sender = sender,
                decision = SmsDecision.ALLOW,
                riskScore = 0,
                category = "personal",
                evidences = emptyList(),
                reason = if (allowed.name != null) {
                    "Remetente autorizado (${allowed.name})"
                } else {
                    "Remetente autorizado na lista de permissões"
                }
            )
        }

        // Remetente bloqueado manualmente tem prioridade máxima de bloqueio
        val blocked = db.phoneListDao().findBlocked(sender)
        if (blocked != null) {
            return WhatsAppAnalysis(
                sender = sender,
                decision = SmsDecision.BLOCK,
                riskScore = 100,
                category = "blacklisted",
                evidences = listOf(EngineEvidence("Remetente presente na lista pessoal de bloqueio", 100)),
                reason = blocked.label ?: "Remetente bloqueado manualmente"
            )
        }

        // ------------------------------------------------------------------
        // (a) Phishing do código de verificação (sequestro de conta)
        // ------------------------------------------------------------------
        if (settings.waBlockCodePhish && lower.contains("código") && SIX_DIGITS_RE.containsMatchIn(lower)) {
            add("Phishing de código de verificação (sequestro de conta)", CODE_PHISHING)
            category = "phishing_code"
        }

        // ------------------------------------------------------------------
        // (b) Falso parente: "troquei de número" + pedido de PIX/transferência
        // ------------------------------------------------------------------
        val fakeRelative = (lower.contains("troquei") || lower.contains("novo número")) &&
            (lower.contains("pix") || lower.contains("transferi"))
        if (fakeRelative) {
            add("Falso parente: novo número pedindo PIX/transferência", FAKE_RELATIVE)
            category = "fraud"
        }

        // ------------------------------------------------------------------
        // (c) Links suspeitos com chamada para ação
        // ------------------------------------------------------------------
        val hasSuspiciousLink = SUSPICIOUS_LINKS.any { lower.contains(it) }
        val callToAction = CALL_TO_ACTION.any { lower.contains(it) }
        if (settings.waBlockLinks && hasSuspiciousLink && callToAction) {
            add("Link suspeito com chamada para ação (clique/cadastre/acesse)", SUSPICIOUS_LINK)
            if (category == "unknown") category = "suspicious_link"
        }

        // ------------------------------------------------------------------
        // (d) Prêmio / sorteio com cadastro
        // ------------------------------------------------------------------
        val prize = listOf("ganhou", "prêmio", "sorteio").any { lower.contains(it) }
        val prizeCallToAction = listOf("clique", "cadastre").any { lower.contains(it) }
        if (prize && prizeCallToAction) {
            add("Promessa de prêmio/sorteio pedindo cadastro", PRIZE_SCAM)
            if (category == "unknown") category = "prize_scam"
        }

        // ------------------------------------------------------------------
        // (e) Disparo encaminhado em massa
        // ------------------------------------------------------------------
        if (settings.waBlockForwarded && forwardedMany) {
            add("Mensagem encaminhada muitas vezes (disparo em cadeia)", FORWARDED_CHAIN)
            if (category == "unknown") category = "forwarded_chain"
        }

        // ------------------------------------------------------------------
        // (f) Pressão urgente para PIX
        // ------------------------------------------------------------------
        if (lower.contains("pix") && (lower.contains("urgente") || lower.contains("prazo"))) {
            add("Pressão urgente para pagamento via PIX", PIX_PRESSURE)
            if (category == "unknown") category = "pix_pressure"
        }

        // ------------------------------------------------------------------
        // Spoof Detector aplicado a remetentes numéricos (caller ID manipulado)
        // ------------------------------------------------------------------
        val senderIsNumeric = sender.isNotEmpty() && !isAlphanumeric(sender)
        if (senderIsNumeric) {
            val spoof = spoofDetector.analyzeSpoofing(sender, settings.ownNumber)
            for (flag in spoof.checks.filter { it.status == SpoofStatus.FLAGGED }) {
                add("Spoofing: ${flag.label}", flag.points)
            }
            if (category == "unknown" && spoof.checks.any { it.status == SpoofStatus.FLAGGED }) {
                category = "spoofing"
            }
        }

        score = clampScore(score)

        val decision = when {
            score >= BLOCK_THRESHOLD -> SmsDecision.BLOCK
            score >= QUARANTINE_THRESHOLD -> SmsDecision.QUARANTINE
            else -> SmsDecision.ALLOW
        }

        val reason = when (decision) {
            SmsDecision.BLOCK -> {
                val top = evidences.take(2).joinToString("; ") { it.label }
                "Golpe provável ($score/100): ${top.ifEmpty { "conteúdo malicioso" }}"
            }
            SmsDecision.QUARANTINE -> "Suspeito ($score/100): mensagem marcada para revisão"
            SmsDecision.ALLOW -> "Sem indícios de golpe — mensagem liberada"
        }

        return WhatsAppAnalysis(
            sender = sender,
            decision = decision,
            riskScore = score,
            category = category,
            evidences = evidences.toList(),
            reason = reason
        )
    }

    companion object {
        const val BLOCK_THRESHOLD = 70
        const val QUARANTINE_THRESHOLD = 40

        const val CODE_PHISHING = 60
        const val FAKE_RELATIVE = 70
        const val SUSPICIOUS_LINK = 50
        const val PRIZE_SCAM = 55
        const val FORWARDED_CHAIN = 20
        const val PIX_PRESSURE = 40

        private val SIX_DIGITS_RE = Regex("\\d{6}")
        private val SUSPICIOUS_LINKS = listOf("http", "bit.ly", "tinyurl", ".xyz", "wa.me")
        private val CALL_TO_ACTION = listOf("clique", "cadastre", "acesse")
    }
}

/** Resultado da análise do Guardião WhatsApp. */
data class WhatsAppAnalysis(
    val sender: String,
    val decision: SmsDecision,
    val riskScore: Int,
    val category: String,
    val evidences: List<EngineEvidence>,
    val reason: String
)
