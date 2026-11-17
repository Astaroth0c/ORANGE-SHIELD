package com.orangeshield.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * ORANGE SHIELD — banco local (Room), versão 1.
 * Sem schema exportado (exportSchema = false); migrations destructivas
 * futuras podem ser adicionadas conforme a evolução do motor.
 */
@Database(
    entities = [
        CallEventEntity::class,
        ReputationEntity::class,
        BlockedEntity::class,
        AllowedEntity::class,
        WhatsAppEventEntity::class,
        HistoryCandidateEntity::class,
        CommunityReportEntity::class,
        ChainBlockEntity::class,
        ChainPendingEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callEventDao(): CallEventDao
    abstract fun reputationDao(): ReputationDao
    abstract fun phoneListDao(): PhoneListDao
    abstract fun whatsappEventDao(): WhatsAppEventDao
    abstract fun historyCandidateDao(): HistoryCandidateDao
    abstract fun communityReportDao(): CommunityReportDao
    abstract fun chainBlockDao(): ChainBlockDao
    abstract fun chainPendingDao(): ChainPendingDao
}

/**
 * Singleton do banco — um único AppDatabase por processo.
 * [getInstance] é idempotente e seguro para uso concorrente
 * (MainActivity, CallScreeningService e NotificationListenerService).
 */

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS history_candidates (e164 TEXT NOT NULL PRIMARY KEY, totalCalls INTEGER NOT NULL, missedCalls INTEGER NOT NULL, rejectedCalls INTEGER NOT NULL, blockedCalls INTEGER NOT NULL, firstSeenAt INTEGER NOT NULL, lastSeenAt INTEGER NOT NULL, suggestedLabel TEXT NOT NULL, reviewed INTEGER NOT NULL, scannedAt INTEGER NOT NULL)")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE call_events ADD COLUMN rawNumber TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE history_candidates ADD COLUMN rawExamples TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE call_events ADD COLUMN networkVerificationStatus TEXT")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS community_reports (" +
                "e164 TEXT NOT NULL PRIMARY KEY, " +
                "classification TEXT NOT NULL, " +
                "reportCount INTEGER NOT NULL, " +
                "confidence REAL NOT NULL, " +
                "notes TEXT NOT NULL, " +
                "updatedAt INTEGER NOT NULL, " +
                "feedVersion TEXT NOT NULL)"
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_community_reports_e164 ON community_reports (e164)")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS chain_blocks (" +
                "`index` INTEGER NOT NULL PRIMARY KEY, " +
                "hash TEXT NOT NULL, " +
                "prevHash TEXT NOT NULL, " +
                "merkleRoot TEXT NOT NULL, " +
                "timestamp INTEGER NOT NULL, " +
                "publisher TEXT NOT NULL, " +
                "payloadJson TEXT NOT NULL, " +
                "receivedAt INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS chain_pending (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "e164 TEXT NOT NULL, " +
                "classification TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL)"
        )
    }
}

object DatabaseProvider {

    @Volatile
    private var instance: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "orange_shield.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
        }
}
