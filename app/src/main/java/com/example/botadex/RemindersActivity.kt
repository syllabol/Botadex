package com.example.botadex

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.example.botadex.database.BotadexDatabase
import com.example.botadex.database.Reminder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class RemindersActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RemindersAdapter
    private lateinit var db: BotadexDatabase
    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminders)

        db = Room.databaseBuilder(
            applicationContext,
            BotadexDatabase::class.java,
            "botadex-db"
        ).fallbackToDestructiveMigration().build()

        recyclerView = findViewById(R.id.remindersRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        
        findViewById<View>(R.id.addReminderButton).setOnClickListener {
            showAddReminderDialog()
        }

        // Bottom Navigation
        findViewById<View>(R.id.navIdentify).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<View>(R.id.navLibrary).setOnClickListener {
            startActivity(Intent(this, CropLibraryActivity::class.java))
        }
        findViewById<View>(R.id.navJournal).setOnClickListener {
            startActivity(Intent(this, JournalActivity::class.java))
        }
        // navCalendar is current activity

        loadReminders()
    }

    private fun loadReminders() {
        lifecycleScope.launch {
            val reminders = db.cropDao().getAllReminders()
            adapter = RemindersAdapter(reminders)
            recyclerView.adapter = adapter
        }
    }

    private fun loadCropNames(): List<String> {
        return try {
            val jsonString = assets.open("crop_library.json")
                .bufferedReader()
                .use { it.readText() }

            val type = object : TypeToken<Map<String, CropInfo>>() {}.type
            val map: Map<String, CropInfo> = Gson().fromJson(jsonString, type)
            
            map.keys.map { it.replaceFirstChar { char -> char.uppercase() } }.sorted()
        } catch (e: Exception) {
            listOf("Cassava", "Kamote", "Onion", "Potato", "Tomato")
        }
    }

    private fun showAddReminderDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_reminder, null)
        val spinnerCropName = dialogView.findViewById<Spinner>(R.id.spinnerCropName)
        val spinnerTask = dialogView.findViewById<Spinner>(R.id.spinnerTask)
        val editDate = dialogView.findViewById<EditText>(R.id.editDate)
        val editTime = dialogView.findViewById<EditText>(R.id.editTime)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)

        // Setup Crop Spinner
        val cropNames = loadCropNames()
        val adapterCrops = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cropNames)
        spinnerCropName.adapter = adapterCrops

        // Setup Task Spinner
        val tasks = arrayOf("Water plants", "Apply fertilizer", "Harvest")
        val adapterSpinner = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tasks)
        spinnerTask.adapter = adapterSpinner

        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            val format = SimpleDateFormat("MMMM dd", Locale.getDefault())
            editDate.setText(format.format(calendar.time))
        }

        editDate.setOnClickListener {
            DatePickerDialog(this, dateSetListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            val format = SimpleDateFormat("h:mm a", Locale.getDefault())
            editTime.setText(format.format(calendar.time))
        }

        editTime.setOnClickListener {
            TimePickerDialog(this, timeSetListener, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
        }

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnSave.setOnClickListener {
            val cropName = spinnerCropName.selectedItem.toString()
            val task = spinnerTask.selectedItem.toString()
            val dateStr = editDate.text.toString()
            val timeStr = editTime.text.toString()

            if (dateStr.isEmpty() || timeStr.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val reminder = Reminder(cropName = cropName, taskType = task, date = dateStr, time = timeStr)
            lifecycleScope.launch {
                db.cropDao().insertReminder(reminder)
                syncToCalendar(cropName, task)
                loadReminders()
                alertDialog.dismiss()
            }
        }

        alertDialog.show()
    }

    private fun syncToCalendar(cropName: String, task: String) {
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, "$task: $cropName")
            .putExtra(CalendarContract.Events.DESCRIPTION, "Botadex Farming Reminder")
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, calendar.timeInMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, calendar.timeInMillis + 60 * 60 * 1000)
        startActivity(intent)
    }

    class RemindersAdapter(private val reminders: List<Reminder>) :
        RecyclerView.Adapter<RemindersAdapter.ReminderViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_reminder_card, parent, false)
            return ReminderViewHolder(view)
        }

        override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
            holder.bind(reminders[position])
        }

        override fun getItemCount() = reminders.size

        class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val name = itemView.findViewById<TextView>(R.id.cropNameText)
            private val task = itemView.findViewById<TextView>(R.id.taskTypeText)
            private val dateTime = itemView.findViewById<TextView>(R.id.dateTimeText)
            private val icon = itemView.findViewById<ImageView>(R.id.taskIcon)

            fun bind(reminder: Reminder) {
                name.text = reminder.cropName
                task.text = reminder.taskType
                dateTime.text = "${reminder.date}  at  ${reminder.time}"
                
                when {
                    reminder.taskType.contains("Water", ignoreCase = true) -> {
                        icon.setImageResource(R.drawable._aa81a892985725a915157479d7c0b4d7bbefcf2) // Placeholder
                    }
                    reminder.taskType.contains("Fertilizer", ignoreCase = true) -> {
                        icon.setImageResource(R.drawable.d8f3fd12ec12732af9a1fce41011dc460746599a)
                    }
                    reminder.taskType.contains("Harvest", ignoreCase = true) -> {
                        icon.setImageResource(R.drawable.w90a89e78afoasjdoa79sd3)
                    }
                    else -> {
                        icon.setImageResource(android.R.drawable.ic_menu_today) // Placeholder
                    }
                }
            }
        }
    }
}
