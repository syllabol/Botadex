package com.example.botadex

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.botadex.database.BotadexDatabase
import com.example.botadex.database.JournalCollection
import com.example.botadex.database.JournalEntry
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class CollectionDetailActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: JournalAdapter
    private lateinit var db: BotadexDatabase
    private var collectionId: Int = -1
    
    private lateinit var btnHarvest: MaterialButton
    private lateinit var addEntryButton: FloatingActionButton
    private lateinit var harvestBadge: TextView
    
    // Header Views
    private lateinit var collectionFullImage: ShapeableImageView
    private lateinit var collectionThumbnail: ImageView
    private lateinit var headerCropName: TextView
    private lateinit var headerPlantName: TextView
    private lateinit var collectionDescription: TextView
    
    // Tab Views
    private lateinit var tabJournal: TextView
    private lateinit var tabEdit: TextView
    private lateinit var tabIndicator: View
    private lateinit var journalContainer: View
    private lateinit var editContainer: View
    
    // Edit Tab Views
    private lateinit var collectionDescriptionEdit: EditText
    private lateinit var btnEditCover: MaterialButton
    private lateinit var btnEditThumbnail: MaterialButton
    private lateinit var btnDeleteCollection: MaterialButton
    private lateinit var editPreviewCover: ImageView
    private lateinit var editPreviewThumbnail: ImageView
    
    private var currentCollection: JournalCollection? = null

    private val pickCoverLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleImagePicked(it, isCover = true) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collection_detail)

        db = BotadexDatabase.getDatabase(this)

        collectionId = intent.getIntExtra("COLLECTION_ID", -1)
        val title = intent.getStringExtra("COLLECTION_TITLE") ?: "Collection"

        findViewById<TextView>(R.id.collectionTitleHeader).text = title
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.entriesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        btnHarvest = findViewById(R.id.btnHarvest)
        addEntryButton = findViewById(R.id.addEntryButton)
        harvestBadge = findViewById(R.id.harvestBadge)
        
        // Initialize header views
        collectionFullImage = findViewById(R.id.collectionFullImage)
        collectionThumbnail = findViewById(R.id.collectionThumbnailImage)
        headerCropName = findViewById(R.id.headerCropName)
        headerPlantName = findViewById(R.id.headerPlantName)
        collectionDescription = findViewById(R.id.collectionDescription)
        
        // Initialize tabs
        tabJournal = findViewById(R.id.tabJournal)
        tabEdit = findViewById(R.id.tabEdit)
        tabIndicator = findViewById(R.id.tabIndicator)
        journalContainer = findViewById(R.id.journalContainer)
        editContainer = findViewById(R.id.editContainer)
        
        // Initialize edit tab content
        collectionDescriptionEdit = findViewById(R.id.collectionDescriptionEdit)
        btnEditCover = findViewById(R.id.btnEditCover)
        btnEditThumbnail = findViewById(R.id.btnEditThumbnail)
        btnDeleteCollection = findViewById(R.id.btnDeleteCollection)
        editPreviewCover = findViewById(R.id.editPreviewCover)
        editPreviewThumbnail = findViewById(R.id.editPreviewThumbnail)

        tabJournal.setOnClickListener { switchTab("journal") }
        tabEdit.setOnClickListener { switchTab("edit") }

        addEntryButton.setOnClickListener {
            currentCollection?.let { collection ->
                val intent = Intent(this, IdentifyHealthActivity::class.java)
                intent.putExtra("TARGET_COLLECTION_ID", collectionId)
                intent.putExtra("PRESET_CROP_NAME", collection.cropName)
                startActivity(intent)
            }
        }

        btnHarvest.setOnClickListener {
            showHarvestConfirmationDialog()
        }

        btnEditCover.setOnClickListener { pickCoverLauncher.launch("image/*") }
        btnEditThumbnail.setOnClickListener { showJournalImagePicker() }
        
        btnDeleteCollection.setOnClickListener {
            showDeleteCollectionDialog()
        }

        setupDescriptionSaving()
        loadCollectionData()
    }

    private fun showJournalImagePicker() {
        lifecycleScope.launch {
            val entries = db.cropDao().getJournalEntriesByCollection(collectionId)
            val allImagePaths = entries.flatMap { entry ->
                if (entry.imagePaths.isNotEmpty()) {
                    entry.imagePaths.split(",").filter { it.isNotEmpty() }
                } else emptyList()
            }

            if (allImagePaths.isEmpty()) {
                Toast.makeText(this@CollectionDetailActivity, "No images found in journal entries", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val dialogView = LayoutInflater.from(this@CollectionDetailActivity).inflate(R.layout.dialog_image_grid, null)
            val rv = dialogView.findViewById<RecyclerView>(R.id.imageGridRecyclerView)
            rv.layoutManager = GridLayoutManager(this@CollectionDetailActivity, 3)
            
            val dialog = AlertDialog.Builder(this@CollectionDetailActivity)
                .setTitle("Select Featured Photo")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .create()

            rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val iv = ImageView(parent.context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            300
                        )
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setPadding(4, 4, 4, 4)
                    }
                    return object : RecyclerView.ViewHolder(iv) {}
                }

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val path = allImagePaths[position]
                    val bitmap = BitmapFactory.decodeFile(path)
                    (holder.itemView as ImageView).setImageBitmap(bitmap)
                    holder.itemView.setOnClickListener {
                        updateThumbnail(path)
                        dialog.dismiss()
                    }
                }

                override fun getItemCount() = allImagePaths.size
            }

            dialog.show()
        }
    }

    private fun updateThumbnail(path: String) {
        lifecycleScope.launch {
            currentCollection?.let { coll ->
                // Sync with JournalActivity list by updating imagePath
                val updated = coll.copy(imagePath = path)
                db.cropDao().insertCollection(updated)
                currentCollection = updated
                updateHeaderData(updated)
                Toast.makeText(this@CollectionDetailActivity, "Featured photo updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun switchTab(tab: String) {
        val rootLayout = findViewById<View>(R.id.innerConstraintLayout) as androidx.constraintlayout.widget.ConstraintLayout
        val constraintSet = ConstraintSet()
        constraintSet.clone(rootLayout)

        if (tab == "journal") {
            journalContainer.visibility = View.VISIBLE
            editContainer.visibility = View.GONE
            
            tabJournal.setTextColor(ContextCompat.getColor(this, R.color.brand_green))
            tabJournal.setTypeface(null, Typeface.BOLD)
            tabEdit.setTextColor(ContextCompat.getColor(this, R.color.nav_inactive))
            tabEdit.setTypeface(null, Typeface.NORMAL)
            
            constraintSet.connect(tabIndicator.id, ConstraintSet.START, tabJournal.id, ConstraintSet.START)
            constraintSet.connect(tabIndicator.id, ConstraintSet.END, tabJournal.id, ConstraintSet.END)
        } else {
            journalContainer.visibility = View.GONE
            editContainer.visibility = View.VISIBLE
            
            tabEdit.setTextColor(ContextCompat.getColor(this, R.color.brand_green))
            tabEdit.setTypeface(null, Typeface.BOLD)
            tabJournal.setTextColor(ContextCompat.getColor(this, R.color.nav_inactive))
            tabJournal.setTypeface(null, Typeface.NORMAL)
            
            constraintSet.connect(tabIndicator.id, ConstraintSet.START, tabEdit.id, ConstraintSet.START)
            constraintSet.connect(tabIndicator.id, ConstraintSet.END, tabEdit.id, ConstraintSet.END)
        }
        constraintSet.applyTo(rootLayout)
    }

    private fun handleImagePicked(uri: Uri, isCover: Boolean) {
        lifecycleScope.launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                bitmap?.let {
                    val file = File(filesDir, "collection_${if (isCover) "cover" else "thumb"}_${System.currentTimeMillis()}.jpg")
                    val out = FileOutputStream(file)
                    it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out)
                    out.close()
                    
                    currentCollection?.let { coll ->
                        // Cover picker updates thumbnailPath, Thumbnail card picker updates imagePath
                        val updated = if (isCover) coll.copy(thumbnailPath = file.absolutePath) 
                                      else coll.copy(imagePath = file.absolutePath)
                        db.cropDao().insertCollection(updated)
                        currentCollection = updated
                        updateHeaderData(updated)
                        Toast.makeText(this@CollectionDetailActivity, "Photo updated", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@CollectionDetailActivity, "Failed to update photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteCollectionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Collection")
            .setMessage("Are you sure you want to delete this collection and all its entries? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    currentCollection?.let {
                        db.cropDao().deleteCollection(it)
                        finish()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupDescriptionSaving() {
        collectionDescriptionEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                saveDescription(s.toString())
            }
        })
    }

    private fun saveDescription(desc: String) {
        lifecycleScope.launch {
            currentCollection?.let {
                if (it.description != desc) {
                    val updated = it.copy(description = desc)
                    db.cropDao().insertCollection(updated)
                    currentCollection = updated
                    collectionDescription.text = desc
                }
            }
        }
    }

    private fun loadCollectionData() {
        lifecycleScope.launch {
            currentCollection = db.cropDao().getCollectionById(collectionId)
            currentCollection?.let { collection ->
                updateUIBasedOnStatus(collection)
                updateHeaderData(collection)
                loadEntries(collection.status == "Harvested")
            }
        }
    }

    private fun updateHeaderData(collection: JournalCollection) {
        lifecycleScope.launch {
            val crop = db.cropDao().getCropByName(collection.cropName.lowercase())
            headerCropName.text = collection.title
            headerPlantName.text = collection.cropName
            
            if (!collectionDescriptionEdit.isFocused) {
                collectionDescriptionEdit.setText(collection.description ?: "")
            }
            collectionDescription.text = collection.description ?: ""
            
            // 1. Cover Image (Wavysquare - Large) - Custom cover or illustration
            if (!collection.thumbnailPath.isNullOrEmpty() && File(collection.thumbnailPath).exists()) {
                val bitmap = BitmapFactory.decodeFile(collection.thumbnailPath)
                if (bitmap != null) {
                    collectionFullImage.setImageBitmap(bitmap)
                    editPreviewCover.setImageBitmap(bitmap)
                }
            } else {
                val resId = if (crop != null && !crop.imageUri.isNullOrBlank()) {
                     resources.getIdentifier(crop.imageUri, "drawable", packageName)
                } else 0

                if (resId != 0) {
                    collectionFullImage.setImageResource(resId)
                    editPreviewCover.setImageResource(resId)
                } else {
                    val leafId = when (collection.cropName.lowercase()) {
                        "cassava" -> R.drawable.cassavaleaf
                        "tomato" -> R.drawable.tomatoleaf
                        "potato" -> R.drawable.potatoleaf
                        "ube" -> R.drawable.ubeleaf
                        "kamote" -> R.drawable.kamoteleaf
                        "onion" -> R.drawable.onionleaf
                        else -> R.drawable.ic_launcher_background
                    }
                    collectionFullImage.setImageResource(leafId)
                    editPreviewCover.setImageResource(leafId)
                }
            }

            // 2. Thumbnail Image (Small card overlay) - Sync with JournalActivity (imagePath)
            if (!collection.imagePath.isNullOrEmpty() && File(collection.imagePath).exists()) {
                val bitmap = BitmapFactory.decodeFile(collection.imagePath)
                if (bitmap != null) {
                    collectionThumbnail.setImageBitmap(bitmap)
                    editPreviewThumbnail.setImageBitmap(bitmap)
                }
            } else {
                collectionThumbnail.setImageResource(R.color.placeholder_gray)
                editPreviewThumbnail.setImageResource(R.color.placeholder_gray)
            }
        }
    }

    private fun updateUIBasedOnStatus(collection: JournalCollection) {
        val isHarvested = collection.status == "Harvested"
        val isReadyForHarvest = collection.currentDay >= collection.targetDays && !isHarvested

        if (isHarvested) {
            harvestBadge.visibility = View.VISIBLE
            btnHarvest.visibility = View.GONE
            addEntryButton.visibility = View.GONE
        } else {
            harvestBadge.visibility = View.GONE
            addEntryButton.visibility = View.VISIBLE
            if (isReadyForHarvest) {
                btnHarvest.visibility = View.VISIBLE
            } else {
                btnHarvest.visibility = View.GONE
            }
        }
    }

    private fun showHarvestConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Harvest Plant")
            .setMessage("Are you sure you want to harvest this plant? It will become read-only.")
            .setPositiveButton("Harvest") { _, _ ->
                harvestPlant()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun harvestPlant() {
        lifecycleScope.launch {
            currentCollection?.let {
                val updated = it.copy(status = "Harvested")
                db.cropDao().insertCollection(updated)
                Toast.makeText(this@CollectionDetailActivity, "Plant harvested!", Toast.LENGTH_SHORT).show()
                loadCollectionData()
            }
        }
    }

    private fun loadEntries(readOnly: Boolean) {
        lifecycleScope.launch {
            try {
                val entries = db.cropDao().getJournalEntriesByCollection(collectionId)
                adapter = JournalAdapter(entries, { entry ->
                    val intent = Intent(this@CollectionDetailActivity, AddJournalEntryActivity::class.java)
                    intent.putExtra("JOURNAL_ID", entry.id)
                    startActivity(intent)
                }, { entry ->
                    if (!readOnly) {
                        showDeleteConfirmationDialog(entry)
                    } else {
                        Toast.makeText(this@CollectionDetailActivity, "Cannot delete from harvested collection", Toast.LENGTH_SHORT).show()
                    }
                })
                recyclerView.adapter = adapter
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showDeleteConfirmationDialog(entry: JournalEntry) {
        AlertDialog.Builder(this)
            .setTitle("Delete Entry")
            .setMessage("Are you sure you want to delete this entry?")
            .setPositiveButton("Delete") { _, _ ->
                deleteEntry(entry)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteEntry(entry: JournalEntry) {
        lifecycleScope.launch {
            db.cropDao().deleteJournalEntry(entry)
            loadCollectionData()
        }
    }

    override fun onResume() {
        super.onResume()
        loadCollectionData()
    }

    inner class JournalAdapter(
        private val entries: List<JournalEntry>,
        private val onItemClick: (JournalEntry) -> Unit,
        private val onDeleteClick: (JournalEntry) -> Unit
    ) : RecyclerView.Adapter<JournalAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_journal_entry, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = entries[position]
            holder.title.text = "${entry.cropName} - Day ${entry.dayCount}"
            holder.notes.text = entry.notes
            
            updateHealthChipStyle(holder.health, entry.healthStatus)

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
                    holder.year.text = ""
                }
            } catch (e: Exception) {
                holder.monthDay.text = entry.date
                holder.year.text = ""
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

            holder.itemView.setOnClickListener { onItemClick(entry) }
            
            holder.itemView.setOnLongClickListener {
                onDeleteClick(entry)
                true
            }
        }

        private fun updateHealthChipStyle(view: TextView, health: String) {
            val healthLower = health.lowercase(Locale.getDefault())
            when {
                healthLower == "healthy" -> {
                    view.text = "Healthy"
                    view.setBackgroundResource(R.drawable.bg_chip_growing)
                    view.setTextColor(ContextCompat.getColor(this@CollectionDetailActivity, R.color.status_healthy_text))
                }
                healthLower == "attention" -> {
                    view.text = "Attention"
                    view.setBackgroundResource(R.drawable.bg_chip_attention)
                    view.setTextColor(ContextCompat.getColor(this@CollectionDetailActivity, R.color.status_attention_text))
                }
                else -> {
                    view.text = if (healthLower == "warning") "Warning" else health
                    view.setBackgroundResource(R.drawable.bg_chip_warning)
                    view.setTextColor(ContextCompat.getColor(this@CollectionDetailActivity, R.color.status_warning_text))
                }
            }
        }

        override fun getItemCount() = entries.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val monthDay: TextView = view.findViewById(R.id.tvEntryMonthDay)
            val year: TextView = view.findViewById(R.id.tvEntryYear)
            val title: TextView = view.findViewById(R.id.tvEntryTitle)
            val notes: TextView = view.findViewById(R.id.tvEntryNotes)
            val health: TextView = view.findViewById(R.id.tvEntryHealth)
            val image: ImageView = view.findViewById(R.id.ivEntryImage)
        }
    }
}
