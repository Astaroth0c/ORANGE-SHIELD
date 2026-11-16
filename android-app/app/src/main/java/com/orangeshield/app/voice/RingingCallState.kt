package com.orangeshield.app.voice

/**
 * ORANGE VOICE — estado compartilhado da chamada tocando.
 *
 * O CallScreeningServiceImpl publica aqui o resultado da triagem de cada
 * chamada; o VoiceCallService consome para anunciar por voz e executar
 * comandos. Sem banco, sem I/O — apenas memória do processo.
 */
object RingingCallState {

    /** Número tocando (E.164 quando identificável). */
    @Volatile
    var ringingNumber: String? = null

    /** Decisão da triagem: ALLOW | WARN | SILENCE | BLOCK (nulo = não triada). */
    @Volatile
    var screeningDecision: String? = null

    /** Verdadeiro quando a reputação/comunidade aponta golpe ou spam. */
    @Volatile
    var reputationFlagged: Boolean = false

    /** Verdadeiro quando o número está na lista de autorizados. */
    @Volatile
    var isAllowedContact: Boolean = false

    /** Interruptor ORANGE VOICE (espelha settings.voiceAssistant, sem I/O no receiver). */
    @Volatile
    var voiceEnabled: Boolean = false

    fun onScreened(number: String?, decision: String?, reputationFlagged: Boolean, allowed: Boolean) {
        ringingNumber = number
        screeningDecision = decision
        this.reputationFlagged = reputationFlagged
        this.isAllowedContact = allowed
    }

    fun clear() {
        ringingNumber = null
        screeningDecision = null
        reputationFlagged = false
        isAllowedContact = false
    }
}
