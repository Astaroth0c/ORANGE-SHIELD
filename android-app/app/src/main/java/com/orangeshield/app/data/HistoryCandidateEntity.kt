package com.orangeshield.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Agregação local do histórico nativo. Não é lista de bloqueio e não altera
 * registros, configurações ou decisões do discador do fabricante.
 */
@Entity(tableName = "history_candidates")
data class HistoryCandidateEntity(
    @PrimaryKey val e164: String,
    val totalCalls: Int,
    val missedCalls: Int,
    val rejectedCalls: Int,
    val blockedCalls: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val suggestedLabel: String,
    val reviewed: Boolean = false,
    val scannedAt: Long,
    @ColumnInfo(defaultValue = "''") val rawExamples: String = ""
)
