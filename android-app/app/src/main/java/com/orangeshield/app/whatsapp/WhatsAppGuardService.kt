package com.orangeshield.app.whatsapp

import android.app.Notification
import android.content.Intent
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.orangeshield.app.EngineProvider
import com.orangeshield.app.data.WhatsAppEventEntity
import com.orangeshield.app.engine.SmsDecision
import com.orangeshield.app.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * ORANGE SHIELD · GUARDIÃO WHATSAPP
 *
 * NotificationListenerService com opt-in explícito: analisa apenas notificações
 * do WhatsApp/WhatsApp Business, aplica o catálogo de golpes e guarda somente
 * um fingerprint SHA-256 do texto, nunca o conteúdo original.
 *
 * IMPORTANTE: o WhatsApp não oferece API pública para bloquear automaticamente
 * uma conversa/remetente. Por isso o guardião NÃO bloqueia a mensagem de
 * fato — ele classifica, alerta o usuário e abre o deep link wa.me do
 * remetente (via [buildBlockGuidanceIntent]) para o bloqueio manual no
 * próprio WhatsApp (menu do contato → Bloquear).
 */
class WhatsAppGuardService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        EngineProvider.init(this)
        Notifier.ensureChannels(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName != PACKAGE_WHATSAPP && packageName != PACKAGE_WHATSAPP_BUSINESS) return

        // Process notifications only after the user has explicitly accepted
        // the in-app disclosure and enabled Android's notification access.
        val consent = getSharedPreferences(PRIVACY_PREFS, MODE_PRIVATE)
            .getBoolean(KEY_WHATSAPP_CONSENT, false)
        if (!consent) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return

        serviceScope.launch {
            try {
                val settings = EngineProvider.settingsStore.load()

                // A API de notificações não expõe o marcador nativo "encaminhada
                // muitas vezes"; usamos um marcador textual determinístico quando
                // ele aparece no corpo da notificação.
                val forwardedMany = text.contains(FORWARDED_MARKER, ignoreCase = true)

                val analysis = EngineProvider.whatsappEngine.analyze(
                    rawSender = title, // o título da notificação é o remetente (nome ou número)
                    content = text,
                    forwardedMany = forwardedMany,
                    settings = settings
                )

                val dao = EngineProvider.database.whatsappEventDao()
                val now = System.currentTimeMillis()
                val fingerprint = MessageDigest.getInstance("SHA-256")
                    .digest(text.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }

                // Deduplica republicações sem guardar o conteúdo original.
                if (dao.countRecent(analysis.sender, "SHA-256:$fingerprint", now - DUPLICATE_WINDOW_MS) > 0) {
                    return@launch
                }

                dao.insert(
                    WhatsAppEventEntity(
                        sender = analysis.sender,
                        content = "SHA-256:$fingerprint",
                        decision = analysis.decision.name,
                        riskScore = analysis.riskScore,
                        category = analysis.category,
                        reason = analysis.reason,
                        occurredAt = now
                    )
                )

                if (analysis.decision != SmsDecision.ALLOW) {
                    val label = if (analysis.decision == SmsDecision.BLOCK) "BLOQUEADA" else "EM QUARENTENA"
                    Notifier.postWhatsAppAlert(
                        context = this@WhatsAppGuardService,
                        title = "ORANGE SHIELD · WhatsApp",
                        text = "Mensagem de $title: $label (risco ${analysis.riskScore}/100). ${analysis.reason}",
                        contentIntent = buildBlockGuidanceIntent(analysis.sender),
                        notificationId = (analysis.sender + analysis.reason).hashCode()
                    )
                }
            } catch (_: Exception) {
                // Falha de análise nunca deve derrubar o serviço de listener.
            }
        }
    }

    /**
     * O WhatsApp não oferece API pública para bloqueio automático —
     * abrimos o deep link wa.me do remetente para o usuário bloquear
     * manualmente (menu do contato → Bloquear).
     */
    private fun buildBlockGuidanceIntent(sender: String): Intent {
        val digits = sender.filter { it.isDigit() }
        return if (digits.length >= 8) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))
                .setPackage(PACKAGE_WHATSAPP)
        } else {
            // Remetente sem número comparável (ex.: nome de contato): abre o WhatsApp.
            packageManager.getLaunchIntentForPackage(PACKAGE_WHATSAPP)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/"))
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val PACKAGE_WHATSAPP = "com.whatsapp"
        const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        const val DUPLICATE_WINDOW_MS = 30_000L
        const val FORWARDED_MARKER = "encaminhada muitas vezes"
        const val PRIVACY_PREFS = "orange_shield_privacy"
        const val KEY_WHATSAPP_CONSENT = "whatsapp_notification_access_consent"
    }
}
