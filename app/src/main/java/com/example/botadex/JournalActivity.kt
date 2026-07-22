package com.example.botadex

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
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
        findViewById<View>(R.id.navJournal).setOnClickListener { }
        findViewById<View>(R.id.navCalendar).setOnClickListener {
            startActivity(Intent(this, RemindersActivity::class.java))
        }
        findViewById<View>(R.id.navIdentify).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun setupTabs() {
        val tabs = listOf<TextView>(
            findViewById(R.id.tabAll),
            findViewById(R.id.tabGrowing),
            findViewById(R.id.tabHarvested),
            findViewById(R.id.tabArchived)
        )

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
            
            val updatedCollections = mutableListOf<JournalCollection>()
            for (collection in collections) {
                updatedCollections.add(updateCollectionLogic(collection))
            }

            val filtered = if (currentFilter == "All") {
                updatedCollections
            } else {
                updatedCollections.filter { it.status.equals(currentFilter, ignoreCase = true) }
            }
            myCropsAdapter.setCollections(filtered)
        }
    }

    private suspend fun updateCollectionLogic(collection: JournalCollection): JournalCollection {
        var updated = collection
        var needsUpdate = false

        // 1. Update Current Day
        try {
            val sdf = SimpleDateFormat("MMMM dd yyyy", Locale.getDefault())
            val plantedDate = sdf.parse(collection.date)
            if (plantedDate != null) {
                val diff = Date().time - plantedDate.time
                val days = (diff / (1000 * 60 * 60 * 24)).toInt() + 1
                if (days != updated.currentDay && days > 0) {
                    updated = updated.copy(currentDay = days)
                    needsUpdate = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Sync Target Days if it is still at default 30 or sum is needed
        val normalizedName = updated.cropName.lowercase().trim()
        val cropObj = db.cropDao().getCropByName(normalizedName)
        
        val correctTotalDays = when {
            normalizedName.contains("cassava") -> 335
            normalizedName.contains("tomato") -> 98
            normalizedName.contains("potato") && !normalizedName.contains("sweet") -> 105
            normalizedName.contains("ube") -> 270
            normalizedName.contains("kamote") || normalizedName.contains("sweet potato") -> 140
            normalizedName.contains("onion") -> 125
            cropObj != null && cropObj.totalDays > 0 -> cropObj.totalDays
            else -> 30
        }

        if (updated.targetDays != correctTotalDays && correctTotalDays != 30) {
            updated = updated.copy(targetDays = correctTotalDays)
            needsUpdate = true
        }

        // 3. Update Health Status
        val lastInteraction = updated.lastInteractionDate
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
            db.cropDao().insertCollection(updated)
        }
        
        return updated
    }

    private fun showAddCollectionDialog(prefilledCrop: String? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_collection, null)
        val editTitle = dialogView.findViewById<EditText>(R.id.editCollectionTitle)
        val editCrop = dialogView.findViewById<EditText>(R.id.editCropName)
        val editDate = dialogView.findViewById<EditText>(R.id.editDatePlanted)

        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("MMMM dd yyyy", Locale.getDefault())
        editDate.setText(sdf.format(calendar.time))

        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            editDate.setText(sdf.format(calendar.time))
        }

        editDate.setOnClickListener {
            DatePickerDialog(this, dateSetListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        
        if (prefilledCrop != null) {
            editCrop.setText(prefilledCrop)
        }
        
        AlertDialog.Builder(this)
            .setTitle("New Crop")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val title = editTitle.text.toString()
                val crop = editCrop.text.toString()
                val date = editDate.text.toString()
                if (title.isNotEmpty()) {
                    createCollection(title, crop, date)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createCollection(title: String, cropName: String, date: String) {
        lifecycleScope.launch {
            val normalizedName = cropName.lowercase().trim()
            val cropObj = db.cropDao().getCropByName(normalizedName)
            val stages = db.cropDao().getGrowthStagesForCrop(normalizedName)
            
            var targetDays = when {
                normalizedName.contains("cassava") -> 335
                normalizedName.contains("tomato") -> 98
                normalizedName.contains("potato") && !normalizedName.contains("sweet") -> 105
                normalizedName.contains("ube") -> 270
                normalizedName.contains("kamote") || normalizedName.contains("sweet potato") -> 140
                normalizedName.contains("onion") -> 125
                else -> 30
            }
            
            if (cropObj != null && cropObj.totalDays > 0) {
                targetDays = cropObj.totalDays
            } else if (stages.isNotEmpty()) {
                val summed = stages.sumOf { it.durationDays }
                if (summed > 0) targetDays = summed
            }

            val sdf = SimpleDateFormat("MMMM dd yyyy", Locale.getDefault())
            val plantedDateObj = try { sdf.parse(date) } catch (e: Exception) { Date() } ?: Date()
            val diff = Date().time - plantedDateObj.time
            val currentDay = (diff / (1000 * 60 * 60 * 24)).toInt() + 1

            val collection = JournalCollection(
                title = title, 
                cropName = cropName, 
                date = date,
                status = "Growing",
                healthStatus = "Healthy",
                currentDay = if (currentDay > 0) currentDay else 1,
                targetDays = targetDays,
                imagePath = prefillImagePath,
                lastInteractionDate = System.currentTimeMillis()
            )
            
            val id = db.cropDao().insertCollection(collection).toInt()
            
            autoScheduleReminders(title, normalizedName, stages, targetDays, plantedDateObj)
            
            // Redirect directly to AddJournalEntryActivity for documentation
            val intent = Intent(this@JournalActivity, AddJournalEntryActivity::class.java)
            intent.putExtra("COLLECTION_ID", id)
            intent.putExtra("IMAGE_PATH", prefillImagePath)
            
            prefillImagePath = null
            startActivity(intent)
        }
    }

    private suspend fun autoScheduleReminders(plantTitle: String, cropName: String, stages: List<com.example.botadex.database.GrowthStageEntity>, totalDuration: Int, plantedDate: Date) {
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())

        var accumulatedDays = 0
        stages.forEach { stage ->
            if (stage.durationDays > 0) {
                accumulatedDays += stage.durationDays
                val stageCal = Calendar.getInstance()
                stageCal.time = plantedDate
                stageCal.add(Calendar.DAY_OF_YEAR, accumulatedDays)
                
                val reminder = Reminder(
                    cropName = plantTitle,
                    taskType = "Growth Stage: ${stage.stage} - Day ${accumulatedDays + 1}",
                    date = dateFormat.format(stageCal.time),
                    time = "9:00 AM",
                    timestamp = stageCal.timeInMillis
                )
                db.cropDao().insertReminder(reminder)
            }
        }

        val waterCal = Calendar.getInstance()
        waterCal.time = plantedDate
        var waterDay = 1
        while (waterDay + 3 <= totalDuration) {
            waterDay += 3
            waterCal.add(Calendar.DAY_OF_YEAR, 3)
            val reminder = Reminder(
                cropName = plantTitle,
                taskType = "Watering - Day $waterDay",
                date = dateFormat.format(waterCal.time),
                time = "7:00 AM",
                timestamp = waterCal.timeInMillis
            )
            db.cropDao().insertReminder(reminder)
        }

        val fertCal = Calendar.getInstance()
        fertCal.time = plantedDate
        var fertDay = 1
        while (fertDay + 30 <= totalDuration) {
            fertDay += 30
            fertCal.add(Calendar.DAY_OF_YEAR, 30)
            val reminder = Reminder(
                cropName = plantTitle,
                taskType = "Fertilizing - Day $fertDay",
                date = dateFormat.format(fertCal.time),
                time = "8:00 AM",
                timestamp = fertCal.timeInMillis
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
            holder.dayProgress.text = getString(R.string.day_progress_format, item.currentDay, item.targetDays, item.cropName)
            
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
                else -> view.setBackgroundResource(0)
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
                else -> view.setBackgroundResource(0)
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
