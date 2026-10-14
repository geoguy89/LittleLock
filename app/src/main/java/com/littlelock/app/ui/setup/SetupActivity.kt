package com.littlelock.app.ui.setup

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.littlelock.app.databinding.ActivitySetupBinding
import com.littlelock.app.data.local.SettingsRepository
import com.littlelock.app.ui.main.MainActivity
import com.littlelock.app.R
import org.koin.android.ext.android.inject
import java.security.MessageDigest

/**
 * Initial PIN setup activity.
 * Guides parent through setting up a security PIN for the app.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private val settingsRepository: SettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if PIN is already set
        if (settingsRepository.pinHash != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding.btnSetup.setOnClickListener {
            val pin = binding.etPin.text?.toString()?.trim()
            val confirm = binding.etConfirmPin.text?.toString()?.trim()

            if (pin.isNullOrBlank() || confirm.isNullOrBlank()) {
                showError("Please enter both PINs")
                return@setOnClickListener
            }

            if (pin.length < 4) {
                showError("PIN must be at least 4 digits")
                return@setOnClickListener
            }

            if (pin != confirm) {
                showError("PINs do not match")
                return@setOnClickListener
            }

            // Hash and store the PIN
            val hashedPin = hashPin(pin)
            settingsRepository.pinHash = hashedPin
            settingsRepository.isFirstLaunch = false

            Toast.makeText(this, "PIN set successfully", Toast.LENGTH_SHORT).show()

            // Navigate to main activity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = android.view.View.VISIBLE
    }

    private fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(pin.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
