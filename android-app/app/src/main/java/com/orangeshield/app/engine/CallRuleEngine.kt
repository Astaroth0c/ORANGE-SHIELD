package com.orangeshield.app.engine

import com.orangeshield.app.data.AppDatabase
import com.orangeshield.app.data.CallEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * ORANGE SHIELD - ENGINE 01 · CALL GUARD
 *
 * SmartCall Engine: triagem de chamadas local e baseada em evidências.
 * Porta fiel de src/lib/engine/call-engine.ts do console web. Fluxo:
 *   1. Normalizar número
 *   2. Proteções prioritárias (autorizados)                    -> ALLOW
 *   3. Bloqueios explícitos (lista negra, fraude confirmada)   -> BLOCK
 *   4. Pontuação de risco (spoofing, reputação, denúncias, ...)
 *   5. Decisão por limiares (allow / warn / silence / block)
 *   6. Registrar decisão + evidências
 *
 * Referência de pontuação (spec §3):
 *   Lista negra pessoal            100
 *   Denunciado como golpe          100
 *   Prefixo 0303                   +90
 *   Classificação telemarketing    +90
 *   Associação robocall            +95
 *   Denúncias consistentes         +60
 *   Disparo em massa               +50
 *   Empresa verificada             -40
 *   Contato conhecido              liberação prioritária
 */
