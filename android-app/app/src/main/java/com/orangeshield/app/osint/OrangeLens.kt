package com.orangeshield.app.osint

import android.content.Context
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import com.orangeshield.app.data.AppDatabase
import com.orangeshield.app.data.CommunityReportEntity
import com.orangeshield.app.data.ReputationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * ORANGE LENS — inteligência real de números (OSINT legítimo).
 *
 * Três fontes, todas reais:
 * 1. OFFLINE · datasets do Google (libphonenumber geocoder + carrier):
 *    região/praça do número, operadora de origem do prefixo e tipo de linha.
 * 2. COMUNIDADE · feed público `community/reputation-feed.json` do
 *    repositório (denúncias revisadas). Espelhado para a base de reputação
 *    local, onde o Passo 4 do Spoof Detector e o Call Guard já consomem.
 * 3. PÚBLICO · deep links para ferramentas oficiais (ABR Telecom —
 *    portabilidade, Tellows, buscadores) abertas no navegador.
 *
 * Não há simulação: quando uma fonte não responde, o estado é "indisponível",
 * nunca um valor inventado.
 */
object OrangeLens {

    private const val FEED_URL =
        "https://raw.githubusercontent.com/Astaroth0c/ORANGE-SHIELD/main/community/reputation-feed.json"
    private const val PREFS = "orange_lens_prefs"
    private const val KEY_LAST_SYNC = "lastFeedSync"
    private const val SYNC_INTERVAL_MS = 6L * 60L * 60L * 1000L

    /** Inteligência offline do número (sem rede). */
    data class LensIntel(
        val e164: String,
        val valid: Boolean,
        val lineType: String,
        val region: String?,
        val originCarrier: String?,
        val country: String?
    )

    data class LensReport(
        val intel: LensIntel,
        val community: CommunityReportEntity?,
        val localTriages: Int,
        val lastLocalDecision: String?,
        val whatsappEvents: Int
    )

    sealed interface SyncResult {
        data class Synced(val entries: Int) : SyncResult
        data object Cached : SyncResult
        data class Unavailable(val reason: String) : SyncResult
    }

    // ------------------------------------------------------------------
    // 1 · Inteligência offline (datasets do Google, embarcados no APK)
    // ------------------------------------------------------------------

    fun offlineIntel(e164: String): LensIntel {
        val util = PhoneNumberUtil.getInstance()
        val geocoder = PhoneNumberOfflineGeocoder.getInstance()
        val carrierMapper = PhoneNumberToCarrierMapper.getInstance()
        val locale = Locale("pt", "BR")

        return try {
            val number: Phonenumber.PhoneNumber = util.parse(e164, "BR")
            val valid = util.isValidNumber(number)
            val type = util.getNumberType(number)
            LensIntel(
                e164 = e164,
                valid = valid,
                lineType = when (type) {
                    PhoneNumberUtil.PhoneNumberType.MOBILE -> "Móvel"
                    PhoneNumberUtil.PhoneNumberType.FIXED_LINE -> "Fixo"
                    PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE -> "Fixo ou móvel"
                    PhoneNumberUtil.PhoneNumberType.VOIP -> "VOIP"
                    PhoneNumberUtil.PhoneNumberType.TOLL_FREE -> "Gratuito (0800)"
                    PhoneNumberUtil.PhoneNumberType.PREMIUM_RATE -> "Tarifa premium"
                    PhoneNumberUtil.PhoneNumberType.SHARED_COST -> "Custo compartilhado"
                    PhoneNumberUtil.PhoneNumberType.VOICEMAIL -> "Correio de voz"
                    PhoneNumberUtil.PhoneNumberType.PAGER -> "Pager"
                    else -> "Desconhecido"
                },
                region = geocoder.getDescriptionForNumber(number, locale)?.takeIf { it.isNotBlank() },
                originCarrier = carrierMapper.getNameForNumber(number, locale)?.takeIf { it.isNotBlank() },
                country = util.getRegionCodeForNumber(number)
            )
        } catch (_: Exception) {
            LensIntel(
                e164 = e164,
                valid = false,
                lineType = "Desconhecido",
                region = null,
                originCarrier = null,
                country = null
            )
        }
    }

    // ------------------------------------------------------------------
    // 2 · Feed comunitário (repositório público, revisado pelo mantenedor)
    // ------------------------------------------------------------------

    /** Sincroniza o feed público e espelha na base de reputação do motor. */
    suspend fun syncCommunityFeed(context: Context, force: Boolean = false): SyncResult =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
            if (!force && System.currentTimeMillis() - lastSync < SYNC_INTERVAL_MS) {
                return@withContext SyncResult.Cached
            }

