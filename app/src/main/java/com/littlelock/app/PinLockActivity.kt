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
 *  - With [EXTRA_PROFILE]: the same two modes for one child's own PIN - [MODE_ENTER] signs that child in, [MODE_SET] (parent
 *    only) chooses it. A child's PIN is never the parent's PIN, and never another child's.
 */
class PinLockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_NEXT = "next"

        /** The id of the child profile whose PIN this is (see [Profiles]); without it the pad is the parent's. */
        const val EXTRA_PROFILE = "profile"
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
    private var profile: Profile? = null       // a child's PIN pad

    override fun onCreate(savedInstanceState: Bundle?) {
        Accent.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPinLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ENTER
        next = intent.getStringExtra(EXTRA_NEXT) ?: NEXT_SETTINGS
        profile = intent.getStringExtra(EXTRA_PROFILE)?.let { Profiles.get(this, it) }
        if (intent.hasExtra(EXTRA_PROFILE) && profile == null) {
            finish()
            return
        }
        // changing the PIN needs the parent to be signed in already; a first-run set doesn't
        if (mode == MODE_SET && (profile != null || Preferences.isPinSet(this)) && !ParentSession.isUnlocked()) {
            if (profile != null) {
                finish()
                return
            }
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
        val child = profile
        if (child != null) {
            val who = child.name.ifEmpty { getString(R.string.profile_unnamed) }
            binding.tvPinTitle.text = when {
                mode == MODE_SET && firstEntry != null -> getString(R.string.pin_confirm_title)
                mode == MODE_SET -> getString(R.string.pin_child_set_title, who)
                else -> getString(R.string.pin_child_enter_title, who)
            }
            return
        }
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
                    val child = profile
                    if (child != null) {
                        val check = Profiles.setPin(this, child.id, pin)
                        if (check == Profiles.PinCheck.OK) {
                            Toast.makeText(this, R.string.pin_set_success, Toast.LENGTH_SHORT).show()
                            finish()
                            return
                        }
                        firstEntry = null
                        showError(getString(if (check == Profiles.PinCheck.SAME_AS_PARENT) R.string.pin_same_as_parent else R.string.pin_same_as_child))
                    } else if (Profiles.isChildPin(this, pin)) {
                        firstEntry = null
                        showError(getString(R.string.pin_same_as_child_parent))
                    } else {
                        Preferences.setPin(this, pin)
                        ParentSession.unlock()
                        Toast.makeText(this, R.string.pin_set_success, Toast.LENGTH_SHORT).show()
                        done()
                        return
                    }
                } else {
                    firstEntry = null
                    showError(getString(R.string.pin_mismatch))
                }
            }
            else -> {
                val child = profile
                if (child != null) {
                    if (Profiles.verifyPin(this, child.id, pin)) {
                        Preferences.resetFailedPins(this)
                        Profiles.signIn(this, child.id)
                        setResult(RESULT_OK)
                        finish()
                        return
                    }
                } else if (Preferences.verifyPin(this, pin)) {
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
                PinAlert.send(this, Preferences.recordFailedPin(this))
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
