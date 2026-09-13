package com.example.botadex

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.botadex.database.BotadexDatabase
import com.example.botadex.database.Crop
import kotlinx.coroutines.launch

class CropLibraryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CropAdapter
    private var allCrops: List<Crop> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_library)

        recyclerView = findViewById(R.id.cropRecyclerView)

        val searchEditText = findViewById<EditText>(R.id.searchEditText)
        val availableText = findViewById<TextView>(R.id.availableOfflineText)

        adapter = CropAdapter(emptyList()) { crop ->
            val intent = Intent(this, CropDetailActivity::class.java)
            intent.putExtra("CROP_NAME", crop.name)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.navHome).setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }

        // Bottom Navigation
        findViewById<View>(R.id.navIdentify).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<View>(R.id.navLibrary).setOnClickListener {
            // Already here
        }
        findViewById<View>(R.id.navJournal).setOnClickListener {
            startActivity(Intent(this, JournalActivity::class.java))
        }
        findViewById<View>(R.id.navCalendar).setOnClickListener {
            startActivity(Intent(this, RemindersActivity::class.java))
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadCrops()
    }

    override fun onResume() {
        super.onResume()
        loadCrops()
    }

    private fun loadCrops() {
        lifecycleScope.launch {
            val db = BotadexDatabase.getDatabase(this@CropLibraryActivity)
            allCrops = db.cropDao().getAllCrops()
            findViewById<TextView>(R.id.availableOfflineText).text = "${allCrops.size} crops available offline"
            adapter.updateList(allCrops)
        }
    }

    private fun filter(text: String) {
        val filteredList = allCrops.filter {
            it.name.contains(text, ignoreCase = true) || 
            it.scientificName?.contains(text, ignoreCase = true) == true
        }
        adapter.updateList(filteredList)
    }

    class CropAdapter(
        private var crops: List<Crop>,
        private val onItemClick: (Crop) -> Unit
    ) : RecyclerView.Adapter<CropAdapter.CropViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CropViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_crop_card, parent, false)
            return CropViewHolder(view, onItemClick)
        }

        override fun onBindViewHolder(holder: CropViewHolder, position: Int) {
            holder.bind(crops[position])
        }

        override fun getItemCount() = crops.size

        fun updateList(newList: List<Crop>) {
            crops = newList
            notifyDataSetChanged()
        }

        class CropViewHolder(
            itemView: View,
            private val onItemClick: (Crop) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val nameText = itemView.findViewById<TextView>(R.id.cropNameText)
            private val descriptionText = itemView.findViewById<TextView>(R.id.descriptionPreviewText)
            private val cropImageView = itemView.findViewById<ImageView>(R.id.cropImageView)

            fun bind(crop: Crop) {
                nameText.text = crop.name.replaceFirstChar { it.uppercase() }
                descriptionText.text = crop.description
                
                // Dynamic Image Loading
                val context = itemView.context
                val imageResId = if (!crop.imageUri.isNullOrBlank()) {
                    context.resources.getIdentifier(crop.imageUri, "drawable", context.packageName)
                } else {
                    0
                }

                if (imageResId != 0) {
                    cropImageView.setImageResource(imageResId)
                } else {
                    // Fallback to name-based or default
                    val fallbackId = when (crop.name.lowercase()) {
                        "cassava" -> R.drawable.cassava
                        "tomato" -> R.drawable.tomato
                        "potato" -> R.drawable.potato
                        "ube" -> R.drawable.ube
                        "kamote" -> R.drawable.kamote
                        "onion" -> R.drawable.onion
                        else -> R.drawable.ic_launcher_foreground
                    }
                    cropImageView.setImageResource(fallbackId)
                }

                itemView.setOnClickListener { onItemClick(crop) }
            }
        }
    }
}
