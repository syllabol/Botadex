package com.example.botadex

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.botadex.database.BotadexDatabase
import com.example.botadex.database.JournalCollection
import com.example.botadex.database.JournalEntry
import com.example.botadex.database.Reminder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class JournalActivity : AppCompatActivity() {

    private lateinit var rvMyCrops: RecyclerView
    private lateinit var rvRecentJournal: RecyclerView
    private lateinit var myCropsAdapter: MyCropsAdapter
    private lateinit var recentJournalAdapter: RecentJournalAdapter
    private lateinit var db: BotadexDatabase

    private var currentFilter = "All"
    private var prefillImagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_journal)

        db = BotadexDatabase.getDatabase(this)

        setupRecyclerViews()
        setupListeners()
        setupTabs()
        observeData()

        val prefillCrop = intent.getStringExtra("PREFILL_CROP_NAME")
        prefillImagePath = intent.getStringExtra("PREFILL_IMAGE_PATH")
        if (prefillCrop != null) {
            showAddCollectionDialog(prefillCrop)
        } else {
            lifecycleScope.launch {
                val collections = db.cropDao().getAllCollectionsOnce()
                if (collections.isEmpty()) {
                    showAddCollectionDialog()
                }
            }
        }
    }

    private fun setupRecyclerViews() {
        rvMyCrops = findViewById(R.id.rvMyCrops)
        rvMyCrops.layoutManager = LinearLayoutManager(this)
        myCropsAdapter = MyCropsAdapter { collection ->
            val intent = Intent(this, CollectionDetailActivity::class.java)
            intent.putExtra("COLLECTION_ID", collection.id)
            intent.putExtra("COLLECTION_TITLE", collection.title)
            startActivity(intent)
        }
        rvMyCrops.adapter = myCropsAdapter

        // Horizontal Swipe to Delete for My Crops (Optional, keeping as fallback)
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val collection = myCropsAdapter.getCollectionAt(position)
                showDeleteCollectionDialog(collection, position)
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(rvMyCrops)

        rvRecentJournal = findViewById(R.id.rvRecentJournal)
        rvRecentJournal.layoutManager = LinearLayoutManager(this)
        recentJournalAdapter = RecentJournalAdapter()
        rvRecentJournal.adapter = recentJournalAdapter
    }

    private fun showDeleteCollectionDialog(collection: JournalCollection, position: Int, motionLayout: MotionLayout? = null) {
        AlertDialog.Builder(this)
            .setTitle("Delete Collection")
            .setMessage("Are you sure you want to delete \"${collection.title}\" and all its journal entries?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val entries = db.cropDao().getJournalEntriesByCollection(collection.id)
                    entries.forEach { db.cropDao().deleteJournalEntry(it) }
                    db.cropDao().deleteCollection(collection)
                    Toast.makeText(this@JournalActivity, "Collection deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                motionLayout?.transitionToStart()
                myCropsAdapter.notifyItemChanged(position)
            }
            .setOnCancelListener {
                motionLayout?.transitionToStart()
                myCropsAdapter.notifyItemChanged(position)
            }
            .show()
    }

    private fun setupListeners() {
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAddCrop).setOnClickListener { showAddCollectionDialog() }
        findViewById<View>(R.id.btnViewAll).setOnClickListener {
            val intent = Intent(this, AllJournalEntriesActivity::class.java)
            startActivity(intent)
        }

        findViewById<View>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
        }
        findViewById<View>(R.id.navLibrary).setOnClickListener {
            startActivity(Intent(this, CropLibraryActivity::class.java))
        }
        findViewById<View>(R.id.navJournal).setOnClickListener {
            // Already here
        }
        findViewById<View>(R.id.navCalendar).setOnClickListener {
            startActivity(Intent(this, RemindersActivity::class.java))
        }
        findViewById<View>(R.id.navIdentify).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun setupTabs() {
        val tabAll = findViewById<TextView>(R.id.tabAll)
        val tabGrowing = findViewById<TextView>(R.id.tabGrowing)
        val tabHarvested = findViewById<TextView>(R.id.tabHarvested)
        val tabArchived = findViewById<TextView>(R.id.tabArchived)

        val tabs = listOf(tabAll, tabGrowing, tabHarvested, tabArchived)

        tabs.forEach { tab ->
            tab.setOnClickListener {
                currentFilter = tab.text.toString()
                updateTabUI(tabs, tab)
                refreshMyCrops()
            }
        }
    }

    private fun updateTabUI(tabs: List<TextView>, selectedTab: TextView) {
        tabs.forEach {
            it.setBackgroundResource(0)
            it.setTextColor(ContextCompat.getColor(this, R.color.text_green_dark))
        }
        selectedTab.setBackgroundResource(R.drawable.bg_tab_selected)
        selectedTab.setTextColor(ContextCompat.getColor(this, R.color.white))
    }

    private fun observeData() {
        lifecycleScope.launch {
            db.cropDao().getAllCollectionsFlow().collectLatest { collections ->
                refreshMyCrops(collections)
            }
        }

        lifecycleScope.launch {
            db.cropDao().getAllJournalFlow().collectLatest { entries ->
                recentJournalAdapter.setEntries(entries.take(3))
            }
        }
    }

    private fun refreshMyCrops(allCollections: List<JournalCollection>? = null) {
        lifecycleScope.launch {
            val collections = allCollections ?: db.cropDao().getAllCollectionsOnce()
            
            collections.forEach { collection ->
                updateCollectionLogic(collection)
            }

            val filtered = if (currentFilter == "All") {
                collections
            } else {
                collections.filter { it.status.equals(currentFilter, ignoreCase = true) }
            }
            myCropsAdapter.setCollections(filtered)
        }
    }

    private fun updateCollectionLogic(collection: JournalCollection) {
        var updated = collection
        var needsUpdate = false

        try {
            val sdf = SimpleDateFormat("MMMM dd yyyy", Locale.getDefault())
            val plantedDate = sdf.parse(collection.date)
            if (plantedDate != null) {
                val diff = Date().time - plantedDate.time
                val days = (diff / (1000 * 60 * 60 * 24)).toInt() + 1
                if (days != collection.currentDay && days > 0) {
                    updated = updated.copy(currentDay = days)
                    needsUpdate = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val lastInteraction = collection.lastInteractionDate
        val diffInteraction = System.currentTimeMillis() - lastInteraction
        val daysSinceInteraction = (diffInteraction / (1000 * 60 * 60 * 24)).toInt()

        val newHealth = when {
            daysSinceInteraction >= 7 -> "Warning"
            daysSinceInteraction >= 3 -> "Attention"
            else -> "Healthy"
        }

        if (newHealth != updated.healthStatus) {
            updated = updated.copy(healthStatus = newHealth)
            needsUpdate = true
        }

        if (needsUpdate) {
            lifecycleScope.launch {
                db.cropDao().insertCollection(updated)
            }
        }
    }

    private fun showAddCollectionDialog(prefilledCrop: String? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_collection, null)
        val editTitle = dialogView.findViewById<EditText>(R.id.editCollectionTitle)
        val editCrop = dialogView.findViewById<EditText>(R.id.editCropName)
        
        if (prefilledCrop != null) {
            editCrop.setText(prefilledCrop)
        }
        
        AlertDialog.Builder(this)
            .setTitle("New Crop")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val title = editTitle.text.toString()
                val crop = editCrop.text.toString()
                if (title.isNotEmpty()) {
                    createCollection(title, crop)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createCollection(title: String, cropName: String) {
        val date = SimpleDateFormat("MMMM dd yyyy", Locale.getDefault()).format(Date())
        
        var targetDays = 30
        try {
            val jsonString = assets.open("crop_library.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, CropInfo>>() {}.type
            val map: Map<String, CropInfo> = Gson().fromJson(jsonString, type)
            targetDays = when(cropName.lowercase()) {
                "tomato" -> 60
                "onion" -> 100
                "potato" -> 90
                "cassava" -> 240
                "kamote" -> 120
                else -> 30
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val collection = JournalCollection(
            title = title, 
            cropName = cropName, 
            date = date,
            status = "Growing",
            healthStatus = "Healthy",
            currentDay = 1,
            targetDays = targetDays,
            imagePath = prefillImagePath,
            lastInteractionDate = System.currentTimeMillis()
        )
        lifecycleScope.launch {
            val id = db.cropDao().insertCollection(collection).toInt()
            
            val firstEntry = JournalEntry(
                id = 0,
                collectionId = id,
                cropName = title, 
                notes = if (prefillImagePath != null) "Initial documentation from identification." else "Collection started.",
                date = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date()),
                imagePaths = prefillImagePath ?: "",
                dayCount = 1,
                timestamp = System.currentTimeMillis()
            )
            db.cropDao().insertJournalEntry(firstEntry)
            
            autoScheduleReminders(cropName)

            Toast.makeText(this@JournalActivity, "Collection and first entry created!", Toast.LENGTH_SHORT).show()
            prefillImagePath = null 
            
            val intent = Intent(this@JournalActivity, CollectionDetailActivity::class.java)
            intent.putExtra("COLLECTION_ID", id)
            intent.putExtra("COLLECTION_TITLE", title)
            startActivity(intent)
        }
    }

    private suspend fun autoScheduleReminders(cropName: String) {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())

        val existingWatering = db.cropDao().getExistingReminders(cropName, "Watering")
        if (existingWatering.isEmpty()) {
            for (i in 1..7) {
                calendar.add(Calendar.DAY_OF_YEAR, 2)
                val reminder = Reminder(
                    cropName = cropName,
                    taskType = "Watering",
                    date = dateFormat.format(calendar.time),
                    time = "7:00 AM",
                    timestamp = calendar.timeInMillis
                )
                db.cropDao().insertReminder(reminder)
            }
        }

        val existingFertilizer = db.cropDao().getExistingReminders(cropName, "Fertilizing")
        if (existingFertilizer.isEmpty()) {
            calendar.time = Date() // reset
            calendar.add(Calendar.WEEK_OF_YEAR, 4)
            val reminder = Reminder(
                cropName = cropName,
                taskType = "Fertilizing",
                date = dateFormat.format(calendar.time),
                time = "8:00 AM",
                timestamp = calendar.timeInMillis
            )
            db.cropDao().insertReminder(reminder)
        }
    }

    inner class MyCropsAdapter(private val onClick: (JournalCollection) -> Unit) : 
        RecyclerView.Adapter<MyCropsAdapter.ViewHolder>() {
        
        private var collections = listOf<JournalCollection>()

        @SuppressLint("NotifyDataSetChanged")
        fun setCollections(newList: List<JournalCollection>) {
            collections = newList
            notifyDataSetChanged()
        }

        fun getCollectionAt(position: Int): JournalCollection = collections[position]

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_my_crop, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = collections[position]
            holder.plantName.text = item.title
            holder.cropType.text = item.cropName
            holder.plantedDate.text = getString(R.string.planted_on_format, item.date)
            holder.dayProgress.text = getString(R.string.day_progress_format, item.currentDay, item.targetDays)
            
            if (item.targetDays > 0) {
                val progress = ((item.currentDay.toFloat() / item.targetDays) * 100).toInt()
                holder.progressBar.progress = progress.coerceIn(0, 100)
            } else {
                holder.progressBar.progress = 0
            }
            
            holder.chipStatus.text = item.status
            updateStatusChip(holder.chipStatus, item.status)
            
            holder.chipHealth.text = item.healthStatus
            updateHealthChip(holder.chipHealth, item.healthStatus)

            if (item.imagePath != null) {
                val bitmap = BitmapFactory.decodeFile(item.imagePath)
                if (bitmap != null) {
                    holder.cropImage.setImageBitmap(bitmap)
                } else {
                    holder.cropImage.setImageResource(R.color.placeholder_gray)
                }
            } else {
                holder.cropImage.setImageResource(R.color.placeholder_gray)
            }

            holder.itemView.setOnClickListener { onClick(item) }

            holder.itemView.setOnLongClickListener {
                if (holder.motionLayout.currentState == R.id.start) {
                    holder.motionLayout.transitionToEnd()
                } else {
                    holder.motionLayout.transitionToStart()
                }
                true
            }

            holder.deleteButton.setOnClickListener {
                showDeleteCollectionDialog(item, holder.adapterPosition, holder.motionLayout)
            }
        }

        private fun updateStatusChip(view: TextView, status: String) {
            when (status) {
                "Growing" -> {
                    view.setBackgroundResource(R.drawable.bg_chip_growing)
                    view.setTextColor(ContextCompat.getColor(this@JournalActivity, R.color.status_growing_text))
                }
                "Harvested" -> {
                    view.setBackgroundResource(R.drawable.bg_chip_harvested)
                    view.setTextColor(ContextCompat.getColor(this@JournalActivity, R.color.status_harvested_text))
                }
                "Archived" -> {
                    view.setBackgroundResource(R.drawable.bg_chip_archived)
                    view.setTextColor(ContextCompat.getColor(this@JournalActivity, R.color.nav_inactive))
                }
                else -> {
                    view.setBackgroundResource(0)
                }
            }
        }

        private fun updateHealthChip(view: TextView, health: String) {
            when (health) {
                "Healthy" -> {
                    view.setBackgroundResource(R.drawable.bg_chip_growing)
                    view.setTextColor(ContextCompat.getColor(this@JournalActivity, R.color.status_healthy_text))
                }
                "Attention" -> {
                    view.setBackgroundResource(R.drawable.bg_chip_attention)
                    view.setTextColor(ContextCompat.getColor(this@JournalActivity, R.color.status_attention_text))
                }
                "Warning" -> {
                    view.setBackgroundResource(R.drawable.bg_chip_warning)
                    view.setTextColor(ContextCompat.getColor(this@JournalActivity, R.color.status_warning_text))
                }
                else -> {
                    view.setBackgroundResource(0)
                }
            }
        }

        override fun getItemCount() = collections.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val motionLayout: MotionLayout = view.findViewById(R.id.motionLayout)
            val plantName: TextView = view.findViewById(R.id.tvPlantName)
            val cropType: TextView = view.findViewById(R.id.tvCropType)
            val plantedDate: TextView = view.findViewById(R.id.tvPlantedDate)
            val dayProgress: TextView = view.findViewById(R.id.tvDayProgress)
            val progressBar: ProgressBar = view.findViewById(R.id.pbGrowth)
            val chipStatus: TextView = view.findViewById(R.id.chipStatus)
            val chipHealth: TextView = view.findViewById(R.id.chipHealth)
            val cropImage: ImageView = view.findViewById(R.id.ivCropImage)
            val deleteButton: View = view.findViewById(R.id.deleteButtonContainer)
        }
    }

    inner class RecentJournalAdapter : RecyclerView.Adapter<RecentJournalAdapter.ViewHolder>() {
        private var entries = listOf<JournalEntry>()

        @SuppressLint("NotifyDataSetChanged")
        fun setEntries(newList: List<JournalEntry>) {
            entries = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_journal_entry, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = entries[position]
            holder.title.text = "${entry.cropName} - Day ${entry.dayCount}"
            holder.notes.text = entry.notes
            
            try {
                val sdfInput = if (entry.date.contains("/")) {
                    SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
                } else {
                    SimpleDateFormat("MMMM dd yyyy", Locale.getDefault())
                }
                
                val date = sdfInput.parse(entry.date)
                if (date != null) {
                    holder.monthDay.text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
                    holder.year.text = SimpleDateFormat("yyyy", Locale.getDefault()).format(date)
                } else {
                    holder.monthDay.text = entry.date
                }
            } catch (e: Exception) {
                holder.monthDay.text = entry.date
            }

            if (entry.imagePaths.isNotEmpty()) {
                val path = entry.imagePaths.split(",")[0]
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap != null) {
                    holder.image.setImageBitmap(bitmap)
                } else {
                    holder.image.setImageResource(R.color.placeholder_gray)
                }
            } else {
                holder.image.setImageResource(R.color.placeholder_gray)
            }
        }

        override fun getItemCount() = entries.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val monthDay: TextView = view.findViewById(R.id.tvEntryMonthDay)
            val year: TextView = view.findViewById(R.id.tvEntryYear)
            val title: TextView = view.findViewById(R.id.tvEntryTitle)
            val notes: TextView = view.findViewById(R.id.tvEntryNotes)
            val image: ImageView = view.findViewById(R.id.ivEntryImage)
        }
    }
}
