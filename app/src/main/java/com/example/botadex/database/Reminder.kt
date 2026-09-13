package com.example.botadex.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val plantName: String, // Specifically the user-given plant name from the journal (e.g. "planterisms")
    val taskType: String,  // Includes task and day/stage (e.g. "Watering - Day 10")
    val date: String,
    val time: String,
    val timestamp: Long = 0L,
    val isCompleted: Boolean = false
)
