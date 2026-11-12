package com.orangeshield.app.engine

import java.util.Locale

/**
 * ORANGE SHIELD — utilidades de normalização telefônica.
 * Porta 1:1 de src/lib/engine/normalize.ts: números brasileiros (E.164)
 * e remetentes alfanuméricos de SMS.
 */

private val LETTER_RE = Regex("[A-Za-z]")
private val WHITESPACE_RUN_RE = Regex("\\s+")
private val NON_DIGIT_RE = Regex("\\D")
private val SHORTCODE_RE = Regex("^\\+55(\\d+)$")
private val BR_NUMBER_RE = Regex("^\\+55(\\d{2})(\\d{8,9})$")
private val BR_FORMAT_RE = Regex("^\\+55(\\d{2})(\\d{4,5})(\\d{4})$")
private val SERVICE_PREFIX_RE = Regex("^\\+55(0303|0800|0300)")

private const val COUNTRY_CODE = "55"

/** Parte local de um número brasileiro parseado: DDD + local. */
data class BrazilNumber(
    val ddd: String,
    val local: String
)

/**
 * Normaliza uma entrada bruta em E.164-like.
 * Trata: (11) 99999-9999, 0303..., 0800/0300..., +55..., shortcodes.
 * Remetentes alfanuméricos (SMS) voltam em maiúsculas, sem espaços.
 */
fun normalizePhone(raw: String?): String {
    val trimmed = (raw ?: "").trim()
    if (trimmed.isEmpty()) return ""

    // Remetente alfanumérico (ex.: "CLARO", "VIVO-OFERTA")
    if (LETTER_RE.containsMatchIn(trimmed)) {
        return trimmed.uppercase(Locale.ROOT).replace(WHITESPACE_RUN_RE, "")
    }

    // Intenção de discagem internacional: prefixo "+" ou "00"
    val intlIntent = trimmed.startsWith("+") || trimmed.startsWith("00")

    var digits = NON_DIGIT_RE.replace(trimmed, "")
    if (digits.isEmpty()) return ""

    // Remove o prefixo de discagem internacional (00)
    if (digits.startsWith("00")) digits = digits.substring(2)
    if (digits.isEmpty()) return ""

    // Já traz o código do país (55 + DDD + número)
    if (digits.startsWith(COUNTRY_CODE) && digits.length >= 12 && digits.length <= 13) {
        return "+$digits"
    }

    // Código de país estrangeiro — preserva como internacional
    // (o Passo 2 do Spoof Detector depende da apresentação internacional genuína)
    if (intlIntent && !digits.startsWith(COUNTRY_CODE)) {
        return "+$digits"
    }

    // 0303 (telemarketing Anatel), 0800/0300 (serviços)
    if (digits.startsWith("0303") || digits.startsWith("0800") || digits.startsWith("0300")) {
        return "+$COUNTRY_CODE$digits"
    }

    // Shortcodes (SMS premium / centrais de operadora, 3-6 dígitos)
    if (digits.length <= 6) {
        return "+$COUNTRY_CODE$digits"
    }

    // Número local com DDD
    return "+$COUNTRY_CODE$digits"
}

/** Verdadeiro quando o número normalizado é internacional (não +55). */
fun isInternational(e164: String): Boolean =
    e164.startsWith("+") && !e164.startsWith("+$COUNTRY_CODE")

/** Verdadeiro quando o remetente parece um shortcode de operadora (3-6 dígitos após +55). */
fun isShortcode(e164: String): Boolean {
    val match = SHORTCODE_RE.matchEntire(e164) ?: return false
    return match.groupValues[1].length <= 6
}

/** Formata um E.164 brasileiro para exibição: +5511999998888 -> +55 (11) 99999-8888 */
fun formatPhone(e164: String): String {
    val match = BR_FORMAT_RE.matchEntire(e164) ?: return e164
    return "+55 (${match.groupValues[1]}) ${match.groupValues[2]}-${match.groupValues[3]}"
}

/**
 * Converte um número +55 em { ddd, local }.
 * Retorna null para shortcodes / internacionais / remetentes alfanuméricos.
 */
fun parseBrazilNumber(e164: String): BrazilNumber? {
    val match = BR_NUMBER_RE.matchEntire(e164) ?: return null
    return BrazilNumber(ddd = match.groupValues[1], local = match.groupValues[2])
}

/** 0303/0800/0300 usam um plano de numeração dedicado. */
fun isServiceNumber(e164: String): Boolean = SERVICE_PREFIX_RE.containsMatchIn(e164)
