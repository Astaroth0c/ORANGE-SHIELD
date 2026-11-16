package com.orangeshield.app.engine

import com.orangeshield.app.data.AppDatabase
import com.orangeshield.app.data.HistoryCandidateEntity
import com.orangeshield.app.data.ReputationEntity

/**
 * ORANGE SHIELD — analisador de incômodo recorrente ("nuisance").
 *
 * Detecta, a partir de dados REAIS do histórico do aparelho (Radar),
 * números cujo padrão é incomodar: volume alto de chamadas com maioria
 * recusada ou não atendida — característica típica de robocall em rajada,
 * Wangiri e telemarketing insistente.
 *
 * O veredito é gravado na base de reputação local (classificação
 * "nuisance") e consumido pelo Passo 4 do Spoof Detector, influenciando
 * decisões reais do Call Guard. Números publicados no feed comunitário
 * chegam aos demais aparelhos pela mesma cadeia.
 */
object NuisanceAnalyzer {

    /** Mínimo de chamadas para avaliar (abaixo disso não há padrão). */
    const val MIN_TOTAL_CALLS = 4

    /** Fração mínima de chamadas recusadas/não atendidas. */
    const val MIN_UNANSWERED_RATIO = 0.6

    /** Chamadas por dia ativo para configurar insistência. */
    const val MIN_CALLS_PER_DAY = 1.5

    /** Peso do flag de incômodo no spoof score. */
    const val NUISANCE_POINTS = 25

    data class Verdict(
        val isNuisance: Boolean,
        val level: Int,
        val reason: String
    )

    /** Avaliação determinística e explicável — sem aleatoriedade. */
    fun evaluate(candidate: HistoryCandidateEntity): Verdict {
        val unanswered = candidate.missedCalls + candidate.rejectedCalls
        if (candidate.totalCalls < MIN_TOTAL_CALLS || candidate.totalCalls == 0) {
            return Verdict(false, 0, "Volume insuficiente para avaliar padrão (${candidate.totalCalls} chamada(s)).")
        }
        val unansweredRatio = unanswered.toDouble() / candidate.totalCalls
        val windowDays = ((candidate.lastSeenAt - candidate.firstSeenAt) / 86_400_000.0).coerceAtLeast(1.0)
        val callsPerDay = candidate.totalCalls / windowDays

        val persistent = unansweredRatio >= MIN_UNANSWERED_RATIO && callsPerDay >= MIN_CALLS_PER_DAY
        val level = when {
            !persistent -> 0
            else -> (
                20 * callsPerDay.coerceAtMost(5.0) +
                    40 * unansweredRatio
                ).toInt().coerceIn(25, 100)
        }
        val reason = if (persistent) {
            "${candidate.totalCalls} chamadas em ${windowDays.toInt()} dia(s), " +
                "${(unansweredRatio * 100).toInt()}% recusadas/não atendidas (${callsPerDay.format1()}/dia) — padrão de incômodo."
        } else {
            "Sem padrão de incômodo: ${candidate.totalCalls} chamada(s), " +
                "${(unansweredRatio * 100).toInt()}% recusadas/não atendidas."
        }
        return Verdict(persistent, level, reason)
    }

    /**
     * Aplica o veredito à reputação local de todos os candidatos.
     * Preserva evidências mais fortes já existentes (fraude confirmada,
     * mais denúncias). Retorna a quantidade de números taxados.
     */
    suspend fun applyToReputation(db: AppDatabase, candidates: List<HistoryCandidateEntity>): Int {
        val dao = db.reputationDao()
        var flagged = 0
        candidates.forEach { candidate ->
            val verdict = evaluate(candidate)
            if (!verdict.isNuisance) return@forEach
            val existing = dao.findByE164(candidate.e164)
            dao.upsert(
                ReputationEntity(
                    e164 = candidate.e164,
                    riskScore = maxOf(existing?.riskScore ?: 0, verdict.level),
                    classification = if (existing != null && existing.classification in ENGINE_CLASSES) {
                        existing.classification
                    } else "nuisance",
                    reportCount = maxOf(existing?.reportCount ?: 0, 1),
                    isConfirmedFraud = existing?.isConfirmedFraud ?: false,
                    confidence = maxOf(existing?.confidence ?: 0.0, 0.7),
                    updatedAt = System.currentTimeMillis()
                )
            )
            flagged++
        }
        return flagged
    }

    private val ENGINE_CLASSES = setOf("robocall", "telemarketing", "collector", "verified_business")

    private fun Double.format1(): String = if (this % 1.0 == 0.0) toInt().toString() else String.format("%.1f", this)
}
