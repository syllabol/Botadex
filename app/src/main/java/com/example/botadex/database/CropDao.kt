package com.example.botadex.database

import androidx.room.*
import com.example.botadex.CropInfo

@Dao
interface CropDao {

    @Insert
    suspend fun insertCrop(crop: Crop)

    @Query("SELECT * FROM crops")
    suspend fun getAllCrops(): List<Crop>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournal(entry: JournalEntry)

    @Query("SELECT * FROM journal")
    suspend fun getAllJournal(): List<JournalEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder)

    @Query("SELECT * FROM reminders")
    suspend fun getAllReminders(): List<Reminder>

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    @Delete
    suspend fun deleteJournalEntry(entry: JournalEntry)
}
