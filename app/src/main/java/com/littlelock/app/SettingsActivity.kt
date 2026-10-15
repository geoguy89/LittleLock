package com.littlelock.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.littlelock.app.databinding.ActivitySettingsBinding

/**
 * Settings activity for parental controls.
 * Protected by PIN entry.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val deviceAdminResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Toast.makeText(this, R.string.device_admin_updated, Toast.LENGTH_SHORT).show()
    }

    private val accessibilityResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Toast.makeText(this, R.string.accessibility_updated, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        binding.etWebhookUrl.setText(Preferences.getWebhookUrl(this) ?: "")
        binding.cbNotifAppLaunch.isChecked = Preferences.isAppLaunchNotifEnabled(this)
        binding.cbNotifDailySummary.isChecked = Preferences.isDailySummaryEnabled(this)
        binding.cbNotifBlockedApps.isChecked = Preferences.isBlockedAppsNotifEnabled(this)

        // Show version
        try {
            val pm = packageManager
            val info = pm.getPackageInfo(packageName, 0)
            binding.tvVersion.text = getString(R.string.settings_version, info.versionName ?: "1.0")
        } catch (e: Exception) {
            binding.tvVersion.text = getString(R.string.settings_version, "1.0")
        }
    }

    private fun setupListeners() {
        binding.btnSaveWebhook.setOnClickListener {
            saveSettings()
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }

        binding.btnVerifyPin.setOnClickListener {
            val pin = binding.etPin.text?.toString()?.trim()
            if (pin.isNullOrEmpty()) {
                Toast.makeText(this, R.string.pin_error_short, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (Preferences.getPin(this) == pin) {
                // PIN verified - allow changing
                val newPin = promptNewPin()
                if (newPin != null) {
                    Preferences.setPin(this, newPin)
                    Toast.makeText(this, R.string.pin_set_success, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, R.string.pin_error_wrong, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnEnableDeviceAdmin.setOnClickListener {
            val adminIntent = Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLE)
            deviceAdminResult.launch(adminIntent)
        }

        binding.btnEnableAccessibility.setOnClickListener {
            val accessibilityIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            accessibilityResult.launch(accessibilityIntent)
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun promptNewPin(): String? {
        // Simple approach: open PinLockActivity in set mode
        val intent = Intent(this, PinLockActivity::class.java)
        intent.putExtra("mode", "set")
        startActivity(intent)
        return null // Will be set via PinLockActivity
    }

    private fun saveSettings() {
        val webhookUrl = binding.etWebhookUrl.text?.toString()?.trim()
        Preferences.setWebhookUrl(this, if (webhookUrl.isNullOrEmpty()) null else webhookUrl)
        Preferences.setAppLaunchNotifEnabled(this, binding.cbNotifAppLaunch.isChecked)
        Preferences.setDailySummaryEnabled(this, binding.cbNotifDailySummary.isChecked)
        Preferences.setBlockedAppsNotifEnabled(this, binding.cbNotifBlockedApps.isChecked)
    }
}
