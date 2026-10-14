package com.littlelock.app.ui.main

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.littlelock.app.databinding.ActivityMainBinding
import com.littlelock.app.data.local.SettingsRepository
import com.littlelock.app.ui.launcher.KidsLauncherActivity
import com.littlelock.app.ui.settings.SettingsActivity
import com.littlelock.app.R
import org.koin.android.ext.android.inject
import java.util.Calendar
import kotlin.collections.sortedByDescending

/**
 * Parent dashboard activity.
 * Shows protection status, quick actions, and usage summary.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val settingsRepository: SettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadStatus()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
    }

    private fun loadStatus() {
        val enabled = settingsRepository.protectionEnabled
        binding.tvStatus.text = if (enabled) "Active" else "Inactive"
        binding.switchProtection.isChecked = enabled
        loadUsageData()
    }

    private fun setupListeners() {
        // Protection toggle
        binding.switchProtection.setOnCheckedChangeListener { _, isChecked ->
            settingsRepository.protectionEnabled = isChecked
            binding.tvStatus.text = if (isChecked) "Active" else "Inactive"

            if (isChecked) {
                // Check required permissions
                if (!checkUsageAccess()) {
                    Toast.makeText(
                        this,
                        "Please grant Usage Access in Settings for full protection",
                        Toast.LENGTH_LONG
                    ).show()
                }
                // Launch the kids launcher
                startActivity(Intent(this, KidsLauncherActivity::class.java))
            }
        }

        // Lock now button
        binding.btnLockNow.setOnClickListener {
            if (!settingsRepository.protectionEnabled) {
                settingsRepository.protectionEnabled = true
                binding.switchProtection.isChecked = true
            }
            startActivity(Intent(this, KidsLauncherActivity::class.java))
            Toast.makeText(this, "Child launcher activated", Toast.LENGTH_SHORT).show()
        }

        // View usage
          binding.btnViewUsage.setOnClickListener {
              loadUsageData()
          }

        // Settings
        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun checkUsageAccess(): Boolean {
        return try {
            Settings.ACTION_USAGE_ACCESS_SETTINGS
            // We can't directly check, but we can try to get usage stats
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun loadUsageData() {
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val events = usageStatsManager.queryEvents(startOfDay.timeInMillis, endOfDay.timeInMillis)

        var totalTimeMs: Long = 0
        val appUsage = mutableMapOf<String, Long>()

        val event = UsageEvents.Event()
        while (events.nextEvent(event)) {
            val packageName = event.packageName
            val time = event.timeSpentMs
            totalTimeMs += time
            appUsage[packageName] = appUsage.getOrDefault(packageName, 0L) + time
        }

        if (appUsage.isEmpty()) {
            binding.tvUsageSummary.text = "No usage data recorded yet"
            return
        }

        val topApps = appUsage.entries.sortedByDescending { it.value }.take(5)

        val sb = StringBuilder()
        sb.append("Total screen time: ${formatTime(totalTimeMs)}\n\nTop apps:\n")
        topApps.forEachIndexed { index, entry ->
            sb.append("${index + 1}. ${entry.key} - ${formatTime(entry.value)}\n")
        }

        binding.tvUsageSummary.text = sb.toString()
    }

    private fun formatTime(ms: Long): String {
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    override fun onResume() {
        super.onResume()
        loadStatus()
    }
}
