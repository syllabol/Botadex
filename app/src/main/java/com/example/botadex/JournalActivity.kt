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
import androidx.room.Room
import com.example.botadex.database.BotadexDatabase
import com.example.botadex.database.JournalEntry
import kotlinx.coroutines.launch
import java.io.File

class JournalActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: JournalAdapter
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

        loadJournalEntries()
    }

    private fun loadJournalEntries() {
        lifecycleScope.launch {
            try {
                val entries = db.cropDao().getAllJournal()
                adapter = JournalAdapter(entries.toMutableList(), { entry ->
                    val intent = Intent(this@JournalActivity, AddJournalEntryActivity::class.java)
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
            .setMessage("Are you sure you want to delete this journal entry?")
            .setPositiveButton("Delete") { _, _ ->
                deleteEntry(entry)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteEntry(entry: JournalEntry) {
        lifecycleScope.launch {
            db.cropDao().deleteJournalEntry(entry)
            loadJournalEntries()
        }
    }

    override fun onResume() {
        super.onResume()
        loadJournalEntries()
    }

    class JournalAdapter(
        private val entries: MutableList<JournalEntry>,
        private val onItemClick: (JournalEntry) -> Unit,
        private val onDeleteClick: (JournalEntry) -> Unit
    ) : RecyclerView.Adapter<JournalAdapter.JournalViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JournalViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_journal_card, parent, false)
            return JournalViewHolder(view, onItemClick, onDeleteClick)
        }

        override fun onBindViewHolder(holder: JournalViewHolder, position: Int) {
            holder.bind(entries[position])
        }

        override fun getItemCount() = entries.size

        class JournalViewHolder(
            itemView: View,
            private val onItemClick: (JournalEntry) -> Unit,
            private val onDeleteClick: (JournalEntry) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val image = itemView.findViewById<ImageView>(R.id.journalImage)
            private val name = itemView.findViewById<TextView>(R.id.cropNameText)
            private val date = itemView.findViewById<TextView>(R.id.dateText)
            private val notes = itemView.findViewById<TextView>(R.id.notesText)
            private val deleteBtn = itemView.findViewById<ImageButton>(R.id.deleteButton)
            private var currentEntry: JournalEntry? = null

            init {
                itemView.setOnClickListener {
                    currentEntry?.let { onItemClick(it) }
                }
                deleteBtn.setOnClickListener {
                    currentEntry?.let { onDeleteClick(it) }
                }
            }

            fun bind(entry: JournalEntry) {
                currentEntry = entry
                name.text = entry.cropName.replaceFirstChar { it.uppercase() }
                date.text = entry.date
                notes.text = if (entry.notes.isNotEmpty()) entry.notes else "Documentations"

                if (entry.imagePaths.isNotEmpty()) {
                    val paths = entry.imagePaths.split(",")
                    if (paths.isNotEmpty()) {
                        val firstPath = paths[0]
                        if (firstPath.isNotEmpty()) {
                            val imgFile = File(firstPath)
                            if (imgFile.exists()) {
                                val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                                image.setImageBitmap(bitmap)
                            }
                        }
                    }
                }
            }
        }
    }
}
