package com.orangeshield.app.engine

import com.orangeshield.app.data.AppDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToInt

/**
 * ORANGE SHIELD - ENGINE 04 · SPOOF DETECTOR
 *
 * Identificação anti-spoofing em QUATRO passos (pipeline portado de
 * src/lib/engine/spoof.ts do console web):
 *
 *   PASSO 1 · FORMATO — valida o próprio número: código de país, DDD da
 *     ANATEL, regra do nono dígito e padrões sintéticos de dígitos
 *     (repetição em massa / sequências longas) gerados por software.
 *   PASSO 2 · APRESENTAÇÃO — como o caller ID se apresenta: números locais
 *     exibidos em formato internacional inválido (ex.: DDD 00 após +55),
 *     excesso de dígitos além do plano nacional, números brasileiros
 *     mascarados atrás de códigos estrangeiros e NEIGHBOR SPOOFING
 *     (chamadas que imitam o número do próprio usuário).
 *   PASSO 3 · FREQUÊNCIA — heurísticas de ritmo sobre o histórico de
 *     triagem: rediscagem rápida Wangiri ("um toque e desliga") e ondas
 *     de disparo no mesmo DDD (disparo/spoofing em massa).
 *   PASSO 4 · REPUTAÇÃO — base local de reputação: fraude confirmada,
 *     denúncias coletivas e classificação robocall/telemarketing.
 *
 * Saída: status por passo + lista plana de checagens + score de spoofing
 * 0-100, consumido pelo Call Guard, pelo WhatsApp Guard e pelo Simulador.
 */

/** Status de uma checagem (espelho do TS: "pass" | "flagged" | "info"). */
enum class SpoofStatus(val raw: String) {
    PASS("pass"),
    FLAGGED("flagged"),
    INFO("info")
}

/** Veredito de spoofing. */
enum class SpoofVerdictKind(val raw: String) {
    CLEAN("clean"),
    SUSPICIOUS("suspicious"),
    SPOOF_LIKELY("spoof_likely")
}

data class SpoofVerdictResult(
    val verdict: SpoofVerdictKind,
    val verdictLabel: String
)

data class SpoofCheck(
    val code: String,
    val label: String,
    val detail: String,
    val points: Int,
    val status: SpoofStatus
)

data class SpoofStepMeta(
    val id: Int,
    val code: String,
    val title: String,
    val description: String
)

data class SpoofStepResult(
    val id: Int,
    val code: String,
    val title: String,
    val description: String,
    val status: SpoofStatus,
    val points: Int,
    val checks: List<SpoofCheck>
)

data class SpoofAnalysis(
    val checks: List<SpoofCheck>,
    val steps: List<SpoofStepResult>,
    val spoofScore: Int,
    val verdict: SpoofVerdictKind,
    val verdictLabel: String
)

/** Tabela de pesos (parâmetros ajustáveis) — idêntica a SPOOF_WEIGHTS do TS. */
object SpoofWeights {
    // Passo 1 · Formato
    const val INVALID_DDD = 30
    const val BAD_LENGTH = 25
    const val BAD_INTL_LENGTH = 25
    const val REPEATED_DIGITS = 25
    const val SEQUENTIAL_DIGITS = 20
    // Passo 2 · Apresentação
    const val INTERNATIONAL_ANOMALY = 35
    const val EXCESS_LENGTH = 30
    const val INTL_MASKED_BR = 25
    const val NEIGHBOR_SPOOF = 35
    // Passo 3 · Frequência
    const val RAPID_REDIAL = 40
    const val PREFIX_BURST = 30
    // Passo 4 · Reputação
    const val REPUTATION_FRAUD = 100
    const val REPUTATION_REPORTS = 40
    const val REPUTATION_CLASS = 30
}

/** DDDs atribuídos pela ANATEL (plano de numeração brasileiro). */
val VALID_DDDS: Set<Int> = setOf(
    11, 12, 13, 14, 15, 16, 17, 19, 21, 22, 24, 27, 28, 31, 32, 33, 34, 35, 37,
    38, 41, 42, 43, 44, 45, 46, 47, 48, 49, 51, 53, 54, 55, 61, 62, 63, 64, 65,
    66, 67, 68, 69, 71, 73, 74, 75, 77, 79, 81, 82, 83, 84, 85, 86, 87, 88, 89,
    91, 92, 93, 94, 95, 96, 97, 98, 99
)

