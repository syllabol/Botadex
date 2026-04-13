package com.example.botadex.utils

import android.content.Context
import com.example.botadex.CropInfo
import org.json.JSONArray
import kotlin.String

object DatasetLoader {

    fun loadDataset(context: Context): List<CropInfo> {
        val jsonString = context.assets.open("dataset.json")
            .bufferedReader()
            .use { it.readText() }

        val jsonArray = JSONArray(jsonString)
        val list = mutableListOf<CropInfo>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)

            list.add(
                CropInfo(
                    name = obj.getString("name"),
                    scientificName = if (obj.has("scientific_name")) obj.getString("scientific_name") else null,
                    description = obj.getString("description"),
                    watering = obj.getString("watering"),
                    fertilization = obj.getString("fertilization"),
                    pestControl = obj.getString("pest_control"),
                    uses = obj.getString("uses")
                )
            )
        }

        return list
    }
}