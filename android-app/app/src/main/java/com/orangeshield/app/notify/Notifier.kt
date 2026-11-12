package com.orangeshield.app.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.orangeshield.app.R

/**
 * ORANGE SHIELD — utilitário de notificações locais.
 * Canais: triagem de chamadas (BLOCK/WARN) e guardião WhatsApp.
 * Somente API de framework (android.app.Notification.Builder) — sem
 * dependência adicional de androidx.core.
 */
object Notifier {

    const val CHANNEL_CALL_GUARD = "orange_shield_call_guard"
    const val CHANNEL_WHATSAPP_GUARD = "orange_shield_whatsapp_guard"

    private const val BASE_CALL_NOTIFICATION_ID = 1001

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val callChannel = NotificationChannel(
            CHANNEL_CALL_GUARD,
            context.getString(R.string.channel_call_guard_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_call_guard_description)
        }
        val whatsappChannel = NotificationChannel(
            CHANNEL_WHATSAPP_GUARD,
            context.getString(R.string.channel_whatsapp_guard_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_whatsapp_guard_description)
        }
        manager.createNotificationChannels(listOf(callChannel, whatsappChannel))
    }

    /** Verdadeiro quando o usuário concedeu o acesso a notificações (listener). */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return flat.split(":").any { it.contains(context.packageName) }
    }

    fun postCallAlert(context: Context, blocked: Boolean, title: String, text: String) {
        if (!canPost(context)) return
        val builder = Notification.Builder(context, CHANNEL_CALL_GUARD)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        // IDs distintos para BLOCK e WARN — um alerta não sobrescreve o outro.
        post(context, BASE_CALL_NOTIFICATION_ID + if (blocked) 0 else 1, builder)
    }

    fun postWhatsAppAlert(
        context: Context,
        title: String,
        text: String,
        contentIntent: Intent? = null,
        notificationId: Int
    ) {
        if (!canPost(context)) return
        val builder = Notification.Builder(context, CHANNEL_WHATSAPP_GUARD)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        if (contentIntent != null) {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    notificationId,
                    contentIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        post(context, notificationId, builder)
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.areNotificationsEnabled()
    }

    private fun post(context: Context, id: Int, builder: Notification.Builder) {
        try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.notify(id, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revogado: alerta silenciado sem afetar a triagem.
        } catch (_: Exception) {
            // Nunca interromper o serviço por falha de notificação.
        }
    }
}
