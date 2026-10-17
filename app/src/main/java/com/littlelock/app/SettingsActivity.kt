package com.littlelock.app

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.littlelock.app.databinding.ActivitySettingsBinding

/**
 * Parent settings. Only reachable through the parent PIN: opened without an unlocked [ParentSession] it sends the
 * parent to the PIN pad instead. Leaving it (Done / Back) locks again.
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
        showStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSettings()
        setupListeners()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
    }

    override fun onResume() {
        super.onResume()
        if (!ParentSession.isUnlocked()) {
            startActivity(Intent(this, PinLockActivity::class.java)
                .putExtra(PinLockActivity.EXTRA_MODE, PinLockActivity.MODE_ENTER)
                .putExtra(PinLockActivity.EXTRA_NEXT, PinLockActivity.NEXT_SETTINGS))
            finish()
            return
        }
        ParentSession.unlock() // still here: keep the parent signed in
        showStatus()
    }

    private fun loadSettings() {
        binding.etWebhookUrl.setText(Preferences.getWebhookUrl(this) ?: "")
        binding.cbNotifAppLaunch.isChecked = Preferences.isAppLaunchNotifEnabled(this)
        binding.cbNotifDailySummary.isChecked = Preferences.isDailySummaryEnabled(this)
        binding.cbNotifBlockedApps.isChecked = Preferences.isBlockedAppsNotifEnabled(this)

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
        binding.tvVersion.text = getString(R.string.settings_version, version)
    }

    private fun showStatus() {
        val kidMode = Preferences.isKidMode(this)
        val allowed = Preferences.getAllowedApps(this).size
        binding.tvKidModeStatus.text = if (kidMode) {
            resources.getQuantityString(R.plurals.settings_kid_mode_on_status, allowed, allowed)
        } else {
            getString(R.string.settings_kid_mode_off_status)
        }
        binding.btnKidMode.setText(if (kidMode) R.string.settings_kid_mode_off else R.string.settings_kid_mode_on)
        binding.tvAccessibilityStatus.setText(
            if (isBlockingServiceOn()) R.string.settings_accessibility_on else R.string.settings_accessibility_explain
        )
    }

    private fun setupListeners() {
        binding.btnAllowedApps.setOnClickListener {
            startActivity(Intent(this, AllowedAppsActivity::class.java))
        }
        binding.btnSetHome.setOnClickListener { openHomeSettings() }
        binding.btnKidMode.setOnClickListener {
            val turnOn = !Preferences.isKidMode(this)
            Preferences.setKidMode(this, turnOn)
            if (turnOn) {
                Toast.makeText(this, R.string.settings_kid_mode_now_on, Toast.LENGTH_SHORT).show()
            } else {
                // leaving kid mode: let the parent pick their usual home app
                Toast.makeText(this, R.string.settings_kid_mode_now_off, Toast.LENGTH_LONG).show()
                openHomeSettings()
            }
            showStatus()
        }
        binding.btnChangePin.setOnClickListener {
            startActivity(Intent(this, PinLockActivity::class.java)
                .putExtra(PinLockActivity.EXTRA_MODE, PinLockActivity.MODE_SET)
                .putExtra(PinLockActivity.EXTRA_NEXT, PinLockActivity.NEXT_NONE))
        }
        binding.btnSaveWebhook.setOnClickListener {
            saveSettings()
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }
        binding.btnEnableDeviceAdmin.setOnClickListener {
            deviceAdminResult.launch(Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLE))
        }
        binding.btnEnableAccessibility.setOnClickListener {
            accessibilityResult.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnBack.setOnClickListener { leave() }
    }

    private fun leave() {
        saveSettings()
        ParentSession.lock()
        finish()
    }

    private fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun isBlockingServiceOn(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, AccessibilityMonitorService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun saveSettings() {
        val webhookUrl = binding.etWebhookUrl.text?.toString()?.trim()
        Preferences.setWebhookUrl(this, if (webhookUrl.isNullOrEmpty()) null else webhookUrl)
        Preferences.setAppLaunchNotifEnabled(this, binding.cbNotifAppLaunch.isChecked)
        Preferences.setDailySummaryEnabled(this, binding.cbNotifDailySummary.isChecked)
        Preferences.setBlockedAppsNotifEnabled(this, binding.cbNotifBlockedApps.isChecked)
    }
}
