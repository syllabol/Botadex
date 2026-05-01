package com.example.botadex

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.example.botadex.database.BotadexDatabase
import com.example.botadex.database.JournalCollection
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class JournalActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CollectionAdapter
    private lateinit var db: BotadexDatabase

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_journal)

        db = Room.databaseBuilder(
            applicationContext,
            BotadexDatabase::class.java,
            "botadex-db"
        ).fallbackToDestructiveMigration().build()

        recyclerView = findViewById(R.id.journalRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.addCollectionButton).setOnClickListener {
            showAddCollectionDialog()
        }

        // Bottom Navigation
        findViewById<View>(R.id.navIdentify).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
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

        loadCollections()
    }

    private fun loadCollections() {
        lifecycleScope.launch {
            try {
                val collections = db.cropDao().getAllCollections()
                adapter = CollectionAdapter(collections.toMutableList(), { collection ->
                    val intent = Intent(this@JournalActivity, CollectionDetailActivity::class.java)
                    intent.putExtra("COLLECTION_ID", collection.id)
                    intent.putExtra("COLLECTION_TITLE", collection.title)
                    startActivity(intent)
                }, { collection ->
                    showDeleteCollectionDialog(collection)
                })
                recyclerView.adapter = adapter
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showAddCollectionDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_collection, null)
        val editTitle = dialogView.findViewById<EditText>(R.id.editCollectionTitle)
        
        AlertDialog.Builder(this)
            .setTitle("New Collection")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val title = editTitle.text.toString()
                if (title.isNotEmpty()) {
                    createCollection(title)
                } else {
                    Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createCollection(title: String) {
        val date = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date())
        val collection = JournalCollection(title = title, cropName = "", date = date)
        lifecycleScope.launch {
            db.cropDao().insertCollection(collection)
            loadCollections()
        }
    }

    private fun showDeleteCollectionDialog(collection: JournalCollection) {
        AlertDialog.Builder(this)
            .setTitle("Delete Collection")
            .setMessage("Are you sure you want to delete '${collection.title}' and all its entries?")
            .setPositiveButton("Delete") { _, _ ->
                deleteCollection(collection)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCollection(collection: JournalCollection) {
        lifecycleScope.launch {
            db.cropDao().deleteCollection(collection)
            loadCollections()
        }
    }

    override fun onResume() {
        super.onResume()
        loadCollections()
    }

    class CollectionAdapter(
        private val collections: List<JournalCollection>,
        private val onItemClick: (JournalCollection) -> Unit,
        private val onDeleteClick: (JournalCollection) -> Unit
    ) : RecyclerView.Adapter<CollectionAdapter.CollectionViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollectionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_collection_card, parent, false)
            return CollectionViewHolder(view, onItemClick, onDeleteClick)
        }

        override fun onBindViewHolder(holder: CollectionViewHolder, position: Int) {
            holder.bind(collections[position])
        }

        override fun getItemCount() = collections.size

        class CollectionViewHolder(
            itemView: View,
            private val onItemClick: (JournalCollection) -> Unit,
            private val onDeleteClick: (JournalCollection) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val title = itemView.findViewById<TextView>(R.id.collectionTitleText)
            private val info = itemView.findViewById<TextView>(R.id.collectionInfoText)
            private val deleteBtn = itemView.findViewById<ImageButton>(R.id.deleteCollectionButton)
            private var currentCollection: JournalCollection? = null

            init {
                itemView.setOnClickListener {
                    currentCollection?.let { onItemClick(it) }
                }
                deleteBtn.setOnClickListener {
                    currentCollection?.let { onDeleteClick(it) }
                }
            }

            fun bind(collection: JournalCollection) {
                currentCollection = collection
                title.text = collection.title
                info.text = "Created: ${collection.date}"
            }
        }
    }
}
