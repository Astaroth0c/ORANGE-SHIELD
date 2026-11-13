package com.orangeshield.app.history

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.orangeshield.app.data.HistoryCandidateDao
import com.orangeshield.app.data.HistoryCandidateEntity
import com.orangeshield.app.engine.normalizePhone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanSummary(
    val scannedRows: Int,
    val uniqueNumbers: Int,
    val repeatedNumbers: Int,
    val blockedRows: Int
)

/**
 * Importação somente de leitura: consulta o CallLog.Provider e grava apenas
 * agregados por número no banco privado do ORANGE SHIELD. Não toca no bloqueio
 * nativo, não apaga chamadas e não transforma candidatos em bloqueios.
 */
class CallHistoryScanner(
    private val context: Context,
    private val dao: HistoryCandidateDao
) {
    suspend fun scan(): ScanSummary = withContext(Dispatchers.IO) {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED) { "Permissão de histórico de chamadas não concedida." }

        data class Aggregate(
            var total: Int = 0,
            var missed: Int = 0,
            var rejected: Int = 0,
            var blocked: Int = 0,
            var first: Long = Long.MAX_VALUE,
            var last: Long = 0L
        )

        val grouped = linkedMapOf<String, Aggregate>()
        var rows = 0
        var blockedRows = 0
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE
        )
        val uri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter("limit", "5000")
            .build()

        context.contentResolver.query(
            uri,
            projection,
            "${CallLog.Calls.TYPE} IN (?, ?, ?, ?)",
            arrayOf(
                CallLog.Calls.INCOMING_TYPE.toString(),
                CallLog.Calls.MISSED_TYPE.toString(),
                CallLog.Calls.REJECTED_TYPE.toString(),
                CallLog.Calls.BLOCKED_TYPE.toString()
            ),
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
            val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
            while (cursor.moveToNext()) {
                val raw = if (numberIndex >= 0) cursor.getString(numberIndex) else null
                val number = normalizePhone(raw)
                if (number.isBlank() || number.length < 5) continue
                val date = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L
                val type = if (typeIndex >= 0) cursor.getInt(typeIndex) else -1
                val item = grouped.getOrPut(number) { Aggregate() }
                item.total++
                item.first = minOf(item.first, date)
                item.last = maxOf(item.last, date)
                when (type) {
                    CallLog.Calls.MISSED_TYPE -> item.missed++
                    CallLog.Calls.REJECTED_TYPE -> item.rejected++
                    CallLog.Calls.BLOCKED_TYPE -> {
                        item.blocked++
                        blockedRows++
                    }
                }
                rows++
            }
        }

        val now = System.currentTimeMillis()
        val candidates = grouped.map { (number, item) ->
            val label = when {
                number.startsWith("+550303") -> "Prefixo 0303 · telemarketing"
                item.total >= 8 -> "Alta recorrência · revisar"
                item.total >= 4 && item.missed >= 3 -> "Insistência provável · revisar"
                item.blocked > 0 -> "Já consta como bloqueado no aparelho"
                else -> "Número recorrente ou desconhecido"
            }
            HistoryCandidateEntity(
                e164 = number,
                totalCalls = item.total,
                missedCalls = item.missed,
                rejectedCalls = item.rejected,
                blockedCalls = item.blocked,
                firstSeenAt = if (item.first == Long.MAX_VALUE) 0L else item.first,
                lastSeenAt = item.last,
                suggestedLabel = label,
                scannedAt = now
            )
        }
        if (candidates.isNotEmpty()) dao.upsertAll(candidates)
        ScanSummary(
            scannedRows = rows,
            uniqueNumbers = grouped.size,
            repeatedNumbers = grouped.values.count { it.total > 1 },
            blockedRows = blockedRows
        )
    }
}
