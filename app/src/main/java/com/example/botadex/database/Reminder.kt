package com.example.botadex.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val cropName: String,
    val taskType: String, // e.g., "Water plants", "Apply fertilizer"
    val date: String,     // e.g., "March 15"
    val time: String      // e.g., "7:00 am"
)