/** Metadados do pipeline de quatro passos — idêntico a SPOOF_STEPS do TS. */
val SPOOF_STEPS: List<SpoofStepMeta> = listOf(
    SpoofStepMeta(
        id = 1,
        code = "FORMAT",
        title = "Passo 1 · Formato do número",
        description = "Valida a estrutura do caller ID: código de país, DDD da ANATEL, regra do nono dígito e padrões sintéticos de dígitos."
    ),
    SpoofStepMeta(
        id = 2,
        code = "PRESENTATION",
        title = "Passo 2 · Apresentação internacional",
        description = "Detecta número local exibido em formato internacional inválido, excesso de dígitos, BR mascarado atrás de código estrangeiro e neighbor spoofing."
    ),
    SpoofStepMeta(
        id = 3,
        code = "FREQUENCY",
        title = "Passo 3 · Frequência de chamadas",
        description = "Analisa o ritmo de chamadas no histórico: rediscagem rápida (Wangiri) e ondas de disparo no mesmo DDD."
    ),
    SpoofStepMeta(
        id = 4,
        code = "REPUTATION",
        title = "Passo 4 · Reputação do número",
        description = "Consulta a base local de reputação: golpe confirmado, denúncias coletivas e classificação (robocall/telemarketing)."
    )
)

/** Veredito por score — idêntico a spoofVerdict() do TS. */
fun spoofVerdict(score: Int): SpoofVerdictResult = when {
    score >= 55 -> SpoofVerdictResult(SpoofVerdictKind.SPOOF_LIKELY, "Spoofing provável")
    score >= 25 -> SpoofVerdictResult(SpoofVerdictKind.SUSPICIOUS, "Indícios de spoofing")
    else -> SpoofVerdictResult(SpoofVerdictKind.CLEAN, "Nenhum indício de spoofing")
}

/** Verdadeiro quando o caller ID não carrega dígitos (remetente alfanumérico de SMS). */
internal fun isAlphanumeric(e164: String): Boolean {
    // Espelho do TS: /\D/.test(e164.slice(1)) || e164.replace(/\D/g, "").length === 0
    val withoutPlus = if (e164.length > 1) e164.substring(1) else ""
    return NON_DIGIT_RE_PRIVATE.containsMatchIn(withoutPlus) ||
        NON_DIGIT_RE_PRIVATE.replace(e164, "").isEmpty()
}

private val NON_DIGIT_RE_PRIVATE = Regex("\\D")

/** Maior sequência do mesmo dígito (ex.: "97777777" -> 7). */
private fun maxRepeatedRun(local: String): Int {
    var best = 1
    var current = 1
    for (i in 1 until local.length) {
        current = if (local[i] == local[i - 1]) current + 1 else 1
        if (current > best) best = current
    }
    return best
}

/** Maior sequência crescente ou decrescente de dígitos (ex.: "1234567" -> 7). */
private fun maxSequentialRun(local: String): Int {
    var best = 1
    var up = 1
    var down = 1
    for (i in 1 until local.length) {
        val diff = local[i].code - local[i - 1].code
        up = if (diff == 1) up + 1 else 1
        down = if (diff == -1) down + 1 else 1
        best = maxOf(best, up, down)
    }
    return best
}

/** Maior sufixo comum entre duas strings de dígitos. */
private fun commonSuffixLength(a: String, b: String): Int {
    var i = 0
    while (i < a.length && i < b.length && a[a.length - 1 - i] == b[b.length - 1 - i]) i++
    return i
}

/** Maior prefixo comum entre duas strings de dígitos. */
private fun commonPrefixLength(a: String, b: String): Int {
    var i = 0
    while (i < a.length && i < b.length && a[i] == b[i]) i++
    return i
}

/**
 * Motor de spoofing — as quatro etapas, com acesso somente leitura ao banco
 * (não registra nada; quem grava é o Call Guard).
 */
