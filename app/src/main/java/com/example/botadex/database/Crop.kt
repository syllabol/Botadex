package com.example.botadex.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crops")
data class Crop(
    @PrimaryKey val name: String,
    val scientificName: String? = null,
    val description: String,
    val watering: String,
    val fertilization: String,
    val pestControl: String,
    val uses: String,
    val characteristics: List<String> = emptyList(),
    val culinaryUses: List<String> = emptyList(),
    val medicinalUses: List<String> = emptyList(),
    val harvesting: String? = null
)
