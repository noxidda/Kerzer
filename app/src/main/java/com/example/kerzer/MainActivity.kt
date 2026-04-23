package com.example.kerzer

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var switchFocusMode: CompoundButton
    private lateinit var prefs: PrefsManager
    private lateinit var infoPanel: View
    private lateinit var infoButton: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsManager(this)
        switchFocusMode = findViewById(R.id.switchFocusMode)
        infoPanel = findViewById(R.id.infoPanel)
        infoButton = findViewById(R.id.infoButton)

        infoButton.setOnClickListener {
            infoPanel.visibility = if (infoPanel.visibility == View.VISIBLE) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }

        findViewById<View>(R.id.btnAppUsage).setOnClickListener {
            startActivity(Intent(this, AppUsageActivity::class.java))
        }

        findViewById<View>(R.id.btnFocusMode).setOnClickListener {
            startActivity(Intent(this, FocusModeActivity::class.java))
        }

        findViewById<View>(R.id.btnParentalControl).setOnClickListener {
            startActivity(Intent(this, ParentalControlActivity::class.java))
        }

        switchFocusMode.isChecked = prefs.isFocusModeActive()
        switchFocusMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.setFocusModeActive(isChecked)
            if (isChecked) {
                checkPermissionsAndStartService()
            } else {
                stopService(Intent(this, BlockerService::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, R.string.grant_permissions, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } else if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        } else {
            val totalMins = UsageHelper.getTotalUsageTimeMins(this)
            val hours = totalMins / 60
            val mins = totalMins % 60
            findViewById<TextView>(R.id.tvTotalTime).text = "$hours"
            findViewById<TextView>(R.id.tvTotalTimeMinsLabel).text = "$mins"
            

            
            // To ensure parental controls are active even if focus mode is off, 
            // since we manage both in the same service:
            checkPermissionsAndStartService()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkPermissionsAndStartService() {
        if (hasUsageStatsPermission() && Settings.canDrawOverlays(this)) {
            val serviceIntent = Intent(this, BlockerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }
}