package com.example.botadex.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val collectionId: Int, // Link to JournalCollection
    val cropName: String,
    val notes: String,
    val date: String,
    val imagePaths: String // Comma-separated list of image paths
)
