package com.orangeshield.app.engine

/**
 * ORANGE SHIELD — tipos compartilhados pelos motores de proteção.
 * Espelho fiel de src/lib/engine/types.ts do console web:
 * score de risco 0-100, faixas de decisão e classificação por evidências.
 */

/** Decisões do Call Guard (Engine 01). */
enum class CallDecision { ALLOW, WARN, SILENCE, BLOCK }

/** Decisões do WhatsApp Guard (Engine 03). */
enum class SmsDecision { ALLOW, QUARANTINE, BLOCK }

/** Evidência de risco acumulada pelo motor. */
data class EngineEvidence(
    val label: String,
    val points: Int
)

/** Linha da timeline de decisão (passo → resultado). */
data class EngineStep(
    val step: String,
    val result: String
)

/** Flag de spoofing extraída do Spoof Detector (Engine 04) pelo Call Guard. */
data class SpoofFlag(
    val code: String,
    val label: String,
    val points: Int
)

/** Resultado completo da análise de uma chamada. */
data class CallAnalysis(
    val e164: String,
    val decision: CallDecision,
    val riskScore: Int,
    val category: String,
    val evidences: List<EngineEvidence>,
    val steps: List<EngineStep>,
    val reason: String,
    val ruleVersion: String,
    val phoneNormalization: PhoneNormalization? = null,
    val callerVerificationStatus: String? = null
)

/**
 * Configuração dos motores (espelho de EngineSettings do types.ts).
 * Persistida localmente via [com.orangeshield.app.SettingsStore].
 */
data class EngineSettings(
    val protectionActive: Boolean = true,
    val blockThreshold: Int = 70,
    val warnThreshold: Int = 30,
    val blockTelemarketing: Boolean = true,
    val blockRobocalls: Boolean = true,
    val blockCollectors: Boolean = true,
    val blockOperatorAds: Boolean = true,
    val blockInternational: Boolean = false,
    val blockUnknownNumbers: Boolean = false,
    val silenceMode: Boolean = false,
    val insistenceProtection: Boolean = true,
    // WhatsApp Guard (Engine 03)
    val waProtect: Boolean = true,
    val waBlockUnknown: Boolean = false,
    val waBlockLinks: Boolean = true,
    val waBlockForwarded: Boolean = true,
    val waBlockCodePhish: Boolean = true,
    val waBlockGroupInvite: Boolean = false,
    // Spoof Detector (Engine 04)
    val spoofProtection: Boolean = true,
    val ownNumber: String = "",
    val ruleVersion: String = "v2.1.0"
)

/** Padrões idênticos a DEFAULT_SETTINGS do types.ts. */
val DEFAULT_SETTINGS = EngineSettings()

/** Clamp 0-100 do score de risco. */
fun clampScore(score: Int): Int = score.coerceIn(0, 100)

/** Faixa textual de risco (types.ts). */
fun riskBand(score: Int): String = when {
    score <= 19 -> "Confiável"
    score <= 39 -> "Baixo risco"
    score <= 59 -> "Suspeito"
    score <= 79 -> "Alto risco"
    else -> "Muito alto risco"
}
