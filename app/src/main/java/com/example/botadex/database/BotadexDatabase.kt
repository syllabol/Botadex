package com.example.botadex.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Crop::class, JournalCollection::class, JournalEntry::class, Reminder::class],
    version = 5,
    exportSchema = false
)
abstract class BotadexDatabase : RoomDatabase() {
    abstract fun cropDao(): CropDao
}
