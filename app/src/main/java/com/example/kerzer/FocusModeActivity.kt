package com.example.kerzer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FocusModeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_focus_mode)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val apps = UsageHelper.getInstalledAppsAndUsage(this).sortedBy { it.appName }
        val prefs = PrefsManager(this)

        recyclerView.adapter = AppUsageAdapter(apps, AppUsageAdapter.Mode.FOCUS_MODE, onAppBlockedChanged = { app, isBlocked ->
            prefs.setBlockedApp(app.packageName, isBlocked)
        })
    }
}
