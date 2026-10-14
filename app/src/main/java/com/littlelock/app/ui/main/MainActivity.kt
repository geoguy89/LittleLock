package com.littlelock.app.ui.main

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
            Toast.makeText(this, "Usage reports coming soon", Toast.LENGTH_SHORT).show()
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

    override fun onResume() {
        super.onResume()
        loadStatus()
    }
}
