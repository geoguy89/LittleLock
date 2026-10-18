package com.littlelock.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.littlelock.app.databinding.ActivityPinLockBinding

/**
 * The parent PIN pad.
 *  - [MODE_ENTER]: checks the PIN, then opens what [EXTRA_NEXT] names (parent settings by default). Five wrong
 *    PINs in a row lock the pad for 30 seconds.
 *  - [MODE_SET]: the parent types a new PIN twice. Used on first run and from parent settings ("Change PIN").
 */
class PinLockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_NEXT = "next"
        const val MODE_ENTER = "enter"
        const val MODE_SET = "set"
        const val NEXT_SETTINGS = "settings"
        const val NEXT_NONE = "none"

        /** Return RESULT_OK to whoever asked, without signing the parent in or opening anything. */
        const val NEXT_RESULT = "result"
    }

    private lateinit var binding: ActivityPinLockBinding
    private var enteredPin = ""
    private var mode = MODE_ENTER
    private var next = NEXT_SETTINGS
    private var firstEntry: String? = null // set mode: the PIN typed the first time

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ENTER
        next = intent.getStringExtra(EXTRA_NEXT) ?: NEXT_SETTINGS
        // changing the PIN needs the parent to be signed in already; a first-run set doesn't
        if (mode == MODE_SET && Preferences.isPinSet(this) && !ParentSession.isUnlocked()) {
            mode = MODE_ENTER
        }

        val digits = listOf(binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9)
        digits.forEachIndexed { i, button -> button.setOnClickListener { enterDigit(i.toString()) } }
        binding.btnClear.setOnClickListener { deleteDigit() }
        updatePinDisplay()
    }

    fun enterDigit(digit: String) {
        if (mode == MODE_ENTER) {
            val wait = Preferences.pinLockoutRemaining(this)
            if (wait > 0) {
                showError(getString(R.string.pin_locked_out, (wait + 999) / 1000))
                return
            }
        }
        if (enteredPin.length < PinHasher.PIN_LENGTH) {
            enteredPin += digit
            updatePinDisplay()
            if (enteredPin.length == PinHasher.PIN_LENGTH) {
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
        val dots = listOf<ImageView>(binding.ivDot1, binding.ivDot2, binding.ivDot3, binding.ivDot4)
        dots.forEachIndexed { i, dot -> dot.alpha = if (i < enteredPin.length) 1.0f else 0.3f }
        binding.tvPinTitle.setText(
            when {
                mode == MODE_SET && firstEntry != null -> R.string.pin_confirm_title
                mode == MODE_SET -> R.string.pin_set_title
                else -> R.string.pin_enter_title
            }
        )
    }

    private fun confirmPin() {
        val pin = enteredPin
        enteredPin = ""
        when (mode) {
            MODE_SET -> {
                val first = firstEntry
                if (first == null) {
                    firstEntry = pin
                } else if (first == pin) {
                    Preferences.setPin(this, pin)
                    ParentSession.unlock()
                    Toast.makeText(this, R.string.pin_set_success, Toast.LENGTH_SHORT).show()
                    done()
                    return
                } else {
                    firstEntry = null
                    showError(getString(R.string.pin_mismatch))
                }
            }
            else -> {
                if (Preferences.verifyPin(this, pin)) {
                    Preferences.resetFailedPins(this)
                    if (next == NEXT_RESULT) {
                        setResult(RESULT_OK)
                        finish()
                        return
                    }
                    ParentSession.unlock()
                    done()
                    return
                }
                Preferences.recordFailedPin(this)
                showError(getString(R.string.pin_error_wrong))
            }
        }
        updatePinDisplay()
    }

    private fun done() {
        if (next == NEXT_SETTINGS) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        finish()
    }

    private fun showError(text: String) {
        binding.tvPinError.visibility = View.VISIBLE
        binding.tvPinError.text = text
        binding.tvPinError.removeCallbacks(hideError)
        binding.tvPinError.postDelayed(hideError, 2000)
    }

    private val hideError = Runnable { binding.tvPinError.visibility = View.GONE }
}
