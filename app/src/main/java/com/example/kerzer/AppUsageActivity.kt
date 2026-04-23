package com.example.kerzer

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppUsageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_usage)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val apps = UsageHelper.getInstalledAppsAndUsage(this)
            .filter { it.usageTimeMins > 0 }
            .sortedByDescending { it.usageTimeMins }

        // Populate summary stats
        val tvMostUsed = findViewById<TextView>(R.id.tvMostUsed)
        val tvTotalApps = findViewById<TextView>(R.id.tvTotalApps)

        tvMostUsed.text = if (apps.isNotEmpty()) apps.first().appName else "—"
        tvTotalApps.text = apps.size.toString()

        recyclerView.adapter = AppUsageAdapter(apps, AppUsageAdapter.Mode.USAGE)
    }
}