class SpoofDetector(private val db: AppDatabase) {

    // ========================================================================
    // PASSO 1 · FORMATO — validação estrutural do caller ID
    // ========================================================================

    fun formatStepChecks(e164: String): List<SpoofCheck> {
        // Números de serviço (0303/0800/0300) usam plano de numeração
        // dedicado: o prefixo É a identificação oficial da ANATEL, então as
        // checagens de DDD/local gerariam falso positivo.
        if (isServiceNumber(e164)) {
            return listOf(
                SpoofCheck(
                    code = "NOT_BRL",
                    label = "Formato não-local",
                    detail = "Número de serviço (0303/0800/0300) — numeração dedicada",
                    points = 0,
                    status = SpoofStatus.INFO
                ),
                SpoofCheck(
                    code = "SERVICE_FORMAT",
                    label = "Formato de serviço dedicado",
                    detail = "0303/0800/0300 possuem plano próprio — checagem de DDD/local não aplicável",
                    points = 0,
                    status = SpoofStatus.INFO
                )
            )
        }

        val checks = mutableListOf<SpoofCheck>()
        val br = parseBrazilNumber(e164)

        if (br != null) {
            val ddd = br.ddd
            val local = br.local
            val dddNum = ddd.toIntOrNull() ?: 0
            val isMobileFormat = local.startsWith("9")
            val expectedLen = if (isMobileFormat) 9 else 8

            // 1a. Código de país +55 assumido pela normalização; validade do DDD
            if (dddNum !in VALID_DDDS) {
                checks += SpoofCheck(
                    code = "INVALID_DDD",
                    label = "DDD inexistente no Brasil",
                    detail = "O DDD $ddd não existe no plano de numeração da ANATEL — caller ID sintético",
                    points = SpoofWeights.INVALID_DDD,
                    status = SpoofStatus.FLAGGED
                )
            } else {
                checks += SpoofCheck(
                    code = "VALID_DDD",
                    label = "DDD $ddd válido",
                    detail = "Área de registro existente no plano da ANATEL",
                    points = 0,
                    status = SpoofStatus.PASS
                )
            }

            // 1b. Consistência de comprimento (regra do nono dígito)
            if (local.length != expectedLen) {
                val detail = if (isMobileFormat) {
                    "Celular com ${local.length} dígitos — deveria ter 9 (nono dígito ausente, formato pré-2012)"
                } else {
                    "Fixo com ${local.length} dígitos — deveria ter 8"
                }
                checks += SpoofCheck(
                    code = "BAD_LENGTH",
                    label = "Comprimento inválido",
                    detail = detail,
                    points = SpoofWeights.BAD_LENGTH,
                    status = SpoofStatus.FLAGGED
                )
            } else {
                checks += SpoofCheck(
                    code = "OK_LENGTH",
                    label = "Comprimento válido",
                    detail = "${local.length} dígitos coerentes com ${if (isMobileFormat) "celular" else "fixo"}",
                    points = 0,
                    status = SpoofStatus.PASS
                )
            }

            // 1c. Dígitos repetidos (padrões sintéticos)
            val repeated = maxRepeatedRun(local)
            if (repeated >= 6) {
                checks += SpoofCheck(
                    code = "REPEATED_DIGITS",
                    label = "Dígitos repetidos em massa",
                    detail = "Sequência de $repeated dígitos idênticos (\"...${local.take(repeated)}...\") — padrão gerado por software",
                    points = SpoofWeights.REPEATED_DIGITS,
                    status = SpoofStatus.FLAGGED
                )
            } else {
                checks += SpoofCheck(
                    code = "OK_DIGITS",
                    label = "Padrão de dígitos normal",
                    detail = "Sem repetições anormais",
                    points = 0,
                    status = SpoofStatus.PASS
                )
            }

            // 1d. Dígitos sequenciais (8+ consecutivos são praticamente impossíveis em números reais)
            val sequential = maxSequentialRun(local)
            if (sequential >= 8) {
                checks += SpoofCheck(
                    code = "SEQUENTIAL_DIGITS",
                    label = "Sequência numérica artificial",
                    detail = "Sequência de $sequential dígitos consecutivos — caller ID sintético",
                    points = SpoofWeights.SEQUENTIAL_DIGITS,
                    status = SpoofStatus.FLAGGED
                )
            } else {
                checks += SpoofCheck(
                    code = "OK_SEQ",
                    label = "Sem sequências artificiais",
                    detail = "Distribuição de dígitos natural",
                    points = 0,
                    status = SpoofStatus.PASS
                )
            }

            return checks
        }

        // Não é um número brasileiro padrão
        checks += SpoofCheck(
            code = "NOT_BRL",
            label = "Formato não-local",
            detail = when {
                isServiceNumber(e164) -> "Número de serviço (0303/0800/0300) — numeração dedicada"
                e164.startsWith("+55") -> "Número curto/comercial ou formato atípico — checagens de DDD não aplicáveis"
                else -> "Número internacional — formatação brasileira não aplicável"
            },
            points = 0,
            status = SpoofStatus.INFO
        )

        // Remetentes alfanuméricos (SMS) não têm dígitos para validar
        if (isAlphanumeric(e164)) {
            checks += SpoofCheck(
                code = "ALPHANUMERIC_SENDER",
                label = "Remetente alfanumérico",
                detail = "Sem dígitos para validar (ex.: remetente de operadora)",
                points = 0,
                status = SpoofStatus.INFO
            )
            return checks
        }

        // Internacional: validação básica de comprimento E.164 (8-15 dígitos)
        val total = Regex("\\D").replace(e164, "").length
        return if (total < 8 || total > 15) {
            checks + SpoofCheck(
                code = "BAD_INTL_LENGTH",
                label = "Comprimento internacional inválido",
                detail = "$total dígitos no total — o padrão E.164 permite entre 8 e 15",
                points = SpoofWeights.BAD_INTL_LENGTH,
                status = SpoofStatus.FLAGGED
            )
        } else {
            checks + SpoofCheck(
                code = "OK_INTL_FORMAT",
                label = "Formato internacional coerente",
                detail = "$total dígitos dentro do padrão E.164",
                points = 0,
                status = SpoofStatus.PASS
            )
        }
    }

