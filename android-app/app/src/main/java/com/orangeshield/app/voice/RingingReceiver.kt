package com.orangeshield.app.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/**
 * ORANGE VOICE — dispara o assistente quando o telefone começa a tocar
 * e o encerra quando a chamada sai do estado de toque.
 *
 * O interruptor é lido DIRETO do SharedPreferences: funciona mesmo com o
 * processo do app morto (o bug da 2.7.0 era depender da memória do app aberto).
 */
class RingingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!isVoiceEnabled(context)) return
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                if (!number.isNullOrBlank()) RingingCallState.ringingNumber = number
                VoiceCallService.start(context)
            }
            TelephonyManager.EXTRA_STATE_IDLE, TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                RingingCallState.clear()
                VoiceCallService.stop(context)
            }
        }
    }

    private fun isVoiceEnabled(context: Context): Boolean =
        context.getSharedPreferences("orange_shield_settings", Context.MODE_PRIVATE)
            .getBoolean("voiceAssistant", false)
}
