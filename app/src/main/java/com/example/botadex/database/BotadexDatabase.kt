package com.example.botadex.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Crop::class, JournalEntry::class, Reminder::class],
    version = 4,
    exportSchema = false
)
abstract class BotadexDatabase : RoomDatabase() {
    abstract fun cropDao(): CropDao
}
