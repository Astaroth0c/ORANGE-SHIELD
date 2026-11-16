package com.orangeshield.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/**
 * Teste REAL de ORANGE VOICE: o app fala uma frase e em seguida escuta
 * o microfone por alguns segundos, devolvendo o texto reconhecido.
 * Serve para o usuário confirmar no próprio aparelho que TTS e
 * reconhecimento funcionam (e diagnosticar quando não funcionam).
 */
object VoiceTest {

    fun run(context: Context, onResult: (heard: String?, error: String?) -> Unit) {
        val holder = Holder(context.applicationContext, onResult)
        holder.tts = TextToSpeech(context.applicationContext) { status ->
            holder.onTtsReady(status)
        }
    }

    private fun testIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private class Holder(
        val appContext: Context,
        val onResult: (heard: String?, error: String?) -> Unit
    ) {

        var tts: TextToSpeech? = null
        var recognizer: SpeechRecognizer? = null

        fun onTtsReady(status: Int) {
            val engine = tts ?: return
            if (status != TextToSpeech.SUCCESS) {
                onResult(null, "TTS (fala) indisponível neste aparelho.")
                return
            }
            engine.language = java.util.Locale("pt", "BR")
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    recognizer?.startListening(testIntent())
                }
                override fun onError(utteranceId: String?) {
                    onResult(null, "Falha no áudio do TTS.")
                }
                override fun onStop(utteranceId: String?, interrupted: Boolean) {}
            })
            engine.speak("ORANGE VOICE ativo. Diga algo.", TextToSpeech.QUEUE_FLUSH, null, "voice_test")

            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        val heard = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        onResult(heard, null)
                        shutdown()
                    }
                    override fun onError(error: Int) {
                        onResult(
                            null,
                            "Reconhecimento falhou (erro $error). Verifique o microfone e o ruído do ambiente."
                        )
                        shutdown()
                    }
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }

        fun shutdown() {
            runCatching { recognizer?.destroy() }
            recognizer = null
            runCatching { tts?.stop() }
            runCatching { tts?.shutdown() }
            tts = null
        }
    }
}
