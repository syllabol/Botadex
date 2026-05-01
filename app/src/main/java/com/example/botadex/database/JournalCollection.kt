package com.example.botadex.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
data class JournalCollection(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val cropName: String,
    val date: String
)
