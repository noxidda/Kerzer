package com.example.kerzer

import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class AppItem(
    val packageName: String,
    val appName: String,    
    val icon: Drawable,
    val timeLimitMins: Int = -1,
    var isBlocked: Boolean = false,
    val usageTimeMins: Long = 0L
)

class AppUsageAdapter(
    private val apps: List<AppItem>,
    private val mode: Mode,
    private val onAppBlockedChanged: ((AppItem, Boolean) -> Unit)? = null,
    private val onAppLimitChanged: ((AppItem, Int) -> Unit)? = null
) : RecyclerView.Adapter<AppUsageAdapter.ViewHolder>() {

    enum class Mode { USAGE, FOCUS_MODE, PARENTAL_CONTROL }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun getItemCount() = apps.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        private val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
        private val tvUsageOrLimit: TextView = itemView.findViewById(R.id.tvUsageOrLimit)
        private val cbSelect: CheckBox = itemView.findViewById(R.id.cbSelect)
        private val etLimit: EditText = itemView.findViewById(R.id.etLimit)
        private var limitWatcher: TextWatcher? = null

        fun bind(app: AppItem) {
            ivIcon.setImageDrawable(app.icon)
            tvAppName.text = app.appName
            cbSelect.setOnCheckedChangeListener(null)

            when (mode) {
                Mode.USAGE -> {
                    val hours = app.usageTimeMins / 60
                    val mins = app.usageTimeMins % 60
                    // Clean formatting: skip "0h" prefix, show "< 1m" for sub-minute usage
                    val usageText = when {
                        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
                        hours > 0 -> "${hours}h"
                        mins > 0 -> "${mins}m"
                        else -> "< 1m"
                    }
                    tvUsageOrLimit.text = "Used: $usageText"
                    tvUsageOrLimit.visibility = View.VISIBLE
                    cbSelect.visibility = View.GONE
                    etLimit.visibility = View.GONE
                }

                Mode.FOCUS_MODE -> {
                    tvUsageOrLimit.visibility = View.GONE
                    cbSelect.visibility = View.VISIBLE
                    etLimit.visibility = View.GONE
                    cbSelect.isChecked = app.isBlocked
                    cbSelect.setOnCheckedChangeListener { _, isChecked ->
                        onAppBlockedChanged?.invoke(app, isChecked)
                    }
                }

                Mode.PARENTAL_CONTROL -> {
                    // Show current limit as subtitle, or "No limit" if unset
                    tvUsageOrLimit.text = if (app.timeLimitMins > 0) "Limit: ${app.timeLimitMins} min" else "No limit set"
                    tvUsageOrLimit.visibility = View.VISIBLE
                    cbSelect.visibility = View.GONE
                    etLimit.visibility = View.VISIBLE

                    val limitStr = if (app.timeLimitMins > 0) app.timeLimitMins.toString() else ""
                    limitWatcher?.let { etLimit.removeTextChangedListener(it) }
                    etLimit.setText(limitStr)
                    etLimit.setSelection(etLimit.text.length)

                    limitWatcher = object : TextWatcher {
                        override fun afterTextChanged(s: Editable?) {
                            if (!etLimit.isFocused) return
                            val limit = s.toString().toIntOrNull() ?: -1
                            onAppLimitChanged?.invoke(app, limit)
                        }
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    }
                    etLimit.addTextChangedListener(limitWatcher)
                }
            }
        }
    }
}
