package com.example.botadex

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.botadex.database.BotadexDatabase
import com.example.botadex.database.Reminder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class RemindersActivity : AppCompatActivity() {

    private lateinit var todayRecyclerView: RecyclerView
    private lateinit var upcomingRecyclerView: RecyclerView
    private lateinit var noTodayRemindersText: TextView
    private lateinit var noUpcomingRemindersText: TextView
    private lateinit var todayAdapter: RemindersAdapter
    private lateinit var upcomingAdapter: RemindersAdapter
    private lateinit var db: BotadexDatabase
    private lateinit var nestedScrollView: NestedScrollView
    
    private val calendar = Calendar.getInstance()
    private val selectedDate = Calendar.getInstance()
    private val today = Calendar.getInstance()

    private lateinit var monthText: TextView
    private lateinit var calendarGrid: TableLayout
    private lateinit var todayLabel: TextView
    
    private val daysWithReminders = mutableSetOf<Int>() // Format: YYYYDDD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminders)

        db = BotadexDatabase.getDatabase(this)
        createNotificationChannel()

        monthText = findViewById(R.id.monthText)
        calendarGrid = findViewById(R.id.calendarGrid)
        todayLabel = findViewById(R.id.todayLabel)
        nestedScrollView = findViewById(R.id.nestedScrollView)
        
        noTodayRemindersText = findViewById(R.id.noTodayRemindersText)
        noUpcomingRemindersText = findViewById(R.id.noUpcomingRemindersText)
        
        todayRecyclerView = findViewById(R.id.todayRecyclerView)
        todayRecyclerView.layoutManager = LinearLayoutManager(this)
        
        upcomingRecyclerView = findViewById(R.id.upcomingRecyclerView)
        upcomingRecyclerView.layoutManager = LinearLayoutManager(this)

        updateMonthYearDisplay()
        checkPermissions()

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        // Setup Month Navigation
        findViewById<View>(R.id.prevMonth).setOnClickListener {
            calendar.add(Calendar.MONTH, -1)
            updateMonthYearDisplay()
            fetchDaysWithReminders()
        }

        findViewById<View>(R.id.nextMonth).setOnClickListener {
            calendar.add(Calendar.MONTH, 1)
            updateMonthYearDisplay()
            fetchDaysWithReminders()
        }

        findViewById<View>(R.id.navHome).setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent) }
        findViewById<View>(R.id.navIdentify).setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        findViewById<View>(R.id.navLibrary).setOnClickListener { startActivity(Intent(this, CropLibraryActivity::class.java)) }
        findViewById<View>(R.id.navJournal).setOnClickListener { startActivity(Intent(this, JournalActivity::class.java)) }
        findViewById<View>(R.id.navCalendar).setOnClickListener { nestedScrollView.smoothScrollTo(0, 0) }

        fetchDaysWithReminders()
        loadRemindersForSelectedDay()
        loadUpcomingReminders()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = ReminderReceiver.CHANNEL_ID
            val name = ReminderReceiver.CHANNEL_NAME
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = "Plant care notifications"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("Botadex", "Error opening exact alarm settings", e)
                }
            }
        }
    }

    private fun scheduleReminderNotification(reminder: Reminder) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(this, ReminderReceiver::class.java).apply {
            action = "com.example.botadex.ACTION_REMIND_${reminder.id}"
            putExtra(ReminderReceiver.EXTRA_PLANT_NAME, reminder.plantName)
            putExtra(ReminderReceiver.EXTRA_TASK_TYPE, reminder.taskType)
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
            data = android.net.Uri.parse("botadex://reminder/${reminder.id}")
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.timestamp, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminder.timestamp, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.e("Botadex", "Permission denied for exact alarm", e)
        }
    }

    private fun cancelReminderNotification(reminder: Reminder) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java).apply {
            action = "com.example.botadex.ACTION_REMIND_${reminder.id}"
            data = android.net.Uri.parse("botadex://reminder/${reminder.id}")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        fetchDaysWithReminders()
        loadRemindersForSelectedDay()
    }

    private fun updateMonthYearDisplay() {
        val format = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        monthText.text = format.format(calendar.time)
    }

    private fun fetchDaysWithReminders() {
        val startCal = calendar.clone() as Calendar
        startCal.set(Calendar.DAY_OF_MONTH, 1)
        startCal.add(Calendar.DAY_OF_MONTH, -7)
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        
        val endCal = calendar.clone() as Calendar
        endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
        endCal.add(Calendar.DAY_OF_MONTH, 7)
        endCal.set(Calendar.HOUR_OF_DAY, 23)

        lifecycleScope.launch {
            val appReminders = db.cropDao().getRemindersInRange(startCal.timeInMillis, endCal.timeInMillis)
            daysWithReminders.clear()
            val cal = Calendar.getInstance()
            appReminders.forEach {
                cal.timeInMillis = it.timestamp
                daysWithReminders.add(cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR))
            }
            populateCalendar()
        }
    }

    private fun populateCalendar() {
        if (calendarGrid.childCount > 1) {
            calendarGrid.removeViews(1, calendarGrid.childCount - 1)
        }

        val tempCal = calendar.clone() as Calendar
        tempCal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) - 1
        tempCal.add(Calendar.DAY_OF_MONTH, -firstDayOfWeek)

        for (i in 0 until 6) {
            val tableRow = TableRow(this).apply {
                layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (13 * resources.displayMetrics.density).toInt()
                }
                gravity = Gravity.CENTER
            }

            for (j in 0 until 7) {
                tableRow.addView(createDayView(tempCal))
                tempCal.add(Calendar.DAY_OF_MONTH, 1)
            }
            calendarGrid.addView(tableRow)
            if (tempCal.get(Calendar.MONTH) != calendar.get(Calendar.MONTH) && i >= 4) break
        }
    }

    private fun createDayView(dayCal: Calendar): View {
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)

        val container = LinearLayout(this).apply {
            layoutParams = TableRow.LayoutParams(0, (40 * resources.displayMetrics.density).toInt(), 1f)
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true
        }

        val textView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (35 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt()
            )
            gravity = Gravity.CENTER
            text = dayCal.get(Calendar.DAY_OF_MONTH).toString()
            textSize = 14f
            typeface = ResourcesCompat.getFont(this@RemindersActivity, R.font.instrument_sans)
        }

        val isToday = isSameDay(dayCal, today)
        val isSelected = isSameDay(dayCal, selectedDate)
        val isCurrentMonth = dayCal.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)
        val dayKey = dayCal.get(Calendar.YEAR) * 1000 + dayCal.get(Calendar.DAY_OF_YEAR)
        val hasReminder = daysWithReminders.contains(dayKey)

        if (isSelected) {
            textView.setBackgroundResource(R.drawable.rounded_green_button)
            textView.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary_green)
            textView.setTextColor(Color.WHITE)
        } else {
            textView.background = null
            if (isToday) {
                textView.setTextColor(Color.parseColor("#2E7D4A"))
                textView.setTypeface(textView.typeface, Typeface.BOLD)
            } else if (isCurrentMonth) {
                textView.setTextColor(Color.BLACK)
            } else {
                textView.setTextColor(Color.parseColor("#BEC2C7"))
            }
        }

        container.addView(textView)

        if (hasReminder && isCurrentMonth) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams((4 * resources.displayMetrics.density).toInt(), (4 * resources.displayMetrics.density).toInt()).apply { topMargin = (2 * resources.displayMetrics.density).toInt() }
                setBackgroundResource(R.drawable.circle_white_bg)
                backgroundTintList = ContextCompat.getColorStateList(this@RemindersActivity, R.color.primary_green)
            }
            container.addView(dot)
        }

        val dayTime = dayCal.timeInMillis
        container.setOnClickListener {
            selectedDate.timeInMillis = dayTime
            populateCalendar()
            loadRemindersForSelectedDay()
        }
        container.setOnLongClickListener {
            selectedDate.timeInMillis = dayTime
            showAddReminderDialog()
            true
        }

        return container
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean =
        cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)

    private fun loadRemindersForSelectedDay() {
        val isToday = isSameDay(selectedDate, today)
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val dateString = dateFormat.format(selectedDate.time)
        
        todayLabel.text = if (isToday) getString(R.string.reminders_for_today) else getString(R.string.reminders_for_date, dateString)

        lifecycleScope.launch {
            val appReminders = db.cropDao().getRemindersForDay(dateString)
            val allReminders = appReminders.sortedBy { it.timestamp }

            if (allReminders.isNotEmpty()) {
                noTodayRemindersText.visibility = View.GONE
                todayRecyclerView.visibility = View.VISIBLE
                todayAdapter = RemindersAdapter(allReminders, false, { r, c -> toggleReminder(r, c) }, { deleteReminder(it) })
                todayRecyclerView.adapter = todayAdapter
            } else {
                noTodayRemindersText.visibility = View.VISIBLE
                todayRecyclerView.visibility = View.GONE
            }
        }
    }

    private fun loadUpcomingReminders() {
        lifecycleScope.launch {
            val upcoming = db.cropDao().getUpcomingReminders(System.currentTimeMillis())
            if (upcoming.isNotEmpty()) {
                noUpcomingRemindersText.visibility = View.GONE
                upcomingRecyclerView.visibility = View.VISIBLE
                upcomingAdapter = RemindersAdapter(upcoming, true, { r, c -> toggleReminder(r, c) }, { deleteReminder(it) })
                upcomingRecyclerView.adapter = upcomingAdapter
            } else {
                noUpcomingRemindersText.visibility = View.VISIBLE
                upcomingRecyclerView.visibility = View.GONE
            }
        }
    }

    private fun toggleReminder(reminder: Reminder, isChecked: Boolean) {
        lifecycleScope.launch {
            db.cropDao().insertReminder(reminder.copy(isCompleted = isChecked))
            if (isChecked) {
                cancelReminderNotification(reminder)
            } else if (reminder.timestamp > System.currentTimeMillis()) {
                scheduleReminderNotification(reminder)
            }
            loadRemindersForSelectedDay()
            loadUpcomingReminders()
        }
    }

    private fun deleteReminder(reminder: Reminder) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_reminder_title)
            .setMessage(getString(R.string.delete_reminder_msg, reminder.plantName))
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    cancelReminderNotification(reminder)
                    db.cropDao().deleteReminder(reminder)
                    fetchDaysWithReminders()
                    loadRemindersForSelectedDay()
                    loadUpcomingReminders()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadCropLibraryNames(): List<String> {
        return try {
            val jsonString = assets.open("crop_library.json").bufferedReader().use { it.readText() }
            val map: Map<String, CropInfo> = Gson().fromJson(jsonString, object : TypeToken<Map<String, CropInfo>>() {}.type)
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

        val tempCalendar = selectedDate.clone() as Calendar
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        
        editDate.setText(dateFormat.format(tempCalendar.time))
        editTime.setText(timeFormat.format(tempCalendar.time))

        // Updated: Use actual collection titles from your journal
        lifecycleScope.launch {
            val collections = db.cropDao().getAllCollectionsOnce()
            val titles = if (collections.isNotEmpty()) {
                collections.map { it.title }.sorted()
            } else {
                loadCropLibraryNames()
            }
            
            runOnUiThread {
                spinnerCropName.adapter = ArrayAdapter(this@RemindersActivity, android.R.layout.simple_spinner_dropdown_item, titles)
            }
        }

        spinnerTask.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Watering", "Fertilizing", "Pest Control", "Harvesting"))

        editDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                tempCalendar.set(y, m, d)
                editDate.setText(dateFormat.format(tempCalendar.time))
            }, tempCalendar.get(Calendar.YEAR), tempCalendar.get(Calendar.MONTH), tempCalendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        editTime.setOnClickListener {
            TimePickerDialog(this, { _, h, min ->
                tempCalendar.set(Calendar.HOUR_OF_DAY, h)
                tempCalendar.set(Calendar.MINUTE, min)
                tempCalendar.set(Calendar.SECOND, 0)
                tempCalendar.set(Calendar.MILLISECOND, 0)
                editTime.setText(timeFormat.format(tempCalendar.time))
            }, tempCalendar.get(Calendar.HOUR_OF_DAY), tempCalendar.get(Calendar.MINUTE), false).show()
        }

        val alertDialog = AlertDialog.Builder(this).setView(dialogView).create()
        btnSave.setOnClickListener {
            if (tempCalendar.timeInMillis <= System.currentTimeMillis()) {
                Toast.makeText(this, "Please select a future time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val reminder = Reminder(
                plantName = spinnerCropName.selectedItem.toString(),
                taskType = spinnerTask.selectedItem.toString(),
                date = editDate.text.toString(),
                time = editTime.text.toString(),
                timestamp = tempCalendar.timeInMillis
            )
            lifecycleScope.launch {
                val id = db.cropDao().insertReminder(reminder)
                val insertedReminder = reminder.copy(id = id.toInt())
                scheduleReminderNotification(insertedReminder)
                
                fetchDaysWithReminders()
                loadRemindersForSelectedDay()
                loadUpcomingReminders()
                alertDialog.dismiss()
            }
        }
        alertDialog.show()
    }

    class RemindersAdapter(
        private val reminders: List<Reminder>,
        private val showDate: Boolean,
        private val onToggle: (Reminder, Boolean) -> Unit,
        private val onDelete: (Reminder) -> Unit
    ) : RecyclerView.Adapter<RemindersAdapter.ReminderViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder_card, parent, false)
            return ReminderViewHolder(view)
        }

        override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
            val reminder = reminders[position]
            holder.bind(reminder, showDate, onToggle)
            holder.itemView.setOnLongClickListener {
                onDelete(reminder)
                true
            }
        }

        override fun getItemCount() = reminders.size

        class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val name = itemView.findViewById<TextView>(R.id.cropNameText)
            private val task = itemView.findViewById<TextView>(R.id.taskTypeText)
            private val dateTime = itemView.findViewById<TextView>(R.id.dateTimeText)
            private val icon = itemView.findViewById<ImageView>(R.id.taskIcon)
            private val checkbox = itemView.findViewById<CheckBox>(R.id.reminderCheckbox)

            fun bind(reminder: Reminder, showDate: Boolean, onToggle: (Reminder, Boolean) -> Unit) {
                // Updated: use plantName from journal
                name.text = reminder.plantName
                task.text = reminder.taskType
                dateTime.text = if (showDate) "${reminder.date}\n${reminder.time}" else reminder.time
                
                checkbox.setOnCheckedChangeListener(null)
                checkbox.isChecked = reminder.isCompleted
                
                checkbox.visibility = View.VISIBLE
                if (reminder.isCompleted) {
                    name.paintFlags = name.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    name.setTextColor(Color.GRAY)
                    task.setTextColor(Color.GRAY)
                } else {
                    name.paintFlags = name.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    name.setTextColor(Color.parseColor("#1F4D2E"))
                    task.setTextColor(Color.BLACK)
                }
                checkbox.setOnCheckedChangeListener { _, isChecked -> onToggle(reminder, isChecked) }

                val iconRes = when {
                    reminder.taskType.contains("Water", true) -> R.drawable.ic_watering
                    reminder.taskType.contains("Fertilizing", true) -> R.drawable.ic_fertilizing
                    reminder.taskType.contains("Harvest", true) -> R.drawable.ic_harvesting
                    reminder.taskType.contains("Pest", true) -> R.drawable.ic_pest_control
                    else -> android.R.drawable.ic_menu_today
                }
                icon.setImageResource(iconRes)
            }
        }
    }

    data class CropInfo(
        val name: String,
        val scientificName: String,
        val category: String
    )

    data class CropLibraryItem(
        val name: String,
        val category: String
    )
}
