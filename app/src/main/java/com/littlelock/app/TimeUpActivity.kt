package com.littlelock.app

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.littlelock.app.databinding.ActivityTimeUpBinding

/**
 * "Time's up": a colourful, full-screen, opaque page that fills the tablet when the daily limit is used up (or the parent locked
 * it). Nothing on it can open an app. The only way on is the parent button: the PIN, then the clock time the tablet should lock
 * again - until then it is open, and at that time this page comes back.
 */
class TimeUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimeUpBinding

    private val pin = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) askHowLong()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Accent.apply(this)
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        binding = ActivityTimeUpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        paint()
        binding.btnParent.setOnClickListener {
            pin.launch(
                Intent(this, PinLockActivity::class.java)
                    .putExtra(PinLockActivity.EXTRA_MODE, PinLockActivity.MODE_ENTER)
                    .putExtra(PinLockActivity.EXTRA_NEXT, PinLockActivity.NEXT_RESULT)
            )
        }
        // nothing here is a way out: Back does nothing
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
    }

    private val recheck = object : Runnable {
        override fun run() {
            val st = TimeLimit.state(Preferences.timeSettings(this@TimeUpActivity), System.currentTimeMillis())
            if (!st.locked || !Preferences.isKidMode(this@TimeUpActivity)) leave() else binding.root.postDelayed(this, 3000)
        }
    }

    /** Waking the tablet re-reads the clock at once, so a lock that ended while it slept never greets the child. */
    private val wake = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val st = TimeLimit.state(Preferences.timeSettings(this@TimeUpActivity), System.currentTimeMillis())
            if (!st.locked || !Preferences.isKidMode(this@TimeUpActivity)) leave()
        }
    }

    override fun onStart() {
        super.onStart()
        val f = android.content.IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_USER_PRESENT) }
        androidx.core.content.ContextCompat.registerReceiver(this, wake, f, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        try { unregisterReceiver(wake) } catch (_: IllegalArgumentException) {}
        super.onStop()
    }

    override fun onPause() {
        binding.root.removeCallbacks(recheck)
        super.onPause()
    }

    override fun onUserInteraction() {
        Idle.touch()
    }

    override fun onResume() {
        super.onResume()
        Idle.touch()
        binding.root.postDelayed(recheck, 3000)               // extended from afar (Discord): carry on to the apps
        // the parent extended it (or it was unlocked from afar): back to the apps
        val st = TimeLimit.state(Preferences.timeSettings(this), System.currentTimeMillis())
        if (!st.locked || !Preferences.isKidMode(this)) leave()
    }

    private fun paint() {
        val st = TimeLimit.state(Preferences.timeSettings(this), System.currentTimeMillis())
        if (st.why == TimeLimit.Why.BEDTIME) {
            binding.tvTimeUpTitle.setText(R.string.bed_title)
            binding.tvTimeUpMessage.text = getString(R.string.bed_message, TimeLimit.clockText(st.bedEndsAt))
        }
        val dm = resources.displayMetrics
        val bmp = Wallpapers.render(this, "owl", dm.widthPixels, dm.heightPixels)
        binding.root.background = bmp?.let { BitmapDrawable(resources, it).apply { gravity = Gravity.FILL } }
            ?: GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF2B2F4A.toInt(), 0xFF1E1E24.toInt()))
    }

    /** After the PIN the parent picks how much longer the tablet stays open (or the clock time it locks again). */
    private fun askHowLong() {
        ExtendDialog.show(this) { until ->
            Preferences.saveTimeSettings(this, Preferences.timeSettings(this).copy(extendedUntil = until))
            Toast.makeText(this, getString(R.string.time_extended, TimeLimit.clockText(until)), Toast.LENGTH_LONG).show()
            leave()
        }
    }

    private fun leave() {
        startActivity(Intent(this, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
