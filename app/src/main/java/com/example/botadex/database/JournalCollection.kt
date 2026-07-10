package com.example.botadex.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
data class JournalCollection(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String, // User generated Plant Name
    val cropName: String, // Species name (e.g. Onion)
    val date: String, // Planted date MMMM DD YYYY
    val status: String = "Growing", // Growing, Harvested, Archived
    val healthStatus: String = "Healthy", // Healthy, Attention, Warning
    val currentDay: Int = 1,
    val targetDays: Int = 30,
    val imagePath: String? = null,
    val lastInteractionDate: Long = System.currentTimeMillis()
)
