package com.example.botadex.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crops")
data class Crop(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val description: String,
    val wateringSchedule: String,
    val fertilizerInfo: String
)
