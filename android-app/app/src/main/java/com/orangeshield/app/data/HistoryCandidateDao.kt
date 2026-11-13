package com.orangeshield.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryCandidateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<HistoryCandidateEntity>)

    @Query("SELECT * FROM history_candidates ORDER BY totalCalls DESC, lastSeenAt DESC")
    fun observeAll(): Flow<List<HistoryCandidateEntity>>

    @Query("SELECT COUNT(*) FROM history_candidates")
    fun countAll(): Flow<Int>

    @Query("DELETE FROM history_candidates")
    suspend fun clear()

    @Query("UPDATE history_candidates SET reviewed = 1 WHERE e164 = :number")
    suspend fun markReviewed(number: String)
}
