package com.example.botadex

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CropDetailActivity : AppCompatActivity() {

    private var crop: CropInfo? = null
    private lateinit var tabContentContainer: FrameLayout
    private var isDescriptionExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_detail)

        val cropName = intent.getStringExtra("CROP_NAME") ?: return
        val allCrops = loadCropData()
        crop = allCrops[cropName.lowercase()] ?: return

        setupHeader(cropName)
        setupDescription()
        setupTabs()

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        // Bottom Navigation
        findViewById<View>(R.id.navHome).setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }

        findViewById<View>(R.id.navLibrary).setOnClickListener {
            startActivity(Intent(this, CropLibraryActivity::class.java))
        }
        findViewById<View>(R.id.navJournal).setOnClickListener {
            startActivity(Intent(this, JournalActivity::class.java))
        }
        findViewById<View>(R.id.navCalendar).setOnClickListener {
            startActivity(Intent(this, RemindersActivity::class.java))
        }

        // Initial tab content
        updateTabContent(0)
    }

    private fun setupHeader(cropName: String) {
        val displayName = if (crop?.name.isNullOrEmpty()) {
            cropName.replaceFirstChar { it.uppercase() }
        } else {
            crop?.name ?: ""
        }

        findViewById<TextView>(R.id.cropNameText).text = displayName
        findViewById<TextView>(R.id.scientificNameText).text = crop?.scientificName ?: ""
        
        val thumbnail = findViewById<ImageView>(R.id.cropThumbnail)
        val fullImage = findViewById<ImageView>(R.id.cropFullImage)

        val iconId = when (displayName.lowercase()) {
            "cassava" -> R.drawable.cassava
            "tomato" -> R.drawable.tomato
            "potato" -> R.drawable.potato
            "ube" -> R.drawable.ube
            "kamote" -> R.drawable.kamote
            "onion" -> R.drawable.onion
            else -> R.drawable.ic_launcher_foreground
        }

        val leafId = when (displayName.lowercase()) {
            "cassava" -> R.drawable.cassavaleaf
            "tomato" -> R.drawable.tomatoleaf
            "potato" -> R.drawable.potatoleaf
            "ube" -> R.drawable.ubeleaf
            "kamote" -> R.drawable.kamoteleaf
            "onion" -> R.drawable.onionleaf
            else -> R.drawable.ic_launcher_background
        }


        thumbnail.setImageResource(iconId)
        fullImage.setImageResource(leafId)
    }

    private fun setupDescription() {
        val descriptionText = findViewById<TextView>(R.id.descriptionText)
        val seeMoreText = findViewById<TextView>(R.id.seeMoreText)

        descriptionText.text = crop?.description ?: ""

        val toggleDescription = {
            if (isDescriptionExpanded) {
                descriptionText.maxLines = 2
                seeMoreText.text = getString(R.string.see_more)
            } else {
                descriptionText.maxLines = Int.MAX_VALUE
                seeMoreText.text = getString(R.string.see_less)
            }
            isDescriptionExpanded = !isDescriptionExpanded
        }

        descriptionText.setOnClickListener { toggleDescription() }
        seeMoreText.setOnClickListener { toggleDescription() }
    }

    private fun setupTabs() {
        tabContentContainer = findViewById(R.id.tabContentContainer)
        val tabCare = findViewById<TextView>(R.id.tabCareGuide)
        val tabUses = findViewById<TextView>(R.id.tabCropUses)
        val tabStages = findViewById<TextView>(R.id.tabGrowthStages)
        val indicator = findViewById<View>(R.id.activeTabIndicator)

        val tabs = listOf(tabCare, tabUses, tabStages)
        val lines = listOf(
            findViewById<View>(R.id.lineTab1),
            findViewById<View>(R.id.lineTab2),
            findViewById<View>(R.id.lineTab3)
        )

        tabs.forEachIndexed { index, textView ->
            textView.setOnClickListener {
                updateTabUI(index, tabs, lines, indicator)
                updateTabContent(index)
            }
        }
    }

    private fun updateTabUI(selectedIndex: Int, tabs: List<TextView>, lines: List<View>, indicator: View) {
        val activeColor = ContextCompat.getColor(this, R.color.brand_green)
        val inactiveColor = ContextCompat.getColor(this, R.color.nav_inactive)
        val lineActiveColor = ContextCompat.getColor(this, R.color.brand_green)
        val lineInactiveColor = ContextCompat.getColor(this, R.color.line_inactive)

        tabs.forEachIndexed { index, textView ->
            if (index == selectedIndex) {
                textView.setTextColor(activeColor)
                lines[index].setBackgroundColor(lineActiveColor)
                
                val params = indicator.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                params.startToStart = textView.id
                params.endToEnd = textView.id
                indicator.layoutParams = params
            } else {
                textView.setTextColor(inactiveColor)
                lines[index].setBackgroundColor(lineInactiveColor)
            }
        }
    }

    private fun updateTabContent(position: Int) {
        tabContentContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        
        when (position) {
            0 -> {
                val view = inflater.inflate(R.layout.tab_care_guide, tabContentContainer, false)
                val recyclerView = view.findViewById<RecyclerView>(R.id.careGuideRecycler)
                val items = listOf(
                    DetailItem(getString(R.string.watering), crop?.watering ?: ""),
                    DetailItem(getString(R.string.fertilization), crop?.fertilization ?: ""),
                    DetailItem(getString(R.string.pest_control), crop?.pestControl ?: ""),
                    DetailItem(getString(R.string.harvesting), crop?.harvesting ?: "")
                )
                recyclerView.layoutManager = LinearLayoutManager(this)
                recyclerView.adapter = DetailItemAdapter(items)
                tabContentContainer.addView(view)
            }
            1 -> {
                val view = inflater.inflate(R.layout.tab_uses, tabContentContainer, false)
                val recyclerView = view.findViewById<RecyclerView>(R.id.cropUsesRecycler)
                
                val items = mutableListOf<DetailItem>()
                
                // Culinary section (At least 4 items)
                items.add(DetailItem(getString(R.string.culinary), ""))
                val culinary = crop?.culinaryUses ?: emptyList()
                for (i in 0 until maxOf(4, culinary.size)) {
                    val title = if (i < culinary.size) "Culinary Use ${i + 1}" else "Additional Use"
                    val desc = culinary.getOrNull(i) ?: "Learn more in the field guide."
                    items.add(DetailItem(title, desc))
                }
                
                // Medicinal section (At least 4 items)
                items.add(DetailItem(getString(R.string.medicinal), ""))
                val medicinal = crop?.medicinalUses ?: emptyList()
                for (i in 0 until maxOf(4, medicinal.size)) {
                    val title = if (i < medicinal.size) "Medicinal Use ${i + 1}" else "Additional Remedy"
                    val desc = medicinal.getOrNull(i) ?: "Traditional uses vary by region."
                    items.add(DetailItem(title, desc))
                }

                recyclerView.layoutManager = LinearLayoutManager(this)
                recyclerView.adapter = DetailItemAdapter(items)
                tabContentContainer.addView(view)
            }
            2 -> {
                val view = inflater.inflate(R.layout.tab_growth_stages, tabContentContainer, false)
                val recyclerView = view.findViewById<RecyclerView>(R.id.growthStagesRecycler)
                
                val stages = crop?.growthStages ?: emptyList()
                recyclerView.layoutManager = LinearLayoutManager(this)
                recyclerView.adapter = GrowthStagesAdapter(stages)
                tabContentContainer.addView(view)
            }
        }
    }

    private fun loadCropData(): Map<String, CropInfo> {
        return try {
            val jsonString = assets.open("crop_library.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, CropInfo>>() {}.type
            Gson().fromJson(jsonString, type)
        } catch (e: Exception) {
            emptyMap()
        }
    }
}