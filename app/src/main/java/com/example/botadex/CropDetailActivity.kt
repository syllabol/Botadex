package com.example.botadex

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CropDetailActivity : AppCompatActivity() {

    private lateinit var crop: CropInfo
    private lateinit var tabContentContainer: ViewGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_detail)

        val cropName = intent.getStringExtra("CROP_NAME") ?: ""
        val allCrops = loadCropData()
        crop = allCrops[cropName.lowercase()] ?: return

        // Set the name from intent if it's missing in the JSON object
        val displayName = if (crop.name.isNullOrEmpty()) {
            cropName.replaceFirstChar { it.uppercase() }
        } else {
            crop.name
        }

        findViewById<TextView>(R.id.cropNameText).text = displayName
        findViewById<TextView>(R.id.scientificNameText).text = crop.scientificName
        findViewById<TextView>(R.id.descriptionText).text = crop.description

        tabContentContainer = findViewById(R.id.tabContentContainer)

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateTabContent(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        // Initial setup
        setupCharacteristics()
        setupCareGuide()

        // Bottom Navigation
        findViewById<View>(R.id.navIdentify).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<View>(R.id.navJournal).setOnClickListener {
            startActivity(Intent(this, JournalActivity::class.java))
        }
        findViewById<View>(R.id.navCalendar).setOnClickListener {
            startActivity(Intent(this, RemindersActivity::class.java))
        }
    }

    private fun setupCharacteristics() {
        val container = findViewById<LinearLayout>(R.id.characteristicsContainer)
        container.removeAllViews()
        crop.characteristics?.forEach { characteristic ->
            val textView = TextView(this)
            textView.text = "● $characteristic"
            textView.setPadding(0, 4, 0, 4)
            textView.setTextColor(android.graphics.Color.BLACK)
            textView.typeface = ResourcesCompat.getFont(this, R.font.jockey_one)
            container.addView(textView)
        }
    }

    private fun updateTabContent(position: Int) {
        tabContentContainer.removeAllViews()
        when (position) {
            0 -> setupCareGuide()
            1 -> setupUses()
            2 -> setupGrowthStages()
        }
    }

    private fun setupCareGuide() {
        val view = LayoutInflater.from(this).inflate(R.layout.tab_care_guide, tabContentContainer, false)
        view.findViewById<TextView>(R.id.wateringText).text = crop.watering
        view.findViewById<TextView>(R.id.fertilizationText).text = crop.fertilization
        view.findViewById<TextView>(R.id.pestControlText).text = crop.pestControl
        view.findViewById<TextView>(R.id.harvestingText).text = crop.harvesting
        tabContentContainer.addView(view)
    }

    private fun setupUses() {
        val view = LayoutInflater.from(this).inflate(R.layout.tab_uses, tabContentContainer, false)
        val culinaryContainer = view.findViewById<LinearLayout>(R.id.culinaryContainer)
        val medicinalContainer = view.findViewById<LinearLayout>(R.id.medicinalContainer)

        crop.culinaryUses?.forEach { use ->
            val textView = TextView(this)
            textView.text = "● $use"
            textView.setPadding(0, 4, 0, 4)
            textView.setTextColor(android.graphics.Color.BLACK)
            textView.typeface = ResourcesCompat.getFont(this, R.font.jockey_one)
            culinaryContainer.addView(textView)
        }

        crop.medicinalUses?.forEach { use ->
            val textView = TextView(this)
            textView.text = "● $use"
            textView.setPadding(0, 4, 0, 4)
            textView.setTextColor(android.graphics.Color.BLACK)
            textView.typeface = ResourcesCompat.getFont(this, R.font.jockey_one)
            medicinalContainer.addView(textView)
        }
        tabContentContainer.addView(view)
    }

    private fun setupGrowthStages() {
        val view = LayoutInflater.from(this).inflate(R.layout.tab_growth_stages, tabContentContainer, false)
        val stagesContainer = view.findViewById<LinearLayout>(R.id.stagesContainer)

        val jockeyFont = ResourcesCompat.getFont(this, R.font.jockey_one)
        val interFont = ResourcesCompat.getFont(this, R.font.inter)

        crop.growthStages?.forEachIndexed { index, stage ->
            val stageView = LayoutInflater.from(this).inflate(R.layout.item_growth_stage, stagesContainer, false)
            
            val numberText = stageView.findViewById<TextView>(R.id.stageNumber)
            val titleText = stageView.findViewById<TextView>(R.id.stageTitle)
            val descriptionText = stageView.findViewById<TextView>(R.id.stageDescription)

            numberText.text = (index + 1).toString()
            numberText.typeface = jockeyFont

            titleText.text = stage.stage
            titleText.typeface = jockeyFont

            descriptionText.text = stage.description
            descriptionText.typeface = jockeyFont

            stagesContainer.addView(stageView)
        }
        tabContentContainer.addView(view)
    }

    private fun loadCropData(): Map<String, CropInfo> {
        val jsonString = assets.open("crop_library.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<Map<String, CropInfo>>() {}.type
        return Gson().fromJson(jsonString, type)
    }
}
