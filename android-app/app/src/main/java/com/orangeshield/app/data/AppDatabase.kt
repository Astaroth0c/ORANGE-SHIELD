package com.orangeshield.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        WhatsAppEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callEventDao(): CallEventDao
    abstract fun reputationDao(): ReputationDao
    abstract fun phoneListDao(): PhoneListDao
    abstract fun whatsappEventDao(): WhatsAppEventDao
}

/**
 * Singleton do banco — um único AppDatabase por processo.
 * [getInstance] é idempotente e seguro para uso concorrente
 * (MainActivity, CallScreeningService e NotificationListenerService).
 */
object DatabaseProvider {

    @Volatile
    private var instance: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "orange_shield.db"
            ).build().also { instance = it }
        }
}
