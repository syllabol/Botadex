package com.example.botadex

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.botadex.database.BotadexDatabase
import com.example.botadex.database.Crop
import com.example.botadex.database.JournalCollection
import com.example.botadex.ml.TFLiteClassifier
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var identifyLayout: View
    private lateinit var resultLayout: View
    
    private lateinit var viewFinder: PreviewView
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
    private var targetCollectionId: Int = -1

    private lateinit var db: BotadexDatabase
    private lateinit var classifier: TFLiteClassifier
    private lateinit var dataset: List<CropInfo>

    private var imageCapture: ImageCapture? = null
    private var cameraControl: CameraControl? = null
    private var isFlashOn = false
    private lateinit var cameraExecutor: ExecutorService

    private val labels = listOf("cassava","kamote","onion", "potato", "tomato")

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to identify crops", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            val uri: Uri? = data?.data
            uri?.let { handleSelectedImage(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        targetCollectionId = intent.getIntExtra("TARGET_COLLECTION_ID", -1)

        // Initialize layouts
        identifyLayout = findViewById(R.id.identifyLayout)
        resultLayout = findViewById(R.id.resultLayout)
        
        // Initialize views
        viewFinder = findViewById(R.id.viewFinder)
        imageViewInitial = findViewById(R.id.imageViewInitial)
        imageViewResult = findViewById(R.id.imageViewResult)
        cropNameText = findViewById(R.id.cropNameText)
        scientificNameText = findViewById(R.id.scientificNameText)
        confidenceText = findViewById(R.id.confidenceText)
        descriptionText = findViewById(R.id.descriptionText)
        
        val captureButton: View = findViewById(R.id.captureButton)
        val importButton: View = findViewById(R.id.importButton)
        val importButtonResult: View? = findViewById(R.id.importButtonResult)
        val btnFlash: View? = findViewById(R.id.btnFlash)
        
        addToJournalButton = findViewById(R.id.addToJournalButton)
        tryAgainButton = findViewById(R.id.tryAgainButton)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

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

        db = BotadexDatabase.getDatabase(this)
        classifier = TFLiteClassifier(this)
        dataset = loadDataset()

        cameraExecutor = Executors.newSingleThreadExecutor()

        captureButton.setOnClickListener { takePhoto() }
        importButton.setOnClickListener { dispatchImportFromGalleryIntent() }
        importButtonResult?.setOnClickListener { dispatchImportFromGalleryIntent() }
        
        btnFlash?.setOnClickListener {
            toggleFlash()
        }
        
        tryAgainButton.setOnClickListener {
            showIdentifyLayout()
            startCamera()
        }

        addToJournalButton.setOnClickListener {
            if (targetCollectionId == -1) {
                showAddOptionDialog()
            } else {
                startAddJournalActivity(targetCollectionId)
            }
        }
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        showIdentifyLayout()
    }

    private fun showAddOptionDialog() {
        lifecycleScope.launch {
            val collections = db.cropDao().getAllCollectionsOnce()
            if (collections.isEmpty()) {
                // Requirement: Redirect directly onto the add collection dialog
                val intent = Intent(this@MainActivity, JournalActivity::class.java)
                intent.putExtra("PREFILL_CROP_NAME", currentCropName)
                intent.putExtra("PREFILL_IMAGE_PATH", currentImagePath)
                startActivity(intent)
            } else {
                val options = arrayOf("Start New Collection", "Add to Existing Collection")
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Add to Journal")
                    .setItems(options) { _, which ->
                        if (which == 0) {
                            val intent = Intent(this@MainActivity, JournalActivity::class.java)
                            intent.putExtra("PREFILL_CROP_NAME", currentCropName)
                            intent.putExtra("PREFILL_IMAGE_PATH", currentImagePath)
                            startActivity(intent)
                        } else {
                            showCollectionSelectionDialog(collections)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetRotation(Surface.ROTATION_0)
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(Surface.ROTATION_0)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)
                cameraControl = camera.cameraControl

            } catch(exc: Exception) {
                Log.e("MainActivity", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleFlash() {
        isFlashOn = !isFlashOn
        cameraControl?.enableTorch(isFlashOn)
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(filesDir, "temp_capture.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("MainActivity", "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(this@MainActivity, "Capture failed", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    val correctedBitmap = rotateImageIfRequired(bitmap, photoFile.absolutePath)
                    processIdentification(correctedBitmap)
                }
            }
        )
    }

    private fun rotateImageIfRequired(img: Bitmap, path: String): Bitmap {
        val ei = try {
            ExifInterface(path)
        } catch (e: IOException) {
            return img
        }
        val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270f)
            else -> img
        }
    }

    private fun rotateImageIfRequired(img: Bitmap, inputStream: InputStream): Bitmap {
        val ei = try {
            ExifInterface(inputStream)
        } catch (e: IOException) {
            return img
        }
        val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270f)
            else -> img
        }
    }

    private fun rotateImage(img: Bitmap, degree: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree)
        val rotatedImg = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
        if (rotatedImg != img) {
            img.recycle()
        }
        return rotatedImg
    }

    private fun dispatchImportFromGalleryIntent() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun handleSelectedImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) return

            val exifInputStream = contentResolver.openInputStream(uri)
            val correctedBitmap = exifInputStream?.let {
                val result = rotateImageIfRequired(bitmap, it)
                it.close()
                result
            } ?: bitmap

            processIdentification(correctedBitmap)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processIdentification(bitmap: Bitmap) {
        imageViewInitial.setImageBitmap(bitmap)
        imageViewInitial.visibility = View.VISIBLE
        imageViewResult.setImageBitmap(bitmap)
        currentImagePath = saveBitmapToFile(bitmap)

        val (index, confidence) = classifier.classify(bitmap)
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

    private fun showCollectionSelectionDialog(collections: List<JournalCollection>) {
        val titles = collections.map { it.title }.toTypedArray()
        AlertDialog.Builder(this@MainActivity)
            .setTitle("Select Collection")
            .setItems(titles) { _, which ->
                startAddJournalActivity(collections[which].id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startAddJournalActivity(collectionId: Int) {
        val intent = Intent(this, AddJournalEntryActivity::class.java)
        intent.putExtra("CROP_NAME", currentCropName)
        intent.putExtra("IMAGE_PATH", currentImagePath)
        intent.putExtra("COLLECTION_ID", collectionId)
        startActivity(intent)
    }

    private fun showIdentifyLayout() {
        identifyLayout.visibility = View.VISIBLE
        resultLayout.visibility = View.GONE
        viewFinder.visibility = View.VISIBLE
        imageViewInitial.visibility = View.GONE
    }

    private fun showResultLayout() {
        identifyLayout.visibility = View.GONE
        resultLayout.visibility = View.VISIBLE
        viewFinder.visibility = View.GONE
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

    private fun CropInfo.toEntity(): Crop {
        return Crop(
            name = this.name ?: "Unknown",
            scientificName = this.scientificName,
            description = this.description,
            watering = this.watering,
            fertilization = this.fertilization,
            pestControl = this.pestControl,
            uses = this.uses,
            characteristics = this.characteristics ?: emptyList(),
            culinaryUses = this.culinaryUses ?: emptyList(),
            medicinalUses = this.medicinalUses ?: emptyList(),
            harvesting = this.harvesting
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
