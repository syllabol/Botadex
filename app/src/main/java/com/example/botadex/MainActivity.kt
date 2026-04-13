package com.example.botadex

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.botadex.database.BotadexDatabase
import com.example.botadex.database.Crop
import com.example.botadex.ml.TFLiteClassifier
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var identifyLayout: View
    private lateinit var resultLayout: View
    
    private lateinit var imageViewInitial: ImageView
    private lateinit var imageViewResult: ImageView
    private lateinit var cropNameText: TextView
    private lateinit var scientificNameText: TextView
    private lateinit var confidenceText: TextView
    private lateinit var descriptionText: TextView
    
    private lateinit var addToJournalButton: Button
    private lateinit var tryAgainButton: Button
    
    private var currentImagePath: String? = null
    private var currentCropName: String? = null

    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_IMAGE_IMPORT = 2

    private lateinit var db: BotadexDatabase
    private lateinit var classifier: TFLiteClassifier
    private lateinit var dataset: List<CropInfo>

    private val labels = listOf("cassava","kamote","onion", "potato", "tomato")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize layouts
        identifyLayout = findViewById(R.id.identifyLayout)
        resultLayout = findViewById(R.id.resultLayout)
        
        // Initialize views
        imageViewInitial = findViewById(R.id.imageViewInitial)
        imageViewResult = findViewById(R.id.imageViewResult)
        cropNameText = findViewById(R.id.cropNameText)
        scientificNameText = findViewById(R.id.scientificNameText)
        confidenceText = findViewById(R.id.confidenceText)
        descriptionText = findViewById(R.id.descriptionText)
        
        val captureButton: Button = findViewById(R.id.captureButton)
        val importButton: Button = findViewById(R.id.importButton)
        val importButtonResult: Button = findViewById(R.id.importButtonResult)
        
        addToJournalButton = findViewById(R.id.addToJournalButton)
        tryAgainButton = findViewById(R.id.tryAgainButton)

        // Header controls
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.homeIcon).setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }

        // Bottom Navigation
        findViewById<View>(R.id.navIdentify).setOnClickListener {
            showIdentifyLayout()
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

        // DB and ML
        db = Room.databaseBuilder(
            applicationContext,
            BotadexDatabase::class.java,
            "botadex-db"
        ).fallbackToDestructiveMigration().build()

        classifier = TFLiteClassifier(this)
        dataset = loadDataset()

        // Interaction
        captureButton.setOnClickListener { dispatchTakePictureIntent() }
        importButton.setOnClickListener { dispatchImportFromGalleryIntent() }
        importButtonResult.setOnClickListener { dispatchImportFromGalleryIntent() }
        
        tryAgainButton.setOnClickListener {
            showIdentifyLayout()
        }

        addToJournalButton.setOnClickListener {
            val intent = Intent(this, AddJournalEntryActivity::class.java)
            intent.putExtra("CROP_NAME", currentCropName)
            intent.putExtra("IMAGE_PATH", currentImagePath)
            startActivity(intent)
        }
        
        showIdentifyLayout()
    }

    private fun showIdentifyLayout() {
        identifyLayout.visibility = View.VISIBLE
        resultLayout.visibility = View.GONE
    }

    private fun showResultLayout() {
        identifyLayout.visibility = View.GONE
        resultLayout.visibility = View.VISIBLE
    }

    private fun dispatchTakePictureIntent() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
    }

    private fun dispatchImportFromGalleryIntent() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_IMAGE_IMPORT)
    }

    private fun loadDataset(): List<CropInfo> {
        return try {
            val json = assets.open("crop_library.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, CropInfo>>() {}.type
            val map: Map<String, CropInfo> = Gson().fromJson(json, type)

            labels.map { label ->
                val info = map[label] ?: CropInfo(label, null, "No description available", "", "", "", "")
                info.copy(name = label)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            var bitmap: Bitmap? = null

            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                bitmap = data?.extras?.get("data") as? Bitmap
            } else if (requestCode == REQUEST_IMAGE_IMPORT) {
                val uri: Uri? = data?.data
                uri?.let {
                    try {
                        bitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }

            bitmap?.let { bmp ->
                imageViewInitial.setImageBitmap(bmp)
                imageViewResult.setImageBitmap(bmp)
                currentImagePath = saveBitmapToFile(bmp)

                val (index, confidence) = classifier.classify(bmp)
                val crop = dataset.getOrNull(index)

                if (crop != null) {
                    currentCropName = crop.name
                    val confidencePercent = (confidence * 100).toInt()
                    
                    cropNameText.text = currentCropName?.replaceFirstChar { it.uppercase() }
                    scientificNameText.text = crop.scientificName ?: "Scientific name"
                    confidenceText.text = "$confidencePercent%"
                    descriptionText.text = crop.description
                    
                    showResultLayout()

                    lifecycleScope.launch {
                        try {
                            db.cropDao().insertCrop(crop.toEntity())
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    Toast.makeText(this, "Unknown crop", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun CropInfo.toEntity(): Crop {
        return Crop(
            name = this.name ?: "Unknown",
            description = this.description,
            wateringSchedule = this.watering,
            fertilizerInfo = this.fertilization
        )
    }

    private fun saveBitmapToFile(bitmap: Bitmap): String? {
        val dir = File(filesDir, "journal_images")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${System.currentTimeMillis()}.jpg")
        return try {
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.close()
            file.absolutePath
        } catch (e: IOException) {
            null
        }
    }
}