class CallRuleEngine(
    private val db: AppDatabase,
    private val spoofDetector: SpoofDetector
) {

    /**
     * Analisa uma chamada com o motor completo.
     *
     * @param record quando false, a análise é somente leitura (usada pela aba
     *   Análise da UI): nada é gravado em call_events, portanto a análise manual
     *   NÃO polui o histórico real de triagens nem as estatísticas do Painel.
     */
    suspend fun analyzeIncomingCall(
        rawNumber: String,
        settings: EngineSettings,
        record: Boolean = true,
        callerVerificationStatus: String? = null
    ): CallAnalysis {
        val normalization = normalizePhoneDetailed(rawNumber)
        val e164 = normalization.e164
        val steps = mutableListOf<EngineStep>()
        val evidences = mutableListOf<EngineEvidence>()
        var score = 0
        var category = "unknown"

        steps += EngineStep("Identificador recebido (original)", rawNumber.ifEmpty { "não fornecido" })
        steps += EngineStep("Número canônico (E.164)", e164.ifEmpty { "inválido" })
        steps += EngineStep("Validação do plano telefônico", if (normalization.isValid) "Estrutura validada (${normalization.source})" else normalization.note ?: "Não foi possível validar o formato")
        when (callerVerificationStatus) {
            "PASSED" -> steps += EngineStep(
                "Autenticação da operadora (STIR/SHAKEN)",
                "PASSED — a rede validou a origem apresentada; isso não garante que a chamada seja segura"
            )
            "FAILED" -> steps += EngineStep(
                "Autenticação da operadora (STIR/SHAKEN)",
                "FAILED — a verificação da origem falhou; é um sinal forte de possível caller ID falsificado"
            )
            "NOT_VERIFIED" -> steps += EngineStep(
                "Autenticação da operadora (STIR/SHAKEN)",
                "NOT_VERIFIED — a rede não forneceu confirmação; resultado desconhecido, não é prova de fraude"
            )
            else -> steps += EngineStep(
                "Autenticação da operadora (STIR/SHAKEN)",
                "Não disponível neste aparelho/rede ou nesta chamada"
            )
        }

        // Chave mestra de proteção: motor inativo -> tudo passa.
        if (!settings.protectionActive) {
            steps += EngineStep("Motor de proteção", "Desativado — chamada liberada")
            return CallAnalysis(
                e164 = e164,
                decision = CallDecision.ALLOW,
                riskScore = 0,
                category = "unknown",
                evidences = emptyList(),
                steps = steps.toList(),
                reason = "Proteção desativada pelo usuário",
                ruleVersion = settings.ruleVersion,
            phoneNormalization = normalization,
            callerVerificationStatus = callerVerificationStatus
            )
        }

        val international = isInternational(e164)

        // ------------------------------------------------------------------
        // 1. Proteções prioritárias — nunca bloqueadas (spec §10.1)
        // ------------------------------------------------------------------
        val allowed = db.phoneListDao().findAllowed(e164)
        if (allowed != null) {
            steps += EngineStep(
                "Lista de autorizados",
                if (allowed.name != null) "Liberado (${allowed.name}) — prioridade máxima"
                else "Liberado — prioridade máxima"
            )
            return CallAnalysis(
                e164 = e164,
                decision = CallDecision.ALLOW,
                riskScore = 0,
                category = "authorized",
                evidences = emptyList(),
                steps = steps.toList(),
                reason = "Número autorizado na lista de permissões",
                ruleVersion = settings.ruleVersion,
            phoneNormalization = normalization,
            callerVerificationStatus = callerVerificationStatus
            )
        }
        steps += EngineStep("Lista de autorizados", "Não está autorizado")

        // ------------------------------------------------------------------
        // 2. Bloqueios explícitos
        // ------------------------------------------------------------------
        val blocked = db.phoneListDao().findBlocked(e164)
        if (blocked != null) {
            steps += EngineStep("Lista pessoal de bloqueio", "Presente na lista negra")
            return CallAnalysis(
                e164 = e164,
                decision = CallDecision.BLOCK,
                riskScore = 100,
                category = "blacklisted",
                evidences = listOf(EngineEvidence("Número presente na lista pessoal de bloqueio", 100)),
                steps = steps.toList(),
                reason = blocked.label ?: "Número bloqueado manualmente",
                ruleVersion = settings.ruleVersion,
            phoneNormalization = normalization,
            callerVerificationStatus = callerVerificationStatus
            )
        }
        steps += EngineStep("Lista pessoal de bloqueio", "Sem bloqueio manual")

        // ------------------------------------------------------------------
        // 3. Base local de reputação
        // ------------------------------------------------------------------
        val reputation = db.reputationDao().findByE164(e164)

        if (reputation != null && reputation.isConfirmedFraud) {
            steps += EngineStep("Base de reputação", "Golpe confirmado — bloqueio direto")
            return CallAnalysis(
                e164 = e164,
                decision = CallDecision.BLOCK,
                riskScore = 100,
                category = "fraud",
                evidences = listOf(EngineEvidence("Número denunciado como golpe (confirmado)", 100)),
                steps = steps.toList(),
                reason = "Golpe confirmado na base de reputação",
                ruleVersion = settings.ruleVersion,
            phoneNormalization = normalization,
            callerVerificationStatus = callerVerificationStatus
            )
        }
        steps += EngineStep(
            "Base de reputação",
            if (reputation != null) {
                "Classificação: ${reputation.classification} (risco ${reputation.riskScore})"
            } else {
                "Número desconhecido"
            }
        )

        // ------------------------------------------------------------------
        // 4. Pontuação de risco
        // ------------------------------------------------------------------
        fun add(label: String, points: Int) {
            if (points == 0) return
            evidences += EngineEvidence(label, points)
            score += points
        }

        when (callerVerificationStatus) {
            "FAILED" -> add("A operadora não validou a origem do identificador (STIR/SHAKEN FAILED)", 45)
            "PASSED" -> add("Origem do identificador validada pela rede (STIR/SHAKEN PASSED)", -10)
        }

        // ------------------------------------------------------------------
        // 4a. Spoof Detector — manipulação de caller ID (Engine 04)
        // ------------------------------------------------------------------
        val spoofFlags = mutableListOf<SpoofFlag>()
        if (settings.spoofProtection && normalization.isValid) {
            val spoof = spoofDetector.analyzeSpoofing(e164, settings.ownNumber)
            val flagged = spoof.checks.filter { it.status == SpoofStatus.FLAGGED }
            spoofFlags += flagged.map { SpoofFlag(code = it.code, label = it.label, points = it.points) }
            if (flagged.isNotEmpty()) {
                for (flag in flagged) add(flag.label, flag.points)
                if (category == "unknown" && spoof.spoofScore >= 25) category = "spoofing"
                steps += EngineStep(
                    "Detector de spoofing",
                    "${flagged.size} flag(s) — score de spoofing ${spoof.spoofScore}/100 (${spoof.verdictLabel})"
                )
            } else {
                steps += EngineStep(
                    "Detector de spoofing",
                    if (spoof.checks.any { it.status == SpoofStatus.INFO }) {
                        "Checagem parcial — caller ID sem indícios de manipulação"
                    } else {
                        "Caller ID íntegro — todas as checagens passaram"
                    }
                )
            }
        } else if (settings.spoofProtection) {
            steps += EngineStep("Detector de spoofing", "Pontuação suspensa: número não validado; evitar falso positivo por erro de formatação")
        }

        // Prefixo 0303 da Anatel (telemarketing)
        if (e164.startsWith("+550303")) {
            if (settings.blockTelemarketing) {
                add("Prefixo 0303 — telemarketing (Anatel)", PREFIX_0303)
                category = "telemarketing"
            } else {
                steps += EngineStep("Prefixo 0303", "Detectado, mas regra desativada")
            }
        }

        // Classificação da reputação
        if (reputation != null) {
            when (reputation.classification) {
                "telemarketing" -> if (settings.blockTelemarketing) {
                    add("Classificado como telemarketing", TELEMARKETING)
                    category = "telemarketing"
                }
                "robocall" -> if (settings.blockRobocalls) {
                    add("Associado a robocall (disparo automático)", ROBOCALL)
                    category = "robocall"
                }
                "collector" -> if (settings.blockCollectors) {
                    add("Cobrança automatizada", COLLECTOR)
                    category = "collector"
                }
                "verified_business" -> {
                    add("Número comercial verificado", VERIFIED_BUSINESS)
                    category = "verified_business"
                }
            }

            // Denúncias consistentes (ponderadas pela confiança, teto de 60)
            if (reputation.reportCount > 0) {
                val complaint = minOf(
                    COMPLAINT_MAX,
                    COMPLAINT_BASE + (reputation.reportCount - 1) * COMPLAINT_STEP
                )
                val confidence = if (reputation.confidence > 0.0) reputation.confidence else 0.5
                val weighted = (complaint * confidence).roundToInt()
                add(
                    "${reputation.reportCount} denúncia(s) coletiva(s) (confiança ${(reputation.confidence * 100).roundToInt()}%)",
                    weighted
                )
                if (category == "unknown") category = reputation.classification
            }
        }

        // Internacional
        if (international) {
            if (settings.blockInternational) {
                add("Chamada internacional bloqueável", INTERNATIONAL)
                category = "international"
            } else {
                steps += EngineStep("Chamada internacional", "Detectada (regra desativada)")
            }
        }

        // Shortcode de operadora
        val shortcode = Regex("^\\+55\\d{1,6}$").matchEntire(e164) != null
        if (shortcode) {
            add("Remetente/curto comercial (centrais automáticas)", SHORTCODE)
        }

        // Proteção contra insistência — chamadas repetidas na última hora
        if (settings.insistenceProtection) {
            val oneHourAgo = System.currentTimeMillis() - 60L * 60L * 1000L
            val recent = db.callEventDao().countInWindow(e164, oneHourAgo)
            if (recent >= INSISTENCE_HEAVY_COUNT) {
                add("Insistência: $recent chamadas na última hora", INSISTENCE_HEAVY)
            } else if (recent >= INSISTENCE_LIGHT_COUNT) {
                add("Insistência: $recent chamadas na última hora", INSISTENCE_LIGHT)
            }
        }

        // Política de número desconhecido (sem registro de reputação local)
        val unknown = reputation == null
        if (unknown && settings.blockUnknownNumbers) {
            add("Política: bloquear números desconhecidos", UNKNOWN_NUMBER)
            category = "unknown"
        }

        score = clampScore(score)
        steps += EngineStep("Pontuação de risco", "$score/100 — ${riskBand(score)}")

        // ------------------------------------------------------------------
        // 5. Decisão
        // ------------------------------------------------------------------
        val decision = when {
            score >= settings.blockThreshold -> CallDecision.BLOCK
            settings.silenceMode && score < settings.blockThreshold && unknown -> CallDecision.SILENCE
            score >= settings.warnThreshold -> CallDecision.WARN
            else -> CallDecision.ALLOW
        }

        steps += EngineStep(
            "Decisão do SmartCall Engine",
            when (decision) {
                CallDecision.BLOCK -> "BLOQUEAR"
                CallDecision.SILENCE -> "SILENCIAR"
                CallDecision.WARN -> "IDENTIFICAR / ALERTAR"
                CallDecision.ALLOW -> "PERMITIR"
            }
        )

        val reason = when (decision) {
            CallDecision.BLOCK -> {
                val top = evidences.take(2).joinToString("; ") { it.label }
                "Risco alto ($score/100): ${top.ifEmpty { "política do usuário" }}"
            }
            CallDecision.WARN -> "Suspeito ($score/100): ${evidences.take(2).joinToString("; ") { it.label }.ifEmpty { "sinais insuficientes para detalhar" }}"
            CallDecision.SILENCE -> "Modo silêncio total: apenas contatos autorizados tocam"
            CallDecision.ALLOW -> "Sem indícios de risco detectados — autenticidade não confirmada"
        }

        // ------------------------------------------------------------------
        // 6. Registro (trilha de auditoria) — somente para chamadas reais.
        //    A análise manual da UI usa record = false (somente leitura).
        // ------------------------------------------------------------------
        if (record) {
            recordCallEvent(
                e164 = e164,
                rawNumber = rawNumber,
                callerVerificationStatus = callerVerificationStatus,
                decision = decision,
                riskScore = score,
                category = category,
                evidences = evidences.toList(),
                reason = reason,
                spoofFlags = spoofFlags.toList(),
                settings = settings
            )
        }

        return CallAnalysis(
            e164 = e164,
            decision = decision,
            riskScore = score,
            category = category,
            evidences = evidences.toList(),
            steps = steps.toList(),
            reason = reason,
            ruleVersion = settings.ruleVersion,
            phoneNormalization = normalization,
            callerVerificationStatus = callerVerificationStatus
        )
    }

    private suspend fun recordCallEvent(
        e164: String,
        rawNumber: String,
        callerVerificationStatus: String?,
        decision: CallDecision,
        riskScore: Int,
        category: String,
        evidences: List<EngineEvidence>,
        reason: String,
        spoofFlags: List<SpoofFlag>,
        settings: EngineSettings
    ) {
        withContext(Dispatchers.IO) {
            db.callEventDao().insert(
                CallEventEntity(
                    e164 = e164,
                    rawNumber = rawNumber,
                    networkVerificationStatus = callerVerificationStatus,
                    decision = decision.name,
                    riskScore = riskScore,
                    category = category.ifEmpty { null },
                    evidencesJson = evidencesToJson(evidences),
                    spoofFlagsJson = spoofFlagsToJson(spoofFlags),
                    reason = reason,
                    ruleVersion = settings.ruleVersion,
                    occurredAt = System.currentTimeMillis()
                )
            )
        }
    }

    companion object {
        /** Tabela de pesos (parâmetros ajustáveis) — idêntica a CALL_WEIGHTS do TS. */
        const val PREFIX_0303 = 90
        const val TELEMARKETING = 90
        const val ROBOCALL = 95
        const val COLLECTOR = 45
        const val FRAUD = 100
        const val COMPLAINT_BASE = 15
        const val COMPLAINT_STEP = 15
        const val COMPLAINT_MAX = 60
        const val MASS_BURST = 50
        const val VERIFIED_BUSINESS = -40
        const val INTERNATIONAL = 100
        const val UNKNOWN_NUMBER = 100
        const val INSISTENCE_LIGHT = 30
        const val INSISTENCE_HEAVY = 50
        const val SHORTCODE = 25

        /** Limiares de insistência: chamadas nos últimos 60 minutos. */
        const val INSISTENCE_LIGHT_COUNT = 3
        const val INSISTENCE_HEAVY_COUNT = 6
    }
}
