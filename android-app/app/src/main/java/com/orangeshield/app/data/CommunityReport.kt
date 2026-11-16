package com.orangeshield.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * ORANGE LENS — reputação comunitária do ORANGE SHIELD.
 *
 * Espelha o feed público `community/reputation-feed.json` do repositório:
 * números denunciados por golpes/spam pela comunidade, revisados pelo
 * mantenedor. Alimenta o Passo 4 (Reputação) do Spoof Detector via
 * espelhamento para [ReputationEntity].
 */
@Entity(tableName = "community_reports", indices = [Index("e164")])
data class CommunityReportEntity(
    @PrimaryKey val e164: String,
    val classification: String, // fraud_confirmed | spam_reports | telemarketing | robocall | collector | verified_business
    val reportCount: Int,
    val confidence: Double,
    val notes: String,
    val updatedAt: Long,
    val feedVersion: String
)

@Dao
interface CommunityReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<CommunityReportEntity>)

    @Query("SELECT * FROM community_reports WHERE e164 = :e164")
    suspend fun findByE164(e164: String): CommunityReportEntity?

    @Query("SELECT COUNT(*) FROM community_reports")
    suspend fun countAll(): Int

    @Query("DELETE FROM community_reports")
    suspend fun clearAll()

    @Query("SELECT * FROM community_reports ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CommunityReportEntity>>
}