    // ========================================================================
    // PASSO 2 · APRESENTAÇÃO — como o caller ID se apresenta
    // ========================================================================

    private fun neighborSpoofCheck(e164: String, ownNumber: String): SpoofCheck {
        val ownDigits = Regex("\\D").replace(ownNumber, "")
        val ownLocal = when {
            ownDigits.startsWith("55") && ownDigits.length >= 12 -> ownDigits.substring(4) // 55 + DDD(2) + local
            ownDigits.length >= 8 -> ownDigits.takeLast(9)
            else -> ""
        }

        if (ownLocal.isEmpty()) {
            return SpoofCheck(
                code = "OWN_UNSET",
                label = "Neighbor spoofing não avaliado",
                detail = "Configure o seu número na aba Painel para ativar esta checagem",
                points = 0,
                status = SpoofStatus.INFO
            )
        }

        val br = parseBrazilNumber(e164)
        val local = br?.local ?: ""
        val ddd = br?.ddd ?: ""

        if (local.isEmpty() || local == ownLocal) {
            return if (local.isNotEmpty()) {
                SpoofCheck(
                    code = "OK_NEIGHBOR",
                    label = "Não imita seu número",
                    detail = "Sem semelhança relevante com o seu número",
                    points = 0,
                    status = SpoofStatus.PASS
                )
            } else {
                SpoofCheck(
                    code = "NEIGHBOR_SKIP",
                    label = "Neighbor spoofing não avaliado",
                    detail = "Caller ID sem parte local comparável",
                    points = 0,
                    status = SpoofStatus.INFO
                )
            }
        }

        val sameDdd = ownDigits.startsWith("55") && ownDigits.substring(2, 4) == ddd
        val prefix = commonPrefixLength(ownLocal, local)
        val suffix = commonSuffixLength(ownLocal, local)
        val neighbor = (sameDdd && prefix >= 5) || suffix >= 7

        if (neighbor) {
            val how = if (suffix >= 7) {
                "termina com os mesmos $suffix dígitos do seu número"
            } else {
                "mesmo DDD + $prefix dígitos iniciais do seu número"
            }
            return SpoofCheck(
                code = "NEIGHBOR_SPOOF",
                label = "Neighbor spoofing (imita seu número)",
                detail = "Caller ID $how — técnica comum para passar pela desconfiança inicial",
                points = SpoofWeights.NEIGHBOR_SPOOF,
                status = SpoofStatus.FLAGGED
            )
        }

        return SpoofCheck(
            code = "OK_NEIGHBOR",
            label = "Não imita seu número",
            detail = "Sem semelhança relevante com o seu número",
            points = 0,
            status = SpoofStatus.PASS
        )
    }

