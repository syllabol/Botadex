package com.example.botadex.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val cropName: String,
    val taskType: String,
    val date: String,
    val time: String,
    val timestamp: Long = 0L,
    val isCompleted: Boolean = false
)
