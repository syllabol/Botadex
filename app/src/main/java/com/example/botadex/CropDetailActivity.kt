package com.example.botadex

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.botadex.admin.AdminDashboardActivity
import com.example.botadex.database.BotadexDatabase
import com.example.botadex.database.Crop
import com.example.botadex.database.GrowthStageEntity
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class CropDetailActivity : AppCompatActivity() {

    private var crop: Crop? = null
    private var stages: List<GrowthStageEntity> = emptyList()
    private lateinit var tabContentContainer: FrameLayout
    private var isDescriptionExpanded = false
    private var adminTapCount = 0
    private var lastTapTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_detail)

        val cropName = intent.getStringExtra("CROP_NAME") ?: return
        
        tabContentContainer = findViewById(R.id.tabContentContainer)
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

        setupAdminShortcut()
        loadCropData(cropName)
    }

    private fun loadCropData(name: String) {
        lifecycleScope.launch {
            val db = BotadexDatabase.getDatabase(this@CropDetailActivity)
            crop = db.cropDao().getCropByName(name.lowercase())
            stages = db.cropDao().getGrowthStagesForCrop(name.lowercase())

            if (crop != null) {
                setupHeader()
                setupDescription()
                setupTabs()
                updateTabContent(0)
            } else {
                Toast.makeText(this@CropDetailActivity, "Crop not found in database", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun setupAdminShortcut() {
        val title = findViewById<TextView>(R.id.tvCropDetailTitle)
        title.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < 500) {
                adminTapCount++
            } else {
                adminTapCount = 1
            }
            lastTapTime = currentTime

            if (adminTapCount >= 3) {
                adminTapCount = 0
                startActivity(Intent(this, AdminDashboardActivity::class.java))
                Toast.makeText(this, "Admin Mode Entered", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupHeader() {
        val displayName = crop?.name?.replaceFirstChar { it.uppercase() } ?: ""

        findViewById<TextView>(R.id.cropNameText).text = displayName
        findViewById<TextView>(R.id.scientificNameText).text = crop?.scientificName ?: ""
        
        val thumbnail = findViewById<ImageView>(R.id.cropThumbnail)
        val imagePager = findViewById<ViewPager2>(R.id.cropImagePager)
        val imageIndicator = findViewById<TabLayout>(R.id.cropImageIndicator)

        val imageResIds = mutableListOf<Int>()

        // Load gallery images (up to 5)
        crop?.galleryImages?.take(5)?.forEach { uri ->
            val resId = resources.getIdentifier(uri, "drawable", packageName)
            if (resId != 0) imageResIds.add(resId)
        }

        // Fallback to imageUri if gallery is empty
        if (imageResIds.isEmpty()) {
            val mainImageResId = if (!crop?.imageUri.isNullOrBlank()) {
                resources.getIdentifier(crop?.imageUri, "drawable", packageName)
            } else {
                0
            }
            if (mainImageResId != 0) {
                imageResIds.add(mainImageResId)
            }
        }

        // Hardcoded Fallbacks if still empty
        if (imageResIds.isEmpty()) {
            val leafId = when (displayName.lowercase()) {
                "cassava" -> R.drawable.cassavaleaf
                "tomato" -> R.drawable.tomatoleaf
                "potato" -> R.drawable.potatoleaf
                "ube" -> R.drawable.ubeleaf
                "kamote" -> R.drawable.kamoteleaf
                "onion" -> R.drawable.onionleaf
                else -> R.drawable.ic_launcher_background
            }
            imageResIds.add(leafId)
        }

        // Setup ViewPager
        imagePager.adapter = CropImageAdapter(imageResIds)
        
        // Setup Indicator
        if (imageResIds.size > 1) {
            imageIndicator.visibility = View.VISIBLE
            TabLayoutMediator(imageIndicator, imagePager) { _, _ -> }.attach()
        } else {
            imageIndicator.visibility = View.GONE
        }

        // Setup Thumbnail
        val thumbnailResId = if (!crop?.imageUri.isNullOrBlank()) {
            resources.getIdentifier(crop?.imageUri, "drawable", packageName)
        } else {
            when (displayName.lowercase()) {
                "cassava" -> R.drawable.cassava
                "tomato" -> R.drawable.tomato
                "potato" -> R.drawable.potato
                "ube" -> R.drawable.ube
                "kamote" -> R.drawable.kamote
                "onion" -> R.drawable.onion
                else -> R.drawable.ic_launcher_foreground
            }
        }
        thumbnail.setImageResource(thumbnailResId)
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
                val cropName = crop?.name?.lowercase() ?: ""
                val culinaryIcon = R.drawable.ic_culinary
                val medicinalIcon = R.drawable.ic_medicinal

                // 2. Culinary section
                // Passing the specific icon to the header
                items.add(DetailItem(getString(R.string.culinary), "", culinaryIcon))
                
                val culinary = crop?.culinaryUses ?: emptyList()
                if (culinary.isEmpty()) {
                    items.add(DetailItem("Uses", crop?.uses ?: "No information available.", culinaryIcon))
                } else {
                    culinary.forEachIndexed { i, use ->
                        // Passing the specific icon to each list item
                        items.add(DetailItem("Culinary Use ${i + 1}", use, culinaryIcon))
                    }
                }
                
                // 3. Medicinal section
                items.add(DetailItem(getString(R.string.medicinal), "", medicinalIcon))
                
                val medicinal = crop?.medicinalUses ?: emptyList()
                medicinal.forEachIndexed { i, use ->
                    items.add(DetailItem("Medicinal Use ${i + 1}", use, medicinalIcon))
                }

                recyclerView.layoutManager = LinearLayoutManager(this)
                recyclerView.adapter = DetailItemAdapter(items)
                tabContentContainer.addView(view)
            }
            2 -> {
                val view = inflater.inflate(R.layout.tab_growth_stages, tabContentContainer, false)
                val recyclerView = view.findViewById<RecyclerView>(R.id.growthStagesRecycler)
                
                // Convert GrowthStageEntity to GrowthStage (UI model)
                val displayStages = stages.map { 
                    com.example.botadex.GrowthStage(it.stage, it.description, it.durationDays) 
                }
                recyclerView.layoutManager = LinearLayoutManager(this)
                recyclerView.adapter = GrowthStagesAdapter(displayStages)
                tabContentContainer.addView(view)
            }
        }
    }
}