    fun presentationStepChecks(e164: String, ownNumber: String): List<SpoofCheck> {
        // Remetentes alfanuméricos não têm apresentação de caller ID para analisar
        if (isAlphanumeric(e164)) {
            return listOf(
                SpoofCheck(
                    code = "PRESENTATION_SKIP",
                    label = "Apresentação não aplicável",
                    detail = "Remetente alfanumérico — sem caller ID numérico para analisar",
                    points = 0,
                    status = SpoofStatus.INFO
                )
            )
        }

        if (isServiceNumber(e164)) {
            return listOf(
                SpoofCheck(
                    code = "PRESENTATION_SKIP",
                    label = "Apresentação não aplicável",
                    detail = "Números de serviço (0303/0800/0300) não sofrem a manipulação típica de caller ID",
                    points = 0,
                    status = SpoofStatus.INFO
                ),
                neighborSpoofCheck(e164, ownNumber)
            )
        }

        val checks = mutableListOf<SpoofCheck>()

        if (e164.startsWith("+55")) {
            val national = Regex("\\D").replace(e164, "").substring(2) // dígitos após 55

            if (national.startsWith("00")) {
                // Anomalia clássica: número local exibido em formato internacional inválido
                checks += SpoofCheck(
                    code = "INTERNATIONAL_ANOMALY",
                    label = "Anomalia internacional (DDD 00 após +55)",
                    detail = "Número local apresentado em formato internacional inválido (\"+5500…\") — caller ID forjado por software",
                    points = SpoofWeights.INTERNATIONAL_ANOMALY,
                    status = SpoofStatus.FLAGGED
                )
            } else if (national.length > 11) {
                // Máximo do plano nacional: DDD(2) + celular(9) = 11 dígitos
                checks += SpoofCheck(
                    code = "EXCESS_LENGTH",
                    label = "Dígitos excedentes no plano nacional",
                    detail = "${national.length} dígitos após o código de país (máximo 11) — caller ID com dados extras forjados",
                    points = SpoofWeights.EXCESS_LENGTH,
                    status = SpoofStatus.FLAGGED
                )
            } else {
                checks += SpoofCheck(
                    code = "OK_PRESENTATION",
                    label = "Apresentação coerente",
                    detail = "Número nacional dentro do plano de numeração da ANATEL",
                    points = 0,
                    status = SpoofStatus.PASS
                )
            }
        } else {
            // Código de país estrangeiro — checa se há um número brasileiro mascarado
            val digits = Regex("\\D").replace(e164, "")
            val tail = digits.takeLast(11)
            val masked = Regex("^(\\d{2})(9\\d{8})$").matchEntire(tail)
            if (masked != null && VALID_DDDS.contains(masked.groupValues[1].toIntOrNull() ?: 0)) {
                checks += SpoofCheck(
                    code = "INTL_MASKED_BR",
                    label = "Número brasileiro mascarado como internacional",
                    detail = "Terminação \"${masked.groupValues[1]} ${masked.groupValues[2]}\" segue o padrão BR (DDD + celular) atrás de código de país estrangeiro — típico de caller ID forjado",
                    points = SpoofWeights.INTL_MASKED_BR,
                    status = SpoofStatus.FLAGGED
                )
            } else {
                checks += SpoofCheck(
                    code = "INTL_LEGIT",
                    label = "Apresentação internacional",
                    detail = "Código de país estrangeiro sem padrão brasileiro embutido",
                    points = 0,
                    status = SpoofStatus.INFO
                )
            }
        }

        // Neighbor spoofing (imita o número do próprio usuário)
        checks += neighborSpoofCheck(e164, ownNumber)

        return checks
    }

