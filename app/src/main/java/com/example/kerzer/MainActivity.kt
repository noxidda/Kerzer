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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var switchFocusMode: CompoundButton
    private lateinit var prefs: PrefsManager
    private lateinit var infoPanel: View
    private lateinit var infoButton: View
    private val aiExecutor = Executors.newSingleThreadExecutor()

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

        findViewById<View>(R.id.btnAiSuggest).setOnClickListener {
            requestAiLimitSuggestions()
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

    private fun requestAiLimitSuggestions() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_API_KEY") {
            Toast.makeText(this, "Set gemini.api.key in local.properties", Toast.LENGTH_LONG).show()
            return
        }

        val topApps = UsageHelper.getInstalledAppsAndUsage(this)
            .filter { it.usageTimeMins > 0 }
            .sortedByDescending { it.usageTimeMins }
            .take(5)

        if (topApps.isEmpty()) {
            Toast.makeText(this, "Not enough usage data yet", Toast.LENGTH_SHORT).show()
            return
        }

        val prompt = buildString {
            appendLine("You are a digital wellbeing assistant.")
            appendLine("Suggest a daily time limit in minutes for each app for better health.")
            appendLine("Output format: each line 'AppName: Xm' only. No extra commentary.")
            appendLine("Apps:")
            topApps.forEach { app ->
                appendLine("- ${app.appName}: ${app.usageTimeMins} minutes today")
            }
        }

        Toast.makeText(this, "Fetching AI suggestions...", Toast.LENGTH_SHORT).show()

        aiExecutor.execute {
            val result = fetchGeminiText(apiKey, prompt)
            runOnUiThread {
                if (result == null) {
                    Toast.makeText(this, "AI request failed", Toast.LENGTH_LONG).show()
                } else if (result.contains("ListModels", ignoreCase = true)) {
                    Toast.makeText(this, "Fetching available models...", Toast.LENGTH_SHORT).show()
                    aiExecutor.execute {
                        val models = fetchGeminiModels(apiKey)
                        runOnUiThread {
                            androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Available Gemini models")
                                .setMessage(models ?: "Failed to list models")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                } else {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("AI limit suggestions")
                        .setMessage(result)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun fetchGeminiText(apiKey: String, prompt: String): String? {
        return try {
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.doOutput = true

            val parts = JSONArray().put(JSONObject().put("text", prompt))
            val contents = JSONArray().put(JSONObject().put("parts", parts))
            val body = JSONObject().put("contents", contents)

            conn.outputStream.use { os ->
                os.write(body.toString().toByteArray())
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream.bufferedReader().readText()
            if (responseCode !in 200..299) {
                "Error $responseCode:\n$responseText"
            } else {
                val json = JSONObject(responseText)
                val candidates = json.optJSONArray("candidates") ?: return null
                val content = candidates.getJSONObject(0).getJSONObject("content")
                val partsArr = content.getJSONArray("parts")
                partsArr.getJSONObject(0).getString("text")
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchGeminiModels(apiKey: String): String? {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream.bufferedReader().readText()
            if (responseCode !in 200..299) {
                "Error $responseCode:\n$responseText"
            } else {
                val json = JSONObject(responseText)
                val models = json.optJSONArray("models") ?: return null
                val lines = mutableListOf<String>()
                for (i in 0 until models.length()) {
                    val model = models.getJSONObject(i)
                    val name = model.optString("name")
                    val methods = model.optJSONArray("supportedGenerationMethods")
                    val methodsList = mutableListOf<String>()
                    if (methods != null) {
                        for (j in 0 until methods.length()) {
                            methodsList.add(methods.getString(j))
                        }
                    }
                    lines.add("$name: ${methodsList.joinToString(", ")}")
                }
                lines.joinToString("\n")
            }
        } catch (e: Exception) {
            null
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

    override fun onDestroy() {
        super.onDestroy()
        aiExecutor.shutdown()
    }
}
