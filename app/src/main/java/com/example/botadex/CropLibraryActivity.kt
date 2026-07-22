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
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CropLibraryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CropAdapter
    private var allCrops: List<CropInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_library)

        recyclerView = findViewById(R.id.cropRecyclerView)
        // Programmatic LayoutManager removed to allow XML GridLayoutManager (2 columns) to work

        val searchEditText = findViewById<EditText>(R.id.searchEditText)
        val availableText = findViewById<TextView>(R.id.availableOfflineText)

        allCrops = loadCropData()
        availableText.text = "${allCrops.size} crops available offline"

        adapter = CropAdapter(allCrops) { crop ->
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
    }

    private fun filter(text: String) {
        val filteredList = allCrops.filter {
            it.name?.contains(text, ignoreCase = true) == true || 
            it.scientificName?.contains(text, ignoreCase = true) == true
        }
        adapter.updateList(filteredList)
    }

    private fun loadCropData(): List<CropInfo> {
        return try {
            val jsonString = assets.open("crop_library.json")
                .bufferedReader()
                .use { it.readText() }

            val type = object : TypeToken<Map<String, CropInfo>>() {}.type
            val map: Map<String, CropInfo> = Gson().fromJson(jsonString, type)
            
            map.map { (key, value) -> 
                value.copy(name = key.replaceFirstChar { it.uppercase() })
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    class CropAdapter(
        private var crops: List<CropInfo>,
        private val onItemClick: (CropInfo) -> Unit
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

        fun updateList(newList: List<CropInfo>) {
            crops = newList
            notifyDataSetChanged()
        }

        class CropViewHolder(
            itemView: View,
            private val onItemClick: (CropInfo) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val nameText = itemView.findViewById<TextView>(R.id.cropNameText)
            private val descriptionText = itemView.findViewById<TextView>(R.id.descriptionPreviewText)
            private val cropImageView = itemView.findViewById<ImageView>(R.id.cropImageView)

            fun bind(crop: CropInfo) {
                nameText.text = crop.name
                descriptionText.text = crop.description
                
                // Map crop name to its specific image
                val imageResId = when (crop.name?.lowercase()) {
                    "cassava" -> R.drawable.cassava
                    "tomato" -> R.drawable.tomato
                    "potato" -> R.drawable.potato
                    "ube" -> R.drawable.ube
                    "kamote" -> R.drawable.kamote
                    "onion" -> R.drawable.onion
                    else -> R.drawable.ic_launcher_foreground
                }
                cropImageView.setImageResource(imageResId)

                itemView.setOnClickListener { onItemClick(crop) }
            }
        }
    }
}
