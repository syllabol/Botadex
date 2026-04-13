package com.example.botadex

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Grid Menu Navigation
        findViewById<View>(R.id.cardIdentify).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<View>(R.id.cardLibrary).setOnClickListener {
            startActivity(Intent(this, CropLibraryActivity::class.java))
        }

        findViewById<View>(R.id.cardJournal).setOnClickListener {
            startActivity(Intent(this, JournalActivity::class.java))
        }

        findViewById<View>(R.id.cardCalendar).setOnClickListener {
            startActivity(Intent(this, RemindersActivity::class.java))
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

        findViewById<View>(R.id.navCalendar).setOnClickListener {
            startActivity(Intent(this, RemindersActivity::class.java))
        }
    }
}
