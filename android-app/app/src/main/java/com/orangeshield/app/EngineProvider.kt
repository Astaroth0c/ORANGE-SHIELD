package com.orangeshield.app

import android.content.Context
import com.orangeshield.app.data.DatabaseProvider
import com.orangeshield.app.engine.CallRuleEngine
import com.orangeshield.app.engine.SpoofDetector
import com.orangeshield.app.engine.WhatsAppEngine

/**
 * ORANGE SHIELD — injeção de dependências manual (sem Hilt).
 *
 * Fornece banco, motores e store de configurações para a UI e para os
 * serviços de sistema. Chame [init] no onCreate da MainActivity e de cada
 * serviço (é idempotente e seguro entre threads).
 */
object EngineProvider {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) appContext = context.applicationContext
            }
        }
    }

    private fun requireContext(): Context =
        appContext
            ?: error("EngineProvider.init(context) precisa ser chamado antes do uso (MainActivity ou serviços).")

    val database get() = DatabaseProvider.getInstance(requireContext())

    val spoofDetector: SpoofDetector by lazy { SpoofDetector(database) }
    val callEngine: CallRuleEngine by lazy { CallRuleEngine(database, spoofDetector) }
    val whatsappEngine: WhatsAppEngine by lazy { WhatsAppEngine(database, spoofDetector) }
    val settingsStore: SettingsStore by lazy { SettingsStore(requireContext()) }
}
