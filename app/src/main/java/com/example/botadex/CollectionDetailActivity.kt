package com.example.botadex

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.example.botadex.database.BotadexDatabase
import com.example.botadex.database.JournalEntry
import kotlinx.coroutines.launch
import java.io.File

class CollectionDetailActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: JournalAdapter
    private lateinit var db: BotadexDatabase
    private var collectionId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collection_detail)

        db = Room.databaseBuilder(
            applicationContext,
            BotadexDatabase::class.java,
            "botadex-db"
        ).fallbackToDestructiveMigration().build()

        collectionId = intent.getIntExtra("COLLECTION_ID", -1)
        val title = intent.getStringExtra("COLLECTION_TITLE") ?: "Collection"

        findViewById<TextView>(R.id.collectionTitleText).text = title
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.entriesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.addEntryButton).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("TARGET_COLLECTION_ID", collectionId)
            startActivity(intent)
        }

        loadEntries()
    }

    private fun loadEntries() {
        lifecycleScope.launch {
            try {
                val entries = db.cropDao().getJournalEntriesByCollection(collectionId)
                adapter = JournalAdapter(entries.toMutableList(), { entry ->
                    val intent = Intent(this@CollectionDetailActivity, AddJournalEntryActivity::class.java)
                    intent.putExtra("JOURNAL_ID", entry.id)
                    startActivity(intent)
                }, { entry ->
                    showDeleteConfirmationDialog(entry)
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
            loadEntries()
        }
    }

    override fun onResume() {
        super.onResume()
        loadEntries()
    }

    class JournalAdapter(
        private val entries: List<JournalEntry>,
        private val onItemClick: (JournalEntry) -> Unit,
        private val onDeleteClick: (JournalEntry) -> Unit
    ) : RecyclerView.Adapter<JournalAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_journal_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = entries[position]
            holder.name.text = entry.cropName
            holder.date.text = entry.date
            holder.notes.text = entry.notes

            if (entry.imagePaths.isNotEmpty()) {
                val firstPath = entry.imagePaths.split(",")[0]
                val imgFile = File(firstPath)
                if (imgFile.exists()) {
                    holder.image.setImageBitmap(BitmapFactory.decodeFile(imgFile.absolutePath))
                }
            }

            holder.itemView.setOnClickListener { onItemClick(entry) }
            holder.deleteBtn.setOnClickListener { onDeleteClick(entry) }
        }

        override fun getItemCount() = entries.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val image = view.findViewById<ImageView>(R.id.journalImage)
            val name = view.findViewById<TextView>(R.id.cropNameText)
            val date = view.findViewById<TextView>(R.id.dateText)
            val notes = view.findViewById<TextView>(R.id.notesText)
            val deleteBtn = view.findViewById<ImageButton>(R.id.deleteButton)
        }
    }
}
