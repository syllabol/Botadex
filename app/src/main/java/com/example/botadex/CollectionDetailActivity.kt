package com.example.botadex

import android.annotation.SuppressLint
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
import com.example.botadex.database.BotadexDatabase
import com.example.botadex.database.JournalEntry
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CollectionDetailActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: JournalAdapter
    private lateinit var db: BotadexDatabase
    private var collectionId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collection_detail)

        db = BotadexDatabase.getDatabase(this)

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
                adapter = JournalAdapter(entries, { entry ->
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
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_journal_entry, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("SetTextI18n")
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
            
            // Long click for delete since item_recent_journal_entry might not have a delete button visible
            holder.itemView.setOnLongClickListener {
                onDeleteClick(entry)
                true
            }
        }

        override fun getItemCount() = entries.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val monthDay: TextView = view.findViewById(R.id.tvEntryMonthDay)
            val year: TextView = view.findViewById(R.id.tvEntryYear)
            val title: TextView = view.findViewById(R.id.tvEntryTitle)
            val notes: TextView = view.findViewById(R.id.tvEntryNotes)
            val image: ImageView = view.findViewById(R.id.ivEntryImage)
        }
    }
}
