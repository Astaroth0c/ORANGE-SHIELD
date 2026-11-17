package com.orangeshield.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * ORANGE CHAIN — persistência local da cadeia comunitária.
 *
 * [ChainBlockEntity] guarda cada bloco validado (payload serializado em
 * JSON); [ChainPendingEntity] guarda denúncias locais aguardando publicação.
 */
@Entity(tableName = "chain_blocks")
data class ChainBlockEntity(
    @PrimaryKey val index: Long,
    val hash: String,
    val prevHash: String,
    val merkleRoot: String,
    val timestamp: Long,
    val publisher: String,
    val payloadJson: String,
    val receivedAt: Long
)

@Entity(tableName = "chain_pending")
data class ChainPendingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val e164: String,
    val classification: String,
    val createdAt: Long
)

@Dao
interface ChainBlockDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlock(block: ChainBlockEntity)

    @Query("SELECT * FROM chain_blocks ORDER BY `index` ASC")
    suspend fun listByIndexAsc(): List<ChainBlockEntity>

    @Query("SELECT COALESCE(MAX(`index`), -1) FROM chain_blocks")
    suspend fun height(): Long

    @Query("SELECT COUNT(*) FROM chain_blocks")
    suspend fun countAll(): Int

    @Query("SELECT receivedAt FROM chain_blocks ORDER BY `index` DESC LIMIT 1")
    suspend fun lastReceivedAt(): Long?
}

@Dao
interface ChainPendingDao {

    @Insert
    suspend fun insert(entry: ChainPendingEntity): Long

    @Query("SELECT * FROM chain_pending ORDER BY createdAt ASC")
    suspend fun listAll(): List<ChainPendingEntity>

    @Query("SELECT COUNT(*) FROM chain_pending")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM chain_pending WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM chain_pending")
    suspend fun clearAll()
}
