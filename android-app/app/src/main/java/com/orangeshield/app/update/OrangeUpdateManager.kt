package com.orangeshield.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class OrangeRelease(
    val version: String,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val sha256: String
)

sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Available(val release: OrangeRelease) : UpdateCheck
}

class OrangeUpdateManager(private val context: Context) {
    companion object {
        private const val API = "https://api.github.com/repos/Astaroth0c/ORANGE-SHIELD/releases?per_page=30"
        private const val USER_AGENT = "ORANGE-SHIELD-Android-Updater"
    }

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        val installed = installedVersion()
        val connection = (URL(API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("GitHub respondeu HTTP ${connection.responseCode}. Tente novamente.")
            }
            val releases = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            var newest: OrangeRelease? = null
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                if (release.optBoolean("draft", false)) continue
                val tag = release.optString("tag_name")
                val version = tag.removePrefix("v").substringBefore("-build")
                if (!isNewer(version, installed)) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val name = asset.optString("name")
                    if (!name.endsWith("-release.apk")) continue
                    val digest = asset.optString("digest")
                    if (!digest.startsWith("sha256:")) {
                        throw IllegalStateException("A publicação não informou SHA-256 do APK. Download bloqueado por segurança.")
                    }
                    newest = OrangeRelease(
                        version = version,
                        tag = tag,
                        notes = release.optString("body").take(4000),
                        apkUrl = asset.optString("browser_download_url"),
                        sha256 = digest.removePrefix("sha256:").lowercase()
                    )
                    break
                }
                if (newest != null) break
            }
            newest?.let { UpdateCheck.Available(it) } ?: UpdateCheck.UpToDate
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadAndPromptInstall(release: OrangeRelease): Unit = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(updatesDir, "orange-shield-${release.version}.apk")
        val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Falha ao baixar APK: HTTP ${connection.responseCode}.")
            }
            connection.inputStream.use { input ->
                apk.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }

        val actual = MessageDigest.getInstance("SHA-256")
            .digest(apk.readBytes())
            .joinToString("") { "%02x".format(it) }
        if (!actual.equals(release.sha256, ignoreCase = true)) {
            apk.delete()
            throw SecurityException("O SHA-256 do APK não confere. Instalação cancelada.")
        }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            throw IllegalStateException("Autorize ORANGE-SHIELD a instalar atualizações nesta tela e toque em ATUALIZAR novamente.")
        }
        context.startActivity(installIntent)
    }

    private fun installedVersion(): String {
        @Suppress("DEPRECATION")
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    }

    private fun isNewer(candidate: String, installed: String): Boolean {
        fun parts(value: String) = value.split(".").map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av > bv
        }
        return false
    }
}