    // ========================================================================
    // PASSO 3 · FREQUÊNCIA — heurísticas de ritmo sobre o histórico de triagem
    // ========================================================================

    /** Wangiri: >= 3 triagens do mesmo número nos últimos 15 minutos. */
    suspend fun rapidRedialCheck(e164: String): SpoofCheck {
        if (isAlphanumeric(e164) || e164.length <= 9) {
            return SpoofCheck(
                code = "REDIAL_SKIP",
                label = "Frequência não aplicável",
                detail = "Remetente curto/alfanumérico — sem histórico de chamadas",
                points = 0,
                status = SpoofStatus.INFO
            )
        }

        val since = System.currentTimeMillis() - 15L * 60L * 1000L
        val recent = db.callEventDao().countInWindow(e164, since)

        return if (recent >= 3) {
            SpoofCheck(
                code = "RAPID_REDIAL",
                label = "Rediscagem rápida (padrão Wangiri)",
                detail = "$recent triagens nos últimos 15 minutos — esquema \"1 toque e desliga\" de tarifa premium",
                points = SpoofWeights.RAPID_REDIAL,
                status = SpoofStatus.FLAGGED
            )
        } else {
            SpoofCheck(
                code = "OK_REDIAL",
                label = "Sem rediscagem anormal",
                detail = "$recent triagem(ns) nos últimos 15 minutos",
                points = 0,
                status = SpoofStatus.PASS
            )
        }
    }

    /** Onda do mesmo DDD: muitos números distintos de uma área chamando dentro de 1 hora. */
    suspend fun prefixBurstCheck(e164: String): SpoofCheck {
        val br = parseBrazilNumber(e164)
            ?: return SpoofCheck(
                code = "BURST_SKIP",
                label = "Onda regional não avaliada",
                detail = "Sem DDD brasileiro identificável neste caller ID",
                points = 0,
                status = SpoofStatus.INFO
            )

        val since = System.currentTimeMillis() - 60L * 60L * 1000L
        val prefix = "+55${br.ddd}"
        val total = db.callEventDao().countByPrefixSince(prefix, since)
        val distinct = db.callEventDao().listE164ByPrefixSince(prefix, since).distinct().size

        return if (distinct >= 6 && total >= 10) {
            SpoofCheck(
                code = "PREFIX_BURST",
                label = "Onda de disparo no mesmo DDD",
                detail = "$distinct números distintos do DDD ${br.ddd} geraram $total triagens na última hora — disparo/spoofing em massa na região",
                points = SpoofWeights.PREFIX_BURST,
                status = SpoofStatus.FLAGGED
            )
        } else {
            SpoofCheck(
                code = "OK_BURST",
                label = "Sem onda regional",
                detail = "$total triagem(ns) no DDD ${br.ddd} na última hora ($distinct número(s) distinto(s))",
                points = 0,
                status = SpoofStatus.PASS
            )
        }
    }

    suspend fun frequencyStepChecks(e164: String): List<SpoofCheck> =
        listOf(rapidRedialCheck(e164), prefixBurstCheck(e164))

    // ========================================================================
    // PASSO 4 · REPUTAÇÃO — base local de reputação
    // ========================================================================

