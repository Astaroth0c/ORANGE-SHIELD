package com.orangeshield.app.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.orangeshield.app.EngineProvider
import com.orangeshield.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ORANGE VOICE — assistente de chamadas por voz.
 *
 * Enquanto uma chamada TOCA (o microfone ainda é livre):
 *  1. Anuncia por TTS o veredito da triagem ("Spam. Provável tentativa de
 *     golpe." / "Número desconhecido ligando.").
 *  2. Escuta comandos pt-BR: "cancelar chamada" (encerra), "cancelar e
 *     bloquear" (encerra + lista de bloqueio), "atender" (melhor esforço
 *     via tecla de headset + viva-voz).
 *
 * Limitações reais do Android (declaradas também na UI):
 *  - Atender programaticamente exige permissões de discador em muitos
 *    fabricantes — usamos a tecla de headset como melhor esforço.
 *  - No Android 14+ o microfone em serviço de segundo plano pode ser
 *    restringido pelo sistema/fabricante; o anúncio por TTS continua
 *    funcionando mesmo sem o reconhecimento de voz.
 */
class VoiceCallService : Service(), RecognitionListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var restarts = 0
    private var handled = false

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val telecom by lazy { getSystemService(Context.TELECOM_SERVICE) as TelecomManager? }

    override fun onBind(intent: Intent?): IBinder = Binder()

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = java.util.Locale("pt", "BR")
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> {
                val inForeground = startInForeground()
                handled = false
                announceVerdict()
                scope.launch {
                    delay(ANNOUNCE_DELAY_MS)
                    if (inForeground) {
                        startListening()
                        watchCallState()
                    } else {
                        // Android bloqueou o serviço em segundo plano: o
                        // anúncio por TTS já saiu; encerramos limpo.
                        stopSelf()
                    }
                }
            }
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(): Boolean {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "ORANGE VOICE", NotificationManager.IMPORTANCE_LOW)
        )
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ORANGE VOICE ativo")
            .setContentText("Assistente de voz aguardando a chamada tocar.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (_: Exception) {
            // ForegroundServiceStartNotAllowedException / restrição de tipo:
            // sem serviço em primeiro plano neste aparelho neste momento.
            false
        }
    }

    /** Fala o veredito da triagem enquanto o telefone toca. */
    private fun announceVerdict() {
        if (RingingCallState.announced) return
        val state = RingingCallState
        val contact = ContactLookup.name(this, state.ringingNumber)
        val script = when {
            state.reputationFlagged || state.screeningDecision == "WARN" ->
                "Atenção. Spam. Provável tentativa de golpe."
            !contact.isNullOrBlank() -> "Chamada de $contact."
            state.screeningDecision == null || state.screeningDecision == "ALLOW" ->
                "Número desconhecido ligando."
            else -> return
        }
        RingingCallState.announced = true
        speak("$script Diga: cancelar chamada, ou: atender.")
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "orange_voice")
    }

    private fun startListening() {
        if (handled || listening) return
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(this@VoiceCallService)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            startListening(intent)
        }
        listening = true
    }

    private suspend fun watchCallState() {
        // Teto de segurança: encerra o assistente se a chamada demorar.
        repeat(RINGING_TIMEOUT_CYCLES) {
            delay(5_000)
            if (handled) return@repeat
        }
        stopSelf()
    }

    // ------------------------------------------------------------------
    // Comandos
    // ------------------------------------------------------------------

    private fun handleCommand(raw: String) {
        val command = raw.lowercase(java.util.Locale("pt", "BR"))
        val wantsBlock = command.contains("bloque")
        val wantsCancel = command.contains("cancel") || command.contains("deslig") ||
            command.contains("rejeit") || command.contains("recusar")
        val wantsAnswer = command.contains("atend") || command.contains("responder")

        when {
            wantsCancel -> {
                handled = true
                speak("Cancelando chamada.")
                scope.launch {
                    delay(400)
                    endRingingCall()
                    if (wantsBlock) blockRingingNumber()
                    stopSelf()
                }
            }
            wantsAnswer -> {
                handled = true
                speak("Atendendo em viva voz.")
                scope.launch {
                    delay(400)
                    answerRingingCall()
                    stopSelf()
                }
            }
        }
    }

    /** Encerra via TelecomManager (ANSWER_PHONE_CALLS) — melhor esforço. */
    private fun endRingingCall(): Boolean = try {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ANSWER_PHONE_CALLS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            telecom?.endCall() == true
        } else false
    } catch (_: Exception) {
        false
    }

    /**
     * Atender: caminho principal via TelecomManager.acceptRingingCall()
     * (permissão ANSWER_PHONE_CALLS); tecla de headset como fallback.
     * Viva-voz ativado em duas tentativas (a chamada pode ainda estar
     * estabelecendo no primeiro instante).
     */
    private fun answerRingingCall(): Boolean = try {
        val canAnswer = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ANSWER_PHONE_CALLS) ==
            PackageManager.PERMISSION_GRANTED
        if (canAnswer) {
            runCatching { telecom?.acceptRingingCall() }
        }
        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK)
        val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK)
        audioManager.dispatchMediaKeyEvent(down)
        audioManager.dispatchMediaKeyEvent(up)
        scope.launch {
            delay(1_200)
            enableSpeakerphone()
            delay(1_800)
            enableSpeakerphone()
        }
        true
    } catch (_: Exception) {
        false
    }

    private fun enableSpeakerphone() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speaker = audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    audioManager.setCommunicationDevice(speaker)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.setSpeakerphoneOn(true)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.setSpeakerphoneOn(true)
            }
        }
    }

    private suspend fun blockRingingNumber() {
        val number = RingingCallState.ringingNumber ?: return
        if (number.isBlank()) return
        withContext(Dispatchers.IO) {
            EngineProvider.database.phoneListDao().upsertBlocked(
                com.orangeshield.app.data.BlockedEntity(
                    e164 = number,
                    label = "Bloqueado por comando de voz",
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    // ------------------------------------------------------------------
    // RecognitionListener
    // ------------------------------------------------------------------

    override fun onResults(results: android.os.Bundle) {
        listening = false
        val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        heard.forEach { phrase -> handleCommand(phrase) }
        if (!handled && restarts < MAX_RESTARTS) {
            restarts++
            scope.launch {
                delay(1_200)
                startListening()
            }
        }
    }

    override fun onError(error: Int) {
        listening = false
        if (handled) return
        if (restarts < MAX_RESTARTS) {
            restarts++
            scope.launch {
                delay(1_500)
                startListening()
            }
        }
    }

    override fun onPartialResults(partialResults: android.os.Bundle?) {
        val heard = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        heard.forEach { phrase -> handleCommand(phrase) }
    }

    override fun onReadyForSpeech(params: android.os.Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

    // ------------------------------------------------------------------

    override fun onDestroy() {
        listening = false
        runCatching { recognizer?.destroy() }
        recognizer = null
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        RingingCallState.clear()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.orangeshield.app.voice.START"
        const val ACTION_STOP = "com.orangeshield.app.voice.STOP"
        private const val CHANNEL_ID = "orange_voice"
        private const val NOTIFICATION_ID = 4102
        private const val MAX_RESTARTS = 6
        private const val RINGING_TIMEOUT_CYCLES = 18
        private const val ANNOUNCE_DELAY_MS = 900L

        fun start(context: Context) {
            val intent = Intent(context, VoiceCallService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, VoiceCallService::class.java).setAction(ACTION_STOP))
        }
    }
}
