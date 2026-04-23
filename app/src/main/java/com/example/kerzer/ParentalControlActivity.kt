package com.example.kerzer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ParentalControlActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parental_control)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val apps = UsageHelper.getInstalledAppsAndUsage(this).sortedBy { it.appName }
        val prefs = PrefsManager(this)

        recyclerView.adapter = AppUsageAdapter(apps, AppUsageAdapter.Mode.PARENTAL_CONTROL, onAppLimitChanged = { app, limit ->
            prefs.setAppLimit(app.packageName, limit)
        })
    }
}
