package com.example.botadex.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Crop::class,
        GrowthStageEntity::class,
        JournalCollection::class,
        JournalEntry::class,
        Reminder::class,
        AppMetadata::class,
        MaintenanceLog::class
    ],
    version = 24,
    exportSchema = true
)
@TypeConverters(DataConverters::class)
abstract class BotadexDatabase : RoomDatabase() {

    abstract fun cropDao(): BotadexDao

    companion object {
        private const val DATABASE_NAME = "botadex_db"

        @Volatile
        private var INSTANCE: BotadexDatabase? = null

        fun getDatabase(context: Context): BotadexDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BotadexDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
