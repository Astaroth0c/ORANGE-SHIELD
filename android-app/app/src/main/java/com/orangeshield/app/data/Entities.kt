package com.orangeshield.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ORANGE SHIELD — entidades Room (base local on-device).
 *
 * Espelham o modelo do console web (Prisma) naquilo que o motor precisa:
 * histórico de triagens, reputação de números, listas pessoais e
 * eventos analisados do WhatsApp.
 */

/** Registro de cada chamada triada pelo SmartCall Engine (auditoria). */
@Entity(tableName = "call_events", indices = [Index("e164")])
data class CallEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val e164: String,
    val decision: String, // ALLOW | WARN | SILENCE | BLOCK
    val riskScore: Int,
    val category: String? = null,
    val evidencesJson: String = "[]",
    val spoofFlagsJson: String = "[]",
    val reason: String,
    val ruleVersion: String,
    val occurredAt: Long,
    @ColumnInfo(defaultValue = "''") val rawNumber: String = ""
)

/** Reputação local de um número (denúncias coletivas, classificação, fraude confirmada). */
@Entity(tableName = "reputation")
data class ReputationEntity(
    @PrimaryKey val e164: String,
    val riskScore: Int = 0,
    val classification: String = "unknown", // unknown | telemarketing | robocall | collector | verified_business
    val reportCount: Int = 0,
    val isConfirmedFraud: Boolean = false,
    /** Confiança das denúncias (0..1); padrão 0.5 — usado para ponderar as evidências. */
    val confidence: Double = 0.5,
    val updatedAt: Long = 0L
)

/** Lista pessoal de bloqueio. */
@Entity(tableName = "blocked_numbers")
data class BlockedEntity(
    @PrimaryKey val e164: String,
    val label: String? = null,
    val createdAt: Long
)

/** Lista de autorizados (prioridade máxima — nunca bloqueados). */
@Entity(tableName = "allowed_numbers")
data class AllowedEntity(
    @PrimaryKey val e164: String,
    val name: String? = null,
    val createdAt: Long
)

/** Evento de mensagem do WhatsApp analisado pelo Guardião. */
@Entity(tableName = "whatsapp_events", indices = [Index("sender")])
data class WhatsAppEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sender: String,
    val content: String,
    val decision: String, // ALLOW | QUARANTINE | BLOCK
    val riskScore: Int,
    val category: String,
    val reason: String,
    val occurredAt: Long
)
