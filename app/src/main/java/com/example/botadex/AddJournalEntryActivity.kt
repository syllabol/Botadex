package com.example.botadex

import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.botadex.database.BotadexDatabase
import com.example.botadex.database.JournalEntry
import com.example.botadex.database.Reminder
import com.example.botadex.database.JournalCollection
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class AddJournalEntryActivity : AppCompatActivity() {

    private lateinit var db: BotadexDatabase
    private var journalId: Int = -1
    private var targetCollectionId: Int = -1
    private var detectedHealth: String? = null
    private val imagePaths = mutableListOf<String>()

    private lateinit var imagesRecyclerView: RecyclerView
    private lateinit var imageAdapter: ImageAdapter
    
    private lateinit var cropNameEditText: EditText
    private lateinit var dateEditText: EditText
    private lateinit var notesEditText: EditText
    private lateinit var saveButton: ImageButton
    private lateinit var compareButton: View
    
    private lateinit var healthChip: TextView
    private lateinit var diseaseChip: TextView

    private val REQUEST_CAMERA = 101
    private val REQUEST_GALLERY = 102
    
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_journal_entry)

        db = BotadexDatabase.getDatabase(this)

        imagesRecyclerView = findViewById(R.id.imagesRecyclerView)
        imagesRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        
        cropNameEditText = findViewById(R.id.journalCropName)
        dateEditText = findViewById(R.id.journalDate)
        notesEditText = findViewById(R.id.journalNotes)
        saveButton = findViewById(R.id.saveJournalButtonTop)
        compareButton = findViewById(R.id.compareButton)
        
        healthChip = findViewById(R.id.journalHealthChip)
        diseaseChip = findViewById(R.id.journalDiseaseChip)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        // Bottom Navigation
        findViewById<View>(R.id.navIdentify).setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        findViewById<View>(R.id.navLibrary).setOnClickListener { startActivity(Intent(this, CropLibraryActivity::class.java)) }
        findViewById<View>(R.id.navJournal).setOnClickListener { startActivity(Intent(this, JournalActivity::class.java)) }
        findViewById<View>(R.id.navCalendar).setOnClickListener { startActivity(Intent(this, RemindersActivity::class.java)) }

        journalId = intent.getIntExtra("JOURNAL_ID", -1)
        targetCollectionId = intent.getIntExtra("COLLECTION_ID", -1)
        detectedHealth = intent.getStringExtra("DETECTED_HEALTH")
        
        if (journalId != -1) {
            setupReadMode()
        } else {
            setupCreateMode()
        }

        imageAdapter = ImageAdapter(imagePaths, { showFullImage(it) }, { showImagePickerOptions() }, { removeImage(it) })
        imagesRecyclerView.adapter = imageAdapter

        saveButton.setOnClickListener {
            if (journalId == -1) {
                saveEntry()
            }
        }

        compareButton.setOnClickListener {
            showCompareOptions()
        }
    }

    private fun updateHealthUI(health: String?) {
        val finalHealth = health ?: "Healthy"
        val healthLower = finalHealth.lowercase(Locale.getDefault())
        
        when {
            healthLower == "healthy" -> {
                healthChip.text = "Healthy"
                healthChip.setBackgroundResource(R.drawable.bg_chip_growing)
                healthChip.setTextColor(ContextCompat.getColor(this, R.color.status_healthy_text))
                healthChip.visibility = View.VISIBLE
                diseaseChip.visibility = View.GONE
            }
            healthLower == "attention" -> {
                healthChip.text = "Attention"
                healthChip.setBackgroundResource(R.drawable.bg_chip_attention)
                healthChip.setTextColor(ContextCompat.getColor(this, R.color.status_attention_text))
                healthChip.visibility = View.VISIBLE
                
                diseaseChip.text = "potential issue detected"
                diseaseChip.setBackgroundResource(R.drawable.bg_chip_attention)
                diseaseChip.setTextColor(ContextCompat.getColor(this, R.color.status_attention_text))
                diseaseChip.visibility = View.VISIBLE
            }
            else -> {
                healthChip.text = "Unhealthy"
                healthChip.setBackgroundResource(R.drawable.bg_chip_warning)
                healthChip.setTextColor(ContextCompat.getColor(this, R.color.status_warning_text))
                healthChip.visibility = View.VISIBLE
                
                diseaseChip.text = finalHealth.lowercase(Locale.getDefault())
                diseaseChip.setBackgroundResource(R.drawable.bg_chip_warning)
                diseaseChip.setTextColor(ContextCompat.getColor(this, R.color.status_warning_text))
                diseaseChip.visibility = View.VISIBLE
            }
        }
    }

    private fun setupReadMode() {
        isEditMode = false
        saveButton.visibility = View.GONE
        compareButton.visibility = View.VISIBLE
        
        cropNameEditText.isEnabled = false
        dateEditText.isEnabled = false
        notesEditText.isEnabled = false
        
        lifecycleScope.launch {
            val entries = db.cropDao().getAllJournal()
            val entry = entries.find { it.id == journalId }
            entry?.let {
                targetCollectionId = it.collectionId
                cropNameEditText.setText(it.cropName)
                dateEditText.setText(it.date)
                notesEditText.setText(it.notes)
                
                detectedHealth = it.healthStatus
                updateHealthUI(detectedHealth)
                
                imagePaths.clear()
                if (it.imagePaths.isNotEmpty()) {
                    imagePaths.addAll(it.imagePaths.split(","))
                }
                imageAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun setupCreateMode() {
        isEditMode = true
        saveButton.visibility = View.VISIBLE
        compareButton.visibility = View.GONE
        saveButton.setImageResource(android.R.drawable.ic_menu_save)
        
        updateHealthUI(detectedHealth)
        
        val initialImagePath = intent.getStringExtra("IMAGE_PATH")
        initialImagePath?.let { imagePaths.add(it) }

        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("MMMM dd yyyy", Locale.getDefault())
        dateEditText.setText(sdf.format(calendar.time))

        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            dateEditText.setText(sdf.format(calendar.time))
        }

        dateEditText.setOnClickListener {
            DatePickerDialog(this, dateSetListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        lifecycleScope.launch {
            val collections = db.cropDao().getAllCollectionsOnce()
            val collection = collections.find { it.id == targetCollectionId }
            if (collection != null) {
                cropNameEditText.setText(collection.title)
            } else {
                val cropName = intent.getStringExtra("CROP_NAME")
                cropNameEditText.setText(cropName)
            }
        }
    }

    private fun showCompareOptions() {
        lifecycleScope.launch {
            val allEntries = db.cropDao().getJournalEntriesByCollection(targetCollectionId)
            val otherEntries = allEntries.filter { it.id != journalId && it.imagePaths.isNotEmpty() }

            if (otherEntries.isEmpty()) {
                Toast.makeText(this@AddJournalEntryActivity, "No other entries with pictures to compare.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val entryTitles = otherEntries.map { "Day ${it.dayCount} (${it.date})" }.toTypedArray()
            AlertDialog.Builder(this@AddJournalEntryActivity)
                .setTitle("Select entry to compare with")
                .setItems(entryTitles) { _, which ->
                    val selectedEntry = otherEntries[which]
                    val intent = Intent(this@AddJournalEntryActivity, CompareEntriesActivity::class.java)
                    intent.putExtra("ENTRY1_ID", journalId)
                    intent.putExtra("ENTRY2_ID", selectedEntry.id)
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun saveEntry() {
        val name = cropNameEditText.text.toString()
        val date = dateEditText.text.toString()
        val notes = notesEditText.text.toString()

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show()
            return
        }

        if (targetCollectionId == -1) {
            Toast.makeText(this, "Internal Error: No collection target", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val collections = db.cropDao().getAllCollectionsOnce()
            val collection = collections.find { it.id == targetCollectionId }
            
            val sdf = SimpleDateFormat("MMMM dd yyyy", Locale.getDefault())
            var dayCount = collection?.currentDay ?: 1

            if (collection != null) {
                try {
                    val plantedDateObj = sdf.parse(collection.date)
                    val entryDateObj = sdf.parse(date)
                    if (plantedDateObj != null && entryDateObj != null) {
                        val diff = entryDateObj.time - plantedDateObj.time
                        dayCount = (diff / (1000 * 60 * 60 * 24)).toInt() + 1
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val finalHealth = detectedHealth ?: "Healthy"

            val journalEntry = JournalEntry(
                id = 0,
                collectionId = targetCollectionId,
                cropName = name,
                notes = notes,
                date = date,
                imagePaths = imagePaths.joinToString(","),
                dayCount = if (dayCount > 0) dayCount else 1,
                healthStatus = finalHealth,
                timestamp = System.currentTimeMillis()
            )

            db.cropDao().insertJournalEntry(journalEntry)
            
            // Interaction resets health to detected status (or Healthy) and updates timer
            collection?.let {
                val updatedCollection = it.copy(
                    healthStatus = finalHealth,
                    lastInteractionDate = System.currentTimeMillis()
                )
                db.cropDao().insertCollection(updatedCollection)
            }
            
            Toast.makeText(this@AddJournalEntryActivity, "Entry Saved", Toast.LENGTH_SHORT).show()
            
            val intent = Intent(this@AddJournalEntryActivity, CollectionDetailActivity::class.java)
            intent.putExtra("COLLECTION_ID", targetCollectionId)
            intent.putExtra("COLLECTION_TITLE", collection?.title)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun showFullImage(path: String) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_full_image)
        
        val fullImageView = dialog.findViewById<ImageView>(R.id.fullImageView)
        val closeButton = dialog.findViewById<ImageButton>(R.id.closeButton)

        val bitmap = BitmapFactory.decodeFile(path)
        if (bitmap != null) {
            fullImageView.setImageBitmap(bitmap)
        }
        fullImageView.scaleType = ImageView.ScaleType.FIT_CENTER

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showImagePickerOptions() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(this)
            .setTitle("Add Documentation Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }
            .show()
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, REQUEST_CAMERA)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private val pickImageLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            uri?.let {
                try {
                    val inputStream = contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    bitmap?.let { b ->
                        val path = saveBitmapToFile(b)
                        path?.let { p ->
                            imagePaths.add(p)
                            imageAdapter.notifyDataSetChanged()
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CAMERA) {
            val bitmap = data?.extras?.get("data") as? Bitmap
            bitmap?.let {
                val path = saveBitmapToFile(it)
                path?.let { p ->
                    imagePaths.add(p)
                    imageAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap): String? {
        val dir = File(filesDir, "journal_images")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "extra_${System.currentTimeMillis()}.jpg")
        return try {
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.close()
            file.absolutePath
        } catch (e: IOException) {
            null
        }
    }

    private fun removeImage(position: Int) {
        if (position >= 0 && position < imagePaths.size) {
            imagePaths.removeAt(position)
            imageAdapter.notifyDataSetChanged()
        }
    }

    inner class ImageAdapter(
        private val paths: List<String>,
        private val onImageClick: (String) -> Unit,
        private val onAddClick: () -> Unit,
        private val onRemoveClick: (Int) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_IMAGE = 1
        private val TYPE_ADD = 2

        override fun getItemViewType(position: Int): Int {
            return if (isEditMode && position == paths.size) TYPE_ADD else TYPE_IMAGE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_journal_image, parent, false)
            return if (viewType == TYPE_IMAGE) ImageViewHolder(view) else AddViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is ImageViewHolder) {
                holder.bind(paths[position], position)
            } else if (holder is AddViewHolder) {
                holder.bind()
            }
        }

        override fun getItemCount(): Int {
            return if (isEditMode) paths.size + 1 else paths.size
        }

        inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val imageView = itemView.findViewById<ImageView>(R.id.journalImageView)
            private val placeholder = itemView.findViewById<View>(R.id.addMorePlaceholder)
            private val removeButton = itemView.findViewById<ImageButton>(R.id.removeImageButton)

            fun bind(path: String, position: Int) {
                imageView.visibility = View.VISIBLE
                placeholder.visibility = View.GONE
                removeButton.visibility = if (isEditMode && journalId == -1) View.VISIBLE else View.GONE
                
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                }

                itemView.setOnClickListener { onImageClick(path) }
                removeButton.setOnClickListener { onRemoveClick(position) }
            }
        }

        inner class AddViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val imageView = itemView.findViewById<ImageView>(R.id.journalImageView)
            private val placeholder = itemView.findViewById<View>(R.id.addMorePlaceholder)
            private val removeButton = itemView.findViewById<ImageButton>(R.id.removeImageButton)

            fun bind() {
                imageView.visibility = View.GONE
                placeholder.visibility = View.VISIBLE
                removeButton.visibility = View.GONE
                itemView.setOnClickListener { onAddClick() }
            }
        }
    }
}
