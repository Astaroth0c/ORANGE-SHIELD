package com.orangeshield.app.engine

import com.orangeshield.app.data.CallEventEntity

/**
 * Associação comportamental local baseada em chamadas realmente triadas.
 * Um grupo é um indício para revisão, nunca uma confirmação de fraude.
 */
data class CampaignFinding(
    val ddd: String,
    val distinctNumbers: Int,
    val totalCalls: Int,
    val numbers: List<String>,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val confidence: String,
    val explanation: String
)

fun detectCampaigns(
    events: List<CallEventEntity>,
    now: Long = System.currentTimeMillis(),
    windowMillis: Long = 60L * 60L * 1000L
): List<CampaignFinding> {
    val recent = events.filter { it.occurredAt in (now - windowMillis)..now }
    val byDdd = recent.mapNotNull { event ->
        if (!normalizePhoneDetailed(event.e164).isValid) return@mapNotNull null
        val br = parseBrazilNumber(event.e164) ?: return@mapNotNull null
        br.ddd to event
    }.groupBy({ it.first }, { it.second })

    return byDdd.mapNotNull { (ddd, calls) ->
        val numbers = calls.map { it.e164 }.distinct().sorted()
        if (numbers.size < 3 || calls.size < 5) return@mapNotNull null

        val confidence = if (numbers.size >= 6 && calls.size >= 10) "MODERADA" else "BAIXA"
        CampaignFinding(
            ddd = ddd,
            distinctNumbers = numbers.size,
            totalCalls = calls.size,
            numbers = numbers,
            firstSeenAt = calls.minOf { it.occurredAt },
            lastSeenAt = calls.maxOf { it.occurredAt },
            confidence = confidence,
            explanation = "Vários identificadores do mesmo DDD apareceram em uma janela de 60 minutos. Pode ser campanha, central compartilhada ou coincidência; não confirma spoofing."
        )
    }.sortedWith(compareByDescending<CampaignFinding> { it.distinctNumbers }.thenByDescending { it.totalCalls })
}
