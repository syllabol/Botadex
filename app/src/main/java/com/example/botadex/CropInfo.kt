package com.example.botadex

import com.google.gson.annotations.SerializedName

data class CropInfo(
    val name: String? = null,
    @SerializedName("scientific_name")
    val scientificName: String? = "Scientific name",
    val description: String,
    val watering: String,
    val fertilization: String,
    @SerializedName("pest_control")
    val pestControl: String,
    val uses: String,
    val characteristics: List<String>? = emptyList(),
    @SerializedName("culinary_uses")
    val culinaryUses: List<String>? = emptyList(),
    @SerializedName("medicinal_uses")
    val medicinalUses: List<String>? = emptyList(),
    @SerializedName("growth_stages")
    val growthStages: List<GrowthStage>? = emptyList(),
    val harvesting: String? = ""
)

data class GrowthStage(
    val stage: String,
    val description: String
)
