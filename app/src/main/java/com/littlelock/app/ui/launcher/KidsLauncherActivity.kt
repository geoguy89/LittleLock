package com.littlelock.app.ui.launcher

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationCallback
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.littlelock.app.R
import com.littlelock.app.data.local.AllowedAppEntity
import com.littlelock.app.data.local.AllowedAppDao
import com.littlelock.app.data.local.SettingsRepository
import com.littlelock.app.ui.main.MainActivity
import org.koin.android.ext.android.inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import java.security.MessageDigest

/**
 * Child-facing launcher activity.
 * Shows only allowed apps in a grid layout.
 * Long press triggers parent authentication to exit.
 */
class KidsLauncherActivity : AppCompatActivity() {

    private val allowedAppDao: AllowedAppDao by inject()
    private val settingsRepository: SettingsRepository by inject()

    private lateinit var gridApps: GridView
    private lateinit var adapter: AppGridAdapter
    private val appList = mutableListOf<AppItem>()

    // For detecting long press to exit
    private var longPressHandler: Handler? = null
    private var longPressRunnable: Runnable? = null

    // Lockout state
    private var failedAttempts = 0
    private var lockoutEndTime: Long = 0
    private val maxAttempts = 3
    private val lockoutDurationMs = 30_000L // 30 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kids_launcher)

        gridApps = findViewById(R.id.gridApps)

        // Load allowed apps
        lifecycleScope.launch {
            val allowedApps = allowedAppDao.getAllowedApps()
            appList.clear()
            appList.addAll(allowedApps.map { it.toAppItem(packageManager) })
            adapter = AppGridAdapter(appList)
            gridApps.adapter = adapter
        }

        // Set up click listener for apps
        gridApps.setOnItemClickListener { _, _, position, _ ->
            val app = appList[position]
            launchApp(app.packageName)
        }

        // Set up long press to exit (requires PIN)
        gridApps.setOnLongClickListener {
            // Show PIN prompt to exit
            exitWithAuthentication()
            true
        }

        // Prevent switching to other apps
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } catch (e: Exception) {
            // App not available, ignore
        }
    }

    private fun exitWithAuthentication() {
        // Check if currently locked out
        if (isLockedOut()) {
            val remainingSeconds = ((lockoutEndTime - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(1)
            showPinDialog(null, "Too many attempts. Try again in $remainingSeconds seconds.")
            return
        }

        // Check if a PIN is set
        val storedPinHash = settingsRepository.pinHash
        if (storedPinHash == null) {
            showPinDialog(null, getString(R.string.pin_error_no_pin))
            return
        }

        // Check if biometric is available
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS

        showPinDialog(storedPinHash, biometricAvailable = canAuthenticate)
    }

    private fun isLockedOut(): Boolean {
        if (lockoutEndTime == 0L) return false
        if (System.currentTimeMillis() < lockoutEndTime) return true
        // Lockout expired, reset
        failedAttempts = 0
        lockoutEndTime = 0
        return false
    }

    private fun recordFailedAttempt() {
        failedAttempts++
        if (failedAttempts >= maxAttempts) {
            lockoutEndTime = System.currentTimeMillis() + lockoutDurationMs
        }
    }

    private fun resetAttempts() {
        failedAttempts = 0
        lockoutEndTime = 0
    }

    private fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(pin.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun showPinDialog(
        storedPinHash: String?,
        prefillError: String? = null,
        biometricAvailable: Boolean = false
    ) {
        // Inflate the custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_pin_entry, null)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etPin = dialogView.findViewById<EditText>(R.id.etPinInput)
        val tvError = dialogView.findViewById<TextView>(R.id.tvError)
        val btnVerify = dialogView.findViewById<Button>(R.id.btnVerify)
        val btnBiometric = dialogView.findViewById<Button>(R.id.btnBiometric)

        // Show error if prefilled
        if (!prefillError.isNullOrEmpty()) {
            tvError.text = prefillError
            tvError.visibility = View.VISIBLE
        }

        // Show biometric button only if available
        btnBiometric.visibility = if (biometricAvailable) View.VISIBLE else View.GONE

        // Build and show the dialog
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnVerify.setOnClickListener {
            val enteredPin = etPin.text.toString().trim()
            if (enteredPin.isEmpty()) {
                tvError.text = getString(R.string.pin_dialog_hint)
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            if (storedPinHash == null) {
                tvError.text = getString(R.string.pin_error_no_pin)
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            val enteredHash = hashPin(enteredPin)
            if (enteredHash == storedPinHash) {
                // PIN correct - exit
                resetAttempts()
                dialog.dismiss()
                navigateToMainActivity()
            } else {
                // PIN wrong
                recordFailedAttempt()
                if (isLockedOut()) {
                    val remainingSeconds = ((lockoutEndTime - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(1)
                    tvError.text = getString(R.string.pin_error_locked, remainingSeconds)
                } else {
                    tvError.text = getString(R.string.pin_error_wrong)
                }
                tvError.visibility = View.VISIBLE
                etPin.text.clear()
                etPin.requestFocus()
            }
        }

        btnBiometric.setOnClickListener {
            dialog.dismiss()
            promptBiometric(storedPinHash)
        }

        dialog.show()
    }

    private fun promptBiometric(storedPinHash: String?) {
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) {
            // Biometric not available, fall back to PIN
            showPinDialog(storedPinHash, "Biometric not available")
            return
        }

        val biometricPrompt = BiometricPrompt(this, object : AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                // Biometric succeeded - verify PIN hash is set
                if (settingsRepository.pinHash != null) {
                    navigateToMainActivity()
                } else {
                    showPinDialog(null, getString(R.string.pin_error_no_pin))
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Biometric failed - fall back to PIN
                showPinDialog(storedPinHash, "Biometric failed. Enter PIN instead.")
            }

            override fun onAuthenticationError(errorCode: Int, errMsg: CharSequence) {
                super.onAuthenticationError(errorCode, errMsg)
                // User cancelled or error - fall back to PIN
                showPinDialog(storedPinHash, errMsg.toString(), biometricAvailable = false)
            }
        })

        val promptInfo = PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setNegativeButtonText(getString(R.string.biometric_negative))
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Re-lock when returning
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    /**
     * Data class for app items displayed in the grid.
     */
    data class AppItem(
        val packageName: String,
        val appName: String,
        val appIcon: android.graphics.drawable.Drawable?
    )

    /**
     * Adapter for the app grid.
     */
    inner class AppGridAdapter(
        private val apps: List<AppItem>
    ) : BaseAdapter() {

        override fun getCount(): Int = apps.size

        override fun getItem(position: Int): AppItem = apps[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: layoutInflater.inflate(
                android.R.layout.simple_list_item_2,
                parent,
                false
            )

            val app = apps[position]
            val iconView = view.findViewById<ImageView>(android.R.id.icon)
            val textView = view.findViewById<TextView>(android.R.id.text1)

            iconView.setImageDrawable(app.appIcon)
            textView.text = app.appName
            textView.maxLines = 2

            return view
        }
    }

    /**
     * Extension to convert AllowedAppEntity to AppItem.
     */
    private fun AllowedAppEntity.toAppItem(pm: PackageManager): AppItem {
        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            AppItem(
                packageName = packageName,
                appName = if (appName.isNotBlank()) appName else info.loadLabel(pm).toString(),
                appIcon = info.loadIcon(pm)
            )
        } catch (e: PackageManager.NameNotFoundException) {
            AppItem(packageName, appName, null)
        }
    }
}
