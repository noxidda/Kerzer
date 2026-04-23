package com.example.kerzer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class BlockerService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: PrefsManager
    private lateinit var usm: UsageStatsManager
    private var overlayView: View? = null
    private var overlayTitle: TextView? = null
    private var overlayMessage: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isOverlayShowing = false
    private var pendingCloseRunnable: Runnable? = null
    private var lastLimitBlockedPackage: String? = null

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = PrefsManager(this)
        usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        createOverlayView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, "KERZER_CHAN")
            .setContentTitle("Kerzer is Running")
            .setContentText("Focus mode or Parental controls active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        // Using specialUse as requested in the plan
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        handler.post(checkRunnable)
        return START_STICKY
    }

    private fun createOverlayView() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        overlayView = LayoutInflater.from(this).inflate(R.layout.view_block_overlay, null)
        overlayTitle = overlayView?.findViewById(R.id.tvBlockTitle)
        overlayMessage = overlayView?.findViewById(R.id.tvBlockMessage)
        try {
            windowManager.addView(overlayView, params)
            overlayView?.visibility = View.GONE
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkForegroundApp() {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10000 // last 10 seconds
        val events = usm.queryEvents(startTime, endTime)
        var latestEvent: UsageEvents.Event? = null
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                if (latestEvent == null || event.timeStamp > latestEvent.timeStamp) {
                    val cloned = UsageEvents.Event()
                    // Re-query or just track name manually since Event doesn't have deep copy method, but we can assume the last RESUMED is the one.
                }
            }
        }
        
        // A simpler way to get the latest foreground package:
        val usageStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        var foregroundPackage = ""
        var lastTimeUsed = 0L
        for (stats in usageStats) {
            if (stats.lastTimeUsed > lastTimeUsed) {
                lastTimeUsed = stats.lastTimeUsed
                foregroundPackage = stats.packageName
            }
        }

        if (foregroundPackage.isNotEmpty() && foregroundPackage != packageName) {
            val isBlockedByFocus = prefs.isFocusModeActive() && prefs.getBlockedApps().contains(foregroundPackage)
            
            // Check parental control limits
            val limitMins = prefs.getAppLimit(foregroundPackage)
            var isBlockedByLimit = false
            if (limitMins > 0) {
                val usageMins = getTodayUsageMins(foregroundPackage)
                if (usageMins >= limitMins) {
                    isBlockedByLimit = true
                }
            }

            if (isBlockedByFocus || isBlockedByLimit) {
                if (isBlockedByLimit) {
                    showOverlay(
                        title = "Limit reached",
                        message = "This app exceeded its daily limit. Closing in 3 seconds."
                    )
                    scheduleCloseToHome(foregroundPackage)
                } else {
                    showOverlay(
                        title = "Blocked",
                        message = "This app is blocked by focus mode."
                    )
                    cancelPendingClose()
                }
            } else {
                hideOverlay()
                cancelPendingClose()
            }
        } else {
            hideOverlay() // we are in the launcher or kerzer app
            cancelPendingClose()
        }
    }

    private fun getTodayUsageMins(pkg: String): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, calendar.timeInMillis, System.currentTimeMillis())
        return (stats.find { it.packageName == pkg }?.totalTimeInForeground ?: 0L) / (1000 * 60)
    }

    private fun showOverlay(title: String, message: String) {
        overlayTitle?.text = title
        overlayMessage?.text = message
        if (!isOverlayShowing) {
            overlayView?.visibility = View.VISIBLE
            isOverlayShowing = true
        }
    }

    private fun hideOverlay() {
        if (isOverlayShowing) {
            overlayView?.visibility = View.GONE
            isOverlayShowing = false
        }
    }

    private fun scheduleCloseToHome(packageNameToClose: String) {
        if (lastLimitBlockedPackage == packageNameToClose && pendingCloseRunnable != null) return
        cancelPendingClose()
        lastLimitBlockedPackage = packageNameToClose
        pendingCloseRunnable = Runnable { forceCloseToHome() }
        handler.postDelayed(pendingCloseRunnable!!, 3000)
    }

    private fun cancelPendingClose() {
        pendingCloseRunnable?.let { handler.removeCallbacks(it) }
        pendingCloseRunnable = null
        lastLimitBlockedPackage = null
    }

    private fun forceCloseToHome() {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("KERZER_CHAN", "Kerzer Background Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