    suspend fun reputationStepChecks(e164: String): List<SpoofCheck> {
        val rep = db.reputationDao().findByE164(e164)
            ?: return listOf(
                SpoofCheck(
                    code = "REPUTATION_UNKNOWN",
                    label = "Sem histórico de reputação",
                    detail = "Número ainda não avaliado na base local — os passos 1 a 3 respondem sozinhos",
                    points = 0,
                    status = SpoofStatus.INFO
                )
            )

        val checks = mutableListOf<SpoofCheck>()

        if (rep.isConfirmedFraud) {
            checks += SpoofCheck(
                code = "REPUTATION_FRAUD",
                label = "Golpe confirmado na base",
                detail = "Denúncias aprovadas confirmaram fraude deste número — evidência máxima",
                points = SpoofWeights.REPUTATION_FRAUD,
                status = SpoofStatus.FLAGGED
            )
        } else if (rep.reportCount > 0) {
            val raw = minOf(SpoofWeights.REPUTATION_REPORTS, 10 + rep.reportCount * 10)
            val confidence = if (rep.confidence > 0.0) rep.confidence else 0.5
            checks += SpoofCheck(
                code = "REPUTATION_REPORTS",
                label = "${rep.reportCount} denúncia(s) coletiva(s)",
                detail = "Confiança ${(rep.confidence * 100).roundToInt()}% — relatos da comunidade contam contra o caller ID",
                points = (raw * confidence).roundToInt(),
                status = SpoofStatus.FLAGGED
            )
        } else {
            checks += SpoofCheck(
                code = "REPUTATION_NO_REPORTS",
                label = "Nenhuma denúncia registrada",
                detail = "Número sem relatos negativos na base local",
                points = 0,
                status = SpoofStatus.PASS
            )
        }

        when (rep.classification) {
            "robocall", "telemarketing", "collector" -> checks += SpoofCheck(
                code = "REPUTATION_CLASS",
                label = "Classificado como ${rep.classification}",
                detail = "A base de reputação associa este número a disparos automáticos/telemarketing",
                points = SpoofWeights.REPUTATION_CLASS,
                status = SpoofStatus.FLAGGED
            )
            "verified_business" -> checks += SpoofCheck(
                code = "REPUTATION_VERIFIED",
                label = "Número comercial verificado",
                detail = "Empresa verificada — reputação positiva reduz a suspeita",
                points = 0,
                status = SpoofStatus.PASS
            )
        }

        return checks
    }

    // ========================================================================
    // PIPELINE — monta os quatro passos no veredito de spoofing
    // ========================================================================

    private fun buildSpoofSteps(
        format: List<SpoofCheck>,
        presentation: List<SpoofCheck>,
        frequency: List<SpoofCheck>,
        reputation: List<SpoofCheck>
    ): List<SpoofStepResult> = SPOOF_STEPS.map { meta ->
        val checks = when (meta.code) {
            "FORMAT" -> format
            "PRESENTATION" -> presentation
            "FREQUENCY" -> frequency
            else -> reputation
        }
        val points = checks.filter { it.status == SpoofStatus.FLAGGED }.sumOf { it.points }
        val status = when {
            checks.any { it.status == SpoofStatus.FLAGGED } -> SpoofStatus.FLAGGED
            checks.any { it.status == SpoofStatus.PASS } -> SpoofStatus.PASS
            else -> SpoofStatus.INFO
        }
        SpoofStepResult(
            id = meta.id,
            code = meta.code,
            title = meta.title,
            description = meta.description,
            status = status,
            points = points,
            checks = checks
        )
    }

    /**
     * Análise completa de spoofing em quatro passos (formato -> apresentação ->
     * frequência -> reputação). Somente leitura: não registra nada.
     */
    suspend fun analyzeSpoofing(e164: String, ownNumber: String): SpoofAnalysis = coroutineScope {
        val format = formatStepChecks(e164)
        val presentation = presentationStepChecks(e164, ownNumber)
        val frequencyDeferred = async { frequencyStepChecks(e164) }
        val reputationDeferred = async { reputationStepChecks(e164) }
        val frequency = frequencyDeferred.await()
        val reputation = reputationDeferred.await()

        val steps = buildSpoofSteps(
            format = format,
            presentation = presentation,
            frequency = frequency,
            reputation = reputation
        )
        val checks = steps.flatMap { it.checks }

        val spoofScore = clampScore(
            checks.filter { it.status == SpoofStatus.FLAGGED }.sumOf { it.points }
        )
        val verdictResult = spoofVerdict(spoofScore)

        SpoofAnalysis(
            checks = checks,
            steps = steps,
            spoofScore = spoofScore,
            verdict = verdictResult.verdict,
            verdictLabel = verdictResult.verdictLabel
        )
    }
}
