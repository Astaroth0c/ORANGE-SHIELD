package com.orangeshield.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * ORANGE SHIELD — DAOs Room.
 * Consultas determinísticas usadas pelo Call Guard, Spoof Detector,
 * WhatsApp Guard e pelo Painel (estatísticas).
 */

@Dao
interface CallEventDao {

    @Insert
    suspend fun insert(event: CallEventEntity): Long

    @Query("SELECT * FROM call_events WHERE e164 = :e164")
    suspend fun findByE164(e164: String): List<CallEventEntity>

    @Query("SELECT * FROM call_events WHERE e164 = :e164 ORDER BY occurredAt DESC LIMIT 50")
    suspend fun findRecentByE164(e164: String): List<CallEventEntity>

    @Query("SELECT * FROM call_events WHERE e164 = :e164 ORDER BY occurredAt DESC LIMIT 1")
    suspend fun findLatestByE164(e164: String): CallEventEntity?

    /** Rediscagem rápida / insistência: triagens do número dentro da janela. */
    @Query("SELECT COUNT(*) FROM call_events WHERE e164 = :e164 AND occurredAt >= :since")
    suspend fun countInWindow(e164: String, since: Long): Int

    /** Onda regional: total de triagens com o prefixo (ex.: +5511) dentro da janela. */
    @Query("SELECT COUNT(*) FROM call_events WHERE e164 LIKE :prefix || '%' AND occurredAt >= :since")
    suspend fun countByPrefixSince(prefix: String, since: Long): Int

    /** Onda regional: e164s triados com o prefixo dentro da janela (distinct calculado em Kotlin). */
    @Query("SELECT e164 FROM call_events WHERE e164 LIKE :prefix || '%' AND occurredAt >= :since")
    suspend fun listE164ByPrefixSince(prefix: String, since: Long): List<String>

    @Query("SELECT * FROM call_events ORDER BY occurredAt DESC LIMIT :limit")
    fun listRecent(limit: Int): Flow<List<CallEventEntity>>

    // ---- estatísticas do Painel ----

    @Query("SELECT COUNT(*) FROM call_events")
    fun countTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_events WHERE decision = :decision")
    fun countByDecision(decision: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_events WHERE category = :category")
    fun countByCategory(category: String): Flow<Int>

    @Query("DELETE FROM call_events WHERE e164 = :e164")
    suspend fun deleteByE164(e164: String)

    @Query("DELETE FROM call_events")
    suspend fun deleteAll()
}

@Dao
interface ReputationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reputation: ReputationEntity)

    @Query("SELECT * FROM reputation WHERE e164 = :e164")
    suspend fun findByE164(e164: String): ReputationEntity?

    @Query("SELECT * FROM reputation ORDER BY updatedAt DESC")
    suspend fun listAll(): List<ReputationEntity>

    @Query("DELETE FROM reputation WHERE e164 = :e164")
    suspend fun delete(e164: String)
}

@Dao
interface PhoneListDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlocked(entry: BlockedEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllowed(entry: AllowedEntity)

    @Query("SELECT * FROM blocked_numbers WHERE e164 = :e164")
    suspend fun findBlocked(e164: String): BlockedEntity?

    @Query("SELECT * FROM allowed_numbers WHERE e164 = :e164")
    suspend fun findAllowed(e164: String): AllowedEntity?

    @Query("SELECT * FROM blocked_numbers ORDER BY createdAt DESC")
    fun listBlocked(): Flow<List<BlockedEntity>>

    @Query("SELECT * FROM allowed_numbers ORDER BY createdAt DESC")
    fun listAllowed(): Flow<List<AllowedEntity>>

    @Query("DELETE FROM blocked_numbers WHERE e164 = :e164")
    suspend fun deleteBlocked(e164: String)

    @Query("DELETE FROM allowed_numbers WHERE e164 = :e164")
    suspend fun deleteAllowed(e164: String)
}

@Dao
interface WhatsAppEventDao {

    @Insert
    suspend fun insert(event: WhatsAppEventEntity): Long

    @Query("SELECT * FROM whatsapp_events WHERE sender = :sender")
    suspend fun findBySender(sender: String): List<WhatsAppEventEntity>

    /** Deduplicação por fingerprint SHA-256; o texto original não é persistido. */
    @Query(
        "SELECT COUNT(*) FROM whatsapp_events " +
            "WHERE sender = :sender AND content = :fingerprint AND occurredAt >= :since"
    )
    suspend fun countRecent(sender: String, fingerprint: String, since: Long): Int

    @Query("SELECT * FROM whatsapp_events ORDER BY occurredAt DESC LIMIT :limit")
    fun listRecent(limit: Int): Flow<List<WhatsAppEventEntity>>

    @Query("DELETE FROM whatsapp_events")
    suspend fun deleteAll()
}
