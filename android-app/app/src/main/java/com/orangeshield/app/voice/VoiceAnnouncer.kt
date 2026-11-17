package com.orangeshield.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/**
 * Anúncio por TTS sem serviço em primeiro plano — fallback quando o
 * Android impede o início do VoiceCallService em segundo plano
 * (ForegroundServiceStartNotAllowedException / restrição de microfone).
 * TTS é saída de áudio: funciona mesmo com o app em segundo plano.
 */
object VoiceAnnouncer {

    fun announce(context: Context, text: String) {
        val holder = Holder
        holder.tts = TextToSpeech(context.applicationContext) { status ->
            holder.onReady(status, text)
        }
    }

    private object Holder {
        var tts: TextToSpeech? = null

        fun onReady(status: Int, text: String) {
            val engine = tts ?: return
            if (status != TextToSpeech.SUCCESS) return
            engine.language = java.util.Locale("pt", "BR")
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    shutdown()
                }
                override fun onError(utteranceId: String?) {
                    shutdown()
                }
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    shutdown()
                }
            })
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "orange_announce")
        }

        fun shutdown() {
            runCatching { tts?.stop() }
            runCatching { tts?.shutdown() }
            tts = null
        }
    }
}