            val connection = (URL(FEED_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("User-Agent", "ORANGE-SHIELD-Android-Lens")
            }
            try {
                if (connection.responseCode == 404) {
                    return@withContext SyncResult.Unavailable(
                        "Feed comunitário ainda não publicado na branch main do repositório."
                    )
                }
                if (connection.responseCode !in 200..299) {
                    return@withContext SyncResult.Unavailable("Feed respondeu HTTP ${connection.responseCode}.")
                }
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                val entries = parseFeed(root.optJSONArray("entries") ?: JSONArray())
                val db = com.orangeshield.app.data.DatabaseProvider.getInstance(context)

                db.communityReportDao().clearAll()
                if (entries.isNotEmpty()) {
                    db.communityReportDao().upsertAll(entries)
                    mirrorIntoReputation(db, entries)
                }
                prefs.edit()
                    .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                    .apply()
                SyncResult.Synced(entries.size)
            } catch (e: Exception) {
                SyncResult.Unavailable(e.message ?: "Falha de rede ao sincronizar o feed.")
            } finally {
                connection.disconnect()
            }
        }

    /**
     * Espelha o feed na reputação local consumida pelo Passo 4 do
     * Spoof Detector — preservando denúncias locais mais fortes.
     */
    private suspend fun mirrorIntoReputation(
        db: AppDatabase,
        entries: List<CommunityReportEntity>
    ) {
        val dao = db.reputationDao()
        entries.forEach { entry ->
            val existing = dao.findByE164(entry.e164)
            val mergedReportCount = maxOf(existing?.reportCount ?: 0, entry.reportCount)
            val mergedFraud = (existing?.isConfirmedFraud ?: false) ||
                entry.classification == "fraud_confirmed"
            val mergedClassification = when {
                entry.classification in ENGINE_CLASSES -> entry.classification
                existing?.classification in ENGINE_CLASSES -> existing!!.classification
                else -> existing?.classification ?: "unknown"
            }
            val mergedConfidence = maxOf(existing?.confidence ?: 0.0, entry.confidence)
            dao.upsert(
                ReputationEntity(
                    e164 = entry.e164,
                    riskScore = maxOf(
                        existing?.riskScore ?: 0,
                        if (mergedFraud) 100 else (mergedReportCount * 10).coerceAtMost(100)
                    ),
                    classification = mergedClassification,
                    reportCount = mergedReportCount,
                    isConfirmedFraud = mergedFraud,
                    confidence = mergedConfidence,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private val ENGINE_CLASSES = setOf("robocall", "telemarketing", "collector", "verified_business")

    private fun parseFeed(array: JSONArray): List<CommunityReportEntity> {
        val now = System.currentTimeMillis()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val e164 = item.optString("e164").trim()
                if (!e164.startsWith("+") || e164.length < 8) continue
                add(
                    CommunityReportEntity(
                        e164 = e164,
                        classification = item.optString("classification", "spam_reports"),
                        reportCount = item.optInt("reportCount", 0),
                        confidence = item.optDouble("confidence", 0.5),
                        notes = item.optString("notes", ""),
                        updatedAt = item.optLong("reportedAt", now),
                        feedVersion = item.optString("version", "1")
                    )
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // 3 · Deep links públicos (consulta real no navegador)
    // ------------------------------------------------------------------

    fun abrTelecomUrl(): String =
        "https://consultanumero.abrtelecom.com.br/consultanumero/consulta/consultaHistoricoRecenteCtg"

    fun tellowsUrl(e164: String): String = "https://www.tellows.com.br/num/$e164"

    fun webSearchUrl(e164: String): String {
        val national = e164.replace("+55", "0").replace("+", "")
        val spaced = if (national.length >= 10) {
            val ddd = national.substring(0, 2)
            "(0${ddd}) ${national.substring(2)}"
        } else national
        return "https://www.google.com/search?q=" +
            java.net.URLEncoder.encode("\"$spaced\" OR \"$e164\"", "UTF-8")
    }

    fun reportIssueUrl(e164: String, reason: String): String {
        val title = java.net.URLEncoder.encode("[Reporte comunitário] $e164", "UTF-8")
        val body = java.net.URLEncoder.encode(
            "### Número\n`$e164`\n\n### Motivo\n$reason\n\n### Origem\nReporte enviado pelo app ORANGE SHIELD (ORANGE LENS).",
            "UTF-8"
        )
        return "https://github.com/Astaroth0c/ORANGE-SHIELD/issues/new?title=$title&body=$body"
    }
}
