package com.orangeshield.app

import android.content.Context
import com.orangeshield.app.engine.DEFAULT_SETTINGS
import com.orangeshield.app.engine.EngineSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * ORANGE SHIELD — persistência das configurações dos motores (SharedPreferences).
 *
 * Espelha o formato de EngineSettings do types.ts. [load] e [save] são
 * suspend e executam I/O fora da thread principal; [state] expõe um
 * StateFlow observável pela UI Compose.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(DEFAULT_SETTINGS)

    /** Estado observável das configurações (valor inicial = padrões do motor). */
    val state: StateFlow<EngineSettings> = _state.asStateFlow()

    /** Valor atual em cache (sem I/O); prefira [load] na primeira leitura. */
    val current: EngineSettings get() = _state.value

    suspend fun load(): EngineSettings = withContext(Dispatchers.IO) {
        val defaults = DEFAULT_SETTINGS
        val loaded = EngineSettings(
            protectionActive = prefs.getBoolean(KEY_PROTECTION_ACTIVE, defaults.protectionActive),
            blockThreshold = prefs.getInt(KEY_BLOCK_THRESHOLD, defaults.blockThreshold),
            warnThreshold = prefs.getInt(KEY_WARN_THRESHOLD, defaults.warnThreshold),
            blockTelemarketing = prefs.getBoolean(KEY_BLOCK_TELEMARKETING, defaults.blockTelemarketing),
            blockRobocalls = prefs.getBoolean(KEY_BLOCK_ROBOCALLS, defaults.blockRobocalls),
            blockCollectors = prefs.getBoolean(KEY_BLOCK_COLLECTORS, defaults.blockCollectors),
            blockOperatorAds = prefs.getBoolean(KEY_BLOCK_OPERATOR_ADS, defaults.blockOperatorAds),
            blockInternational = prefs.getBoolean(KEY_BLOCK_INTERNATIONAL, defaults.blockInternational),
            blockUnknownNumbers = prefs.getBoolean(KEY_BLOCK_UNKNOWN_NUMBERS, defaults.blockUnknownNumbers),
            silenceMode = prefs.getBoolean(KEY_SILENCE_MODE, defaults.silenceMode),
            insistenceProtection = prefs.getBoolean(KEY_INSISTENCE_PROTECTION, defaults.insistenceProtection),
            waProtect = prefs.getBoolean(KEY_WA_PROTECT, defaults.waProtect),
            waBlockUnknown = prefs.getBoolean(KEY_WA_BLOCK_UNKNOWN, defaults.waBlockUnknown),
            waBlockLinks = prefs.getBoolean(KEY_WA_BLOCK_LINKS, defaults.waBlockLinks),
            waBlockForwarded = prefs.getBoolean(KEY_WA_BLOCK_FORWARD, defaults.waBlockForwarded),
            waBlockCodePhish = prefs.getBoolean(KEY_WA_BLOCK_CODE_PHISH, defaults.waBlockCodePhish),
            waBlockGroupInvite = prefs.getBoolean(KEY_WA_BLOCK_GROUP_INVITE, defaults.waBlockGroupInvite),
            spoofProtection = prefs.getBoolean(KEY_SPOOF_PROTECTION, defaults.spoofProtection),
            ownNumber = prefs.getString(KEY_OWN_NUMBER, defaults.ownNumber) ?: "",
            ruleVersion = prefs.getString(KEY_RULE_VERSION, defaults.ruleVersion) ?: defaults.ruleVersion
        )
        _state.value = loaded
        loaded
    }

    suspend fun save(settings: EngineSettings): EngineSettings = withContext(Dispatchers.IO) {
        prefs.edit()
            .putBoolean(KEY_PROTECTION_ACTIVE, settings.protectionActive)
            .putInt(KEY_BLOCK_THRESHOLD, settings.blockThreshold)
            .putInt(KEY_WARN_THRESHOLD, settings.warnThreshold)
            .putBoolean(KEY_BLOCK_TELEMARKETING, settings.blockTelemarketing)
            .putBoolean(KEY_BLOCK_ROBOCALLS, settings.blockRobocalls)
            .putBoolean(KEY_BLOCK_COLLECTORS, settings.blockCollectors)
            .putBoolean(KEY_BLOCK_OPERATOR_ADS, settings.blockOperatorAds)
            .putBoolean(KEY_BLOCK_INTERNATIONAL, settings.blockInternational)
            .putBoolean(KEY_BLOCK_UNKNOWN_NUMBERS, settings.blockUnknownNumbers)
            .putBoolean(KEY_SILENCE_MODE, settings.silenceMode)
            .putBoolean(KEY_INSISTENCE_PROTECTION, settings.insistenceProtection)
            .putBoolean(KEY_WA_PROTECT, settings.waProtect)
            .putBoolean(KEY_WA_BLOCK_UNKNOWN, settings.waBlockUnknown)
            .putBoolean(KEY_WA_BLOCK_LINKS, settings.waBlockLinks)
            .putBoolean(KEY_WA_BLOCK_FORWARD, settings.waBlockForwarded)
            .putBoolean(KEY_WA_BLOCK_CODE_PHISH, settings.waBlockCodePhish)
            .putBoolean(KEY_WA_BLOCK_GROUP_INVITE, settings.waBlockGroupInvite)
            .putBoolean(KEY_SPOOF_PROTECTION, settings.spoofProtection)
            .putString(KEY_OWN_NUMBER, settings.ownNumber)
            .putString(KEY_RULE_VERSION, settings.ruleVersion)
            .apply()
        _state.value = settings
        settings
    }

    private companion object {
        const val PREFS_NAME = "orange_shield_settings"

        const val KEY_PROTECTION_ACTIVE = "protectionActive"
        const val KEY_BLOCK_THRESHOLD = "blockThreshold"
        const val KEY_WARN_THRESHOLD = "warnThreshold"
        const val KEY_BLOCK_TELEMARKETING = "blockTelemarketing"
        const val KEY_BLOCK_ROBOCALLS = "blockRobocalls"
        const val KEY_BLOCK_COLLECTORS = "blockCollectors"
        const val KEY_BLOCK_OPERATOR_ADS = "blockOperatorAds"
        const val KEY_BLOCK_INTERNATIONAL = "blockInternational"
        const val KEY_BLOCK_UNKNOWN_NUMBERS = "blockUnknownNumbers"
        const val KEY_SILENCE_MODE = "silenceMode"
        const val KEY_INSISTENCE_PROTECTION = "insistenceProtection"
        const val KEY_WA_PROTECT = "waProtect"
        const val KEY_WA_BLOCK_UNKNOWN = "waBlockUnknown"
        const val KEY_WA_BLOCK_LINKS = "waBlockLinks"
        const val KEY_WA_BLOCK_FORWARD = "waBlockForwarded"
        const val KEY_WA_BLOCK_CODE_PHISH = "waBlockCodePhish"
        const val KEY_WA_BLOCK_GROUP_INVITE = "waBlockGroupInvite"
        const val KEY_SPOOF_PROTECTION = "spoofProtection"
        const val KEY_OWN_NUMBER = "ownNumber"
        const val KEY_RULE_VERSION = "ruleVersion"
    }
}
