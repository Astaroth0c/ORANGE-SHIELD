package com.orangeshield.app.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.orangeshield.app.notify.Notifier

/**
 * ORANGE VOICE + TELA DE CHAMADA — quando o telefone toca:
 *  1. Publica a notificação de TELA CHEIA com a CallAlertActivity
 *     (veredito, nome do contato e botões reais Atender/Recusar/Bloquear).
 *  2. Inicia o assistente de voz (TTS + reconhecimento). Se o Android
 *     bloquear o serviço em segundo plano, o anúncio sai por TTS puro
 *     (VoiceAnnouncer) e a tela continua disponível.
 *
 * O interruptor de voz é lido DIRETO do SharedPreferences: funciona mesmo
 * com o processo do app morto.
 */
class RingingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    ?: RingingCallState.ringingNumber
                if (!number.isNullOrBlank()) RingingCallState.ringingNumber = number

                Notifier.postRingingAlert(
                    context,
                    number = RingingCallState.ringingNumber,
                    contactName = ContactLookup.name(context, RingingCallState.ringingNumber)
                )

                if (isVoiceEnabled(context)) {
                    try {
                        VoiceCallService.start(context)
                    } catch (_: Exception) {
                        announceFallback(context)
                    }
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE, TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                RingingCallState.clear()
                Notifier.cancelRingingAlert(context)
                VoiceCallService.stop(context)
            }
        }
    }

    /** Fallback real: anúncio por TTS sem serviço em primeiro plano. */
    private fun announceFallback(context: Context) {
        if (RingingCallState.announced) return
        RingingCallState.announced = true
        val contact = ContactLookup.name(context, RingingCallState.ringingNumber)
        val script = when {
            RingingCallState.reputationFlagged -> "Atenção. Spam. Provável tentativa de golpe."
            !contact.isNullOrBlank() -> "Chamada de $contact."
            else -> "Número desconhecido ligando."
        }
        VoiceAnnouncer.announce(context, "$script Diga: cancelar chamada, ou: atender.")
    }

    private fun isVoiceEnabled(context: Context): Boolean =
        context.getSharedPreferences("orange_shield_settings", Context.MODE_PRIVATE)
            .getBoolean("voiceAssistant", false)
}
