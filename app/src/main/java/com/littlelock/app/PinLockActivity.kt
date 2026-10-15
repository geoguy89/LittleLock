package com.littlelock.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.littlelock.app.databinding.ActivityPinLockBinding

/**
 * PIN lock activity for entering or setting a parental control PIN.
 * Layout uses btn0-btn9, btnClear, ivDot1-ivDot4, tvPinTitle, tvPinError.
 */
class PinLockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinLockBinding
    private var enteredPin = ""
    private var mode: String = "enter" // "enter", "set"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = intent.getStringExtra("mode") ?: "enter"
        updatePinDisplay()

        // Number pad buttons: btn0-btn9
        for (i in 0..9) {
            val btnId = resources.getIdentifier("btn$i", "id", packageName)
            if (btnId != 0) {
                findViewById<View>(btnId).setOnClickListener {
                    enterDigit(i.toString())
                }
            }
        }

        // Clear button
        val btnClear = findViewById<View>(R.id.btnClear)
        btnClear.setOnClickListener { deleteDigit() }
    }

    private fun enterDigit(digit: String) {
        if (enteredPin.length < 4) {
            enteredPin += digit
            updatePinDisplay()
            if (enteredPin.length == 4) {
                confirmPin()
            }
        }
    }

    private fun deleteDigit() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            updatePinDisplay()
        }
    }

    private fun updatePinDisplay() {
        // Update dot indicators
        for (i in 1..4) {
            val dotId = resources.getIdentifier("ivDot$i", "id", packageName)
            val dot = findViewById<ImageView>(dotId)
            if (i <= enteredPin.length) {
                dot.alpha = 1.0f
            } else {
                dot.alpha = 0.3f
            }
        }

        // Update title text based on mode
        val titleRes = when (mode) {
            "set" -> R.string.pin_set_title
            else -> R.string.pin_enter_title
        }
        binding.tvPinTitle.text = getString(titleRes)
    }

    private fun confirmPin() {
        if (enteredPin.length != 4) {
            showError(R.string.pin_error_short)
            return
        }

        when (mode) {
            "set" -> {
                Preferences.setPin(this, enteredPin)
                Toast.makeText(this, R.string.pin_set_success, Toast.LENGTH_SHORT).show()
                finish()
            }
            else -> {
                // Enter mode - verify PIN
                val correctPin = Preferences.getPin(this)
                if (correctPin == enteredPin) {
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    showError(R.string.pin_error_wrong)
                    enteredPin = ""
                    updatePinDisplay()
                }
            }
        }
    }

    private fun showError(resId: Int) {
        binding.tvPinError.visibility = View.VISIBLE
        binding.tvPinError.text = getString(resId)
        // Hide after 2 seconds
        binding.tvPinError.postDelayed({
            binding.tvPinError.visibility = View.GONE
        }, 2000)
    }
}
