package com.littlelock.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.littlelock.app.databinding.ActivitySettingsBinding
import com.littlelock.app.data.local.SettingsRepository
import com.littlelock.app.R
import org.koin.android.ext.android.inject

/**
 * Settings activity for parent configuration.
 * Allows configuring app restrictions, time limits, and permission management.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settingsRepository: SettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadSettings()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
    }

    private fun loadSettings() {
        // Load current settings from repository
        binding.editTimeLimit.setText(settingsRepository.dailyLimitMinutes.toString())
        binding.switchScreenTime.isChecked = settingsRepository.screenTimeEnabled
        binding.switchAppRestrictions.isChecked = settingsRepository.appRestrictionsEnabled
        binding.switchWallpaper.isChecked = settingsRepository.wallpaperEnabled
    }

    private fun setupListeners() {
        // Save button
        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        // Usage access settings
        binding.btnUsageAccess.setOnClickListener {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
        }

        // Notification settings
        binding.btnNotificationSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        }

        // Device admin settings
        binding.btnDeviceAdmin.setOnClickListener {
            Toast.makeText(
                this,
                "Device admin setup requires additional configuration",
                Toast.LENGTH_LONG
            ).show()
        }

        // About
        binding.btnAbout.setOnClickListener {
            Toast.makeText(
                this,
                "LittleLock v1.0.0 - Parental Control Launcher",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun saveSettings() {
        // Save time limit
        val timeLimitText = binding.editTimeLimit.text.toString()
        val timeLimit = timeLimitText.toIntOrNull() ?: 120
        settingsRepository.dailyLimitMinutes = timeLimit

        // Save toggles
        settingsRepository.screenTimeEnabled = binding.switchScreenTime.isChecked
        settingsRepository.appRestrictionsEnabled = binding.switchAppRestrictions.isChecked
        settingsRepository.wallpaperEnabled = binding.switchWallpaper.isChecked

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
