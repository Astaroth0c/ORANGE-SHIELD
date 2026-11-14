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
        HistoryCandidateEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callEventDao(): CallEventDao
    abstract fun reputationDao(): ReputationDao
    abstract fun phoneListDao(): PhoneListDao
    abstract fun whatsappEventDao(): WhatsAppEventDao
    abstract fun historyCandidateDao(): HistoryCandidateDao
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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
}
