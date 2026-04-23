package com.example.kerzer

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Calendar

object UsageHelper {

    fun getInstalledAppsAndUsage(context: Context): List<AppItem> {
        val pm = context.packageManager
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageStatsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        val usageMap = mutableMapOf<String, Long>()
        for (stats in usageStatsList) {
            usageMap[stats.packageName] = (usageMap[stats.packageName] ?: 0L) + stats.totalTimeInForeground
        }

        // Get standard launcher apps to filter out obscure system services
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

        val prefs = PrefsManager(context)

        return resolveInfos.mapNotNull {
            val packageName = it.activityInfo.packageName
            // Skip self just in case, though might be useful to track
            if (packageName == context.packageName) return@mapNotNull null

            try {
                val appName = it.loadLabel(pm).toString()
                val icon = it.loadIcon(pm)
                val totalTimeMs = usageMap[packageName] ?: 0L
                val totalTimeMins = totalTimeMs / (1000 * 60)

                AppItem(
                    packageName = packageName,
                    appName = appName,
                    icon = icon,
                    timeLimitMins = prefs.getAppLimit(packageName),
                    isBlocked = prefs.getBlockedApps().contains(packageName),
                    usageTimeMins = totalTimeMins
                )
            } catch (e: Exception) {
                null
            }
        }.distinctBy { it.packageName }.sortedByDescending { it.usageTimeMins }
    }

    fun getTotalUsageTimeMins(context: Context): Long {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageEvents = usm.queryEvents(startTime, endTime)
        var totalTime = 0L
        var lastInteractiveTime = 0L

        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    lastInteractiveTime = event.timeStamp
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (lastInteractiveTime > 0) {
                        totalTime += (event.timeStamp - lastInteractiveTime)
                        lastInteractiveTime = 0L
                    }
                }
            }
        }

        // If screen is still on at the end of the query
        if (lastInteractiveTime > 0) {
            totalTime += (endTime - lastInteractiveTime)
        }

        return totalTime / (1000 * 60)
    }
}
