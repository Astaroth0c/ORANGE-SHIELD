package com.orangeshield.app.engine

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import java.util.Locale

data class PhoneNormalization(
    val raw: String,
    val e164: String,
    val isValid: Boolean,
    val source: String,
    val note: String? = null
)

private val LETTER_RE = Regex("[A-Za-z]")
private val WHITESPACE_RUN_RE = Regex("\\s+")
private val NON_DIGIT_RE = Regex("\\D")
private val SHORTCODE_RE = Regex("^\\+55(\\d+)$")
private val BR_NUMBER_RE = Regex("^\\+55(\\d{2})(\\d{8,9})$")
private val BR_FORMAT_RE = Regex("^\\+55(\\d{2})(\\d{4,5})(\\d{4})$")
private val SERVICE_PREFIX_RE = Regex("^\\+55(0303|0800|0300)")
private const val COUNTRY_CODE = "55"
private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()

data class BrazilNumber(val ddd: String, val local: String)

/**
 * O prefixo nacional 0 após 55 só é removido quando o candidato resultante
 * é validado pelo plano brasileiro do libphonenumber.
 */
fun normalizePhoneDetailed(raw: String?): PhoneNormalization {
    val original = (raw ?: "").trim()
    if (original.isEmpty()) return PhoneNormalization(original, "", false, "empty")

    if (LETTER_RE.containsMatchIn(original)) {
        val alpha = original.uppercase(Locale.ROOT).replace(WHITESPACE_RUN_RE, "")
        return PhoneNormalization(original, alpha, false, "alphanumeric", "Remetente sem número telefônico")
    }

    val explicitPlus = original.startsWith("+")
    var digits = NON_DIGIT_RE.replace(original, "")
    if (digits.isEmpty()) return PhoneNormalization(original, "", false, "empty")

    val explicitInternational = explicitPlus || digits.startsWith("00")
    if (digits.startsWith("00")) digits = digits.drop(2)
    if (digits.isEmpty()) return PhoneNormalization(original, "", false, "empty")

    if (!explicitInternational && (digits.startsWith("0303") || digits.startsWith("0800") || digits.startsWith("0300"))) {
        return PhoneNormalization(original, "+55$digits", true, "br_service")
    }
    if (!explicitInternational && digits.length <= 6) {
        return PhoneNormalization(original, "+55$digits", true, "br_shortcode")
    }

    fun validE164(candidate: String, region: String?): String? = runCatching {
        val parsed = phoneUtil.parse(candidate, region)
        if (phoneUtil.isValidNumber(parsed)) phoneUtil.format(parsed, PhoneNumberFormat.E164) else null
    }.getOrNull()

    if (digits.startsWith(COUNTRY_CODE)) {
        val canonical = "+$digits"
        validE164(canonical, null)?.let {
            return PhoneNormalization(original, it, true, "br_international")
        }

        // Corrige registros no formato 55 + 0 nacional + DDD + assinante.
        val afterCountry = digits.drop(2)
        if (afterCountry.startsWith("0")) {
            val candidate = "+55${afterCountry.drop(1)}"
            validE164(candidate, null)?.let {
                return PhoneNormalization(original, it, true, "br_trunk_prefix",
                    "Prefixo nacional 0 removido após o código do Brasil; DDD validado")
            }
        }
        return PhoneNormalization(original, canonical, false, "invalid_br_candidate",
            "Número iniciado por 55, mas não validado pelo plano brasileiro")
    }

    if (explicitInternational) {
        val candidate = "+$digits"
        val valid = validE164(candidate, null)
        return PhoneNormalization(original, valid ?: candidate, valid != null,
            if (valid != null) "international_valid" else "international_unvalidated",
            if (valid == null) "Formato internacional não validado" else null)
    }

    val nationalDigits = if (digits.startsWith("0")) digits.drop(1) else digits
    val nationalValid = validE164(nationalDigits, "BR")
    if (nationalValid != null) {
        return PhoneNormalization(original, nationalValid, true,
            if (digits.startsWith("0")) "br_national_trunk" else "br_national",
            if (digits.startsWith("0")) "Prefixo nacional 0 removido após validação" else null)
    }

    return PhoneNormalization(original, "+55$digits", false, "br_unvalidated",
        "Número não validado; não deve ser considerado legítimo nem spoofing apenas pelo formato")
}

fun normalizePhone(raw: String?): String = normalizePhoneDetailed(raw).e164

fun isInternational(e164: String): Boolean =
    e164.startsWith("+") && !e164.startsWith("+$COUNTRY_CODE")

fun isShortcode(e164: String): Boolean {
    val match = SHORTCODE_RE.matchEntire(e164) ?: return false
    return match.groupValues[1].length <= 6
}

fun formatPhone(e164: String): String {
    val match = BR_FORMAT_RE.matchEntire(e164) ?: return e164
    return "+55 (${match.groupValues[1]}) ${match.groupValues[2]}-${match.groupValues[3]}"
}

fun parseBrazilNumber(e164: String): BrazilNumber? {
    val match = BR_NUMBER_RE.matchEntire(e164) ?: return null
    return BrazilNumber(ddd = match.groupValues[1], local = match.groupValues[2])
}

fun isServiceNumber(e164: String): Boolean = SERVICE_PREFIX_RE.containsMatchIn(e164)
