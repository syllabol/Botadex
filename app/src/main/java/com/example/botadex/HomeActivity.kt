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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.botadex.database.BotadexDatabase
import com.example.botadex.database.JournalCollection
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var vpFeatures: ViewPager2
    private lateinit var llPagination: LinearLayout
    private lateinit var rvMyCrops: RecyclerView
    private lateinit var myCropsAdapter: MyCropsAdapter
    private lateinit var db: BotadexDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        db = BotadexDatabase.getDatabase(this)

        // Setup ViewPager2 for Feature Cards
        vpFeatures = findViewById(R.id.vpFeatures)
        llPagination = findViewById(R.id.llPagination)

        val features = listOf(
            FeatureCard(
                "Identify Crop",
                "Scan or upload a photo to identify your root crop",
                R.drawable.kamote,
                R.drawable.cam__1_,
                MainActivity::class.java
            ),
            FeatureCard(
                "Crop Library",
                "Browse crop information and cultivation guides offline",
                R.drawable.potato,
                R.drawable.libr__1_,
                CropLibraryActivity::class.java
            ),
            FeatureCard(
                "My Crops",
                "Journal your farming journey and track plant growth",
                R.drawable.tomato,
                R.drawable.journal,
                JournalActivity::class.java
            ),
            FeatureCard(
                "Calendar",
                "Check your reminders to never miss on watering or harvesting time",
                R.drawable.onion,
                R.drawable.calendar__1_,
                RemindersActivity::class.java
            )
        )

        vpFeatures.adapter = FeatureAdapter(features)
        setupPaginationDots(features.size)

        vpFeatures.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePaginationDots(position)
            }
        })

        setupRecyclerViews()
        observeData()

        // Hidden Admin Mode Trigger: Long click on the logo card
        findViewById<View>(R.id.logoCard).setOnLongClickListener {
            showAdminPinDialog()
            true
        }

        // My Crops: View All -> Redirect to Journal Activity as per request
        findViewById<View>(R.id.btnViewAll).setOnClickListener {
            startActivity(Intent(this, JournalActivity::class.java))
        }

        // Bottom Navigation
        findViewById<View>(R.id.navLibrary).setOnClickListener {
            startActivity(Intent(this, CropLibraryActivity::class.java))
        }

        findViewById<View>(R.id.navJournal).setOnClickListener {
            startActivity(Intent(this, JournalActivity::class.java))
        }

        findViewById<View>(R.id.navCalendar).setOnClickListener {
            startActivity(Intent(this, RemindersActivity::class.java))
        }

        // Floating Camera Button (Ellipse 1)
        findViewById<View>(R.id.navIdentify).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
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
    }

    private fun observeData() {
        lifecycleScope.launch {
            db.cropDao().getAllCollectionsFlow().collectLatest { collections ->
                myCropsAdapter.setCollections(collections.take(5)) // Show only top 5 on Home
            }
        }
    }

    private fun setupPaginationDots(size: Int) {
        llPagination.removeAllViews()
        for (i in 0 until size) {
            val dot = View(this)
            val params = LinearLayout.LayoutParams(dpToPx(10), dpToPx(10))
            params.setMargins(dpToPx(5), 0, dpToPx(5), 0)
            dot.layoutParams = params
            dot.setBackgroundResource(R.drawable.circle_white_bg)
            dot.backgroundTintList =
                getColorStateList(if (i == 0) R.color.brand_green else android.R.color.darker_gray)
            llPagination.addView(dot)
        }
    }

    private fun updatePaginationDots(position: Int) {
        for (i in 0 until llPagination.childCount) {
            val dot = llPagination.getChildAt(i)
            dot.backgroundTintList =
                getColorStateList(if (i == position) R.color.brand_green else android.R.color.darker_gray)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun showAdminPinDialog() {
        val pinEditText = EditText(this)
        pinEditText.hint = "Enter Admin PIN"
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 20, 50, 20)
        layout.addView(pinEditText)

        AlertDialog.Builder(this)
            .setTitle("Admin Mode Access")
            .setMessage("Please enter the secret PIN to continue.")
            .setView(layout)
            .setPositiveButton("Access") { _, _ ->
                val enteredPin = pinEditText.text.toString()
                if (enteredPin == "1234") { // Default secret PIN
                    startActivity(Intent(this, AdminActivity::class.java))
                } else {
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                    Toast.makeText(this@HomeActivity, "Collection deleted", Toast.LENGTH_SHORT).show()
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

    data class FeatureCard(
        val title: String,
        val desc: String,
        val illustration: Int,
        val icon: Int,
        val targetActivity: Class<*>
    )

    inner class FeatureAdapter(private val items: List<FeatureCard>) :
        RecyclerView.Adapter<FeatureAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tvFeatureTitle)
            val desc: TextView = view.findViewById(R.id.tvFeatureDesc)
            val illustration: ImageView = view.findViewById(R.id.ivFeatureIllustration)
            val icon: ImageView = view.findViewById(R.id.ivFeatureIcon)
            val container: View = view.findViewById(R.id.cardContainer)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_feature_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.desc.text = item.desc
            holder.illustration.setImageResource(item.illustration)
            holder.icon.setImageResource(item.icon)
            holder.container.setOnClickListener {
                it.context.startActivity(Intent(it.context, item.targetActivity))
            }
        }

        override fun getItemCount() = items.size
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
                    view.setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.status_growing_text))
                }
                "Harvested" -> {
                    view.setBackgroundResource(R.drawable.bg_chip_harvested)
                    view.setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.status_harvested_text))
                }
                "Archived" -> {
                    view.setBackgroundResource(R.drawable.bg_chip_archived)
                    view.setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.nav_inactive))
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
                    view.setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.status_healthy_text))
                }
                "Attention" -> {
                    view.setBackgroundResource(R.drawable.bg_chip_attention)
                    view.setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.status_attention_text))
                }
                "Warning" -> {
                    view.setBackgroundResource(R.drawable.bg_chip_warning)
                    view.setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.status_warning_text))
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
}
