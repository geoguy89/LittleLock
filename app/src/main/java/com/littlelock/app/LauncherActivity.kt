package com.littlelock.app

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.littlelock.app.databinding.ActivityLauncherBinding

/**
 * The child's home screen: a grid of the apps the parent allowed, nothing else - with the child's name and the
 * parent's choice of tile size and wallpaper.
 * The lock button at the top opens parent settings behind the parent PIN (first run: the parent sets the PIN).
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLauncherBinding
    private var visibleApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        Accent.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        binding.btnSettings.setOnClickListener { openParentArea() }

        // this is the home screen: Back has nowhere to go
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        if (savedInstanceState == null) {
            DailySummaryScheduler.scheduleDailySummary(this)
            if (!Preferences.isPinSet(this)) openParentArea()
        }
    }

    override fun onResume() {
        super.onResume()
        // coming back to the home screen ends the parent's unlocked window: the lock is back without a manual relock
        ParentSession.lock()
        if (Preferences.isKidMode(this) && !ParentSession.isUnlocked() && Preferences.isTimeLocked(this)) {
            // time is up: Home leads to the time-up page, not to the apps
            startActivity(Intent(this, TimeUpActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            return
        }
        loadApps()
        syncKiosk()
    }

    /** With the device-owner lockdown set up: on while kid mode is on and the parent is out, lifted otherwise. */
    private fun syncKiosk() {
        if (!Kiosk.isOwner(this)) return
        if (Kiosk.wanted(Preferences.isKioskOn(this), true, Preferences.isKidMode(this), ParentSession.isUnlocked())) {
            Kiosk.engage(this)
        } else {
            Kiosk.disengage(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        loadApps()
    }

    /** The apps the child may see: the parent's allowed list in kid mode, every app when kid mode is off. */
    fun loadApps() {
        val all = AppFilter.launchableApps(packageManager)
        visibleApps = if (Preferences.isKidMode(this)) {
            AppFilter.visibleApps(all, Preferences.getAllowedApps(this), packageName)
        } else {
            AppFilter.choosableApps(all, packageName)
        }
        val lightText = applyLook()
        binding.gridApps.tile = Look.tile(Preferences.getTileSize(this))
        binding.gridApps.mode = Preferences.getLayoutMode(this)
        binding.gridApps.align = Preferences.getLayoutAlign(this)
        binding.gridApps.gapDp = Layouts.gapDp(Preferences.getLayoutGap(this))
        binding.gridApps.setApps(visibleApps, lightText) { launch(it) }
        binding.tvEmpty.visibility = if (visibleApps.isEmpty()) View.VISIBLE else View.GONE
        binding.tvEmpty.setText(
            if (Preferences.isPinSet(this)) R.string.launcher_empty else R.string.launcher_setup
        )
        binding.tvEmpty.setTextColor(
            if (lightText) getColor(R.color.white) else getColor(R.color.littlelock_text_secondary)
        )
        binding.tvKidModeOff.visibility = if (Preferences.isKidMode(this)) View.GONE else View.VISIBLE
    }

    /**
     * The child's name in the title and the parent's wallpaper behind the grid.
     * Returns true when the app names should be drawn in white (a dark colour or a picture behind them).
     */
    private fun applyLook(): Boolean {
        binding.tvAppName.text = Look.greeting(Preferences.getKidName(this), getString(R.string.launcher_title))
        val wallpaper = Preferences.getWallpaper(this)
        val color = Look.colorOf(wallpaper)
        if (color != null) {
            binding.root.background = ColorDrawable(color)
            return Look.isDark(color)
        }
        val dm = resources.displayMetrics
        val bmp = Wallpapers.forValue(this, wallpaper, dm.widthPixels, dm.heightPixels)
        if (bmp != null) {
            binding.root.background = BitmapDrawable(resources, bmp).apply { gravity = Gravity.FILL }
            return Wallpapers.isDark(bmp)
        }
        binding.root.setBackgroundColor(getColor(R.color.littlelock_background))
        return false
    }

    private fun launch(app: AppInfo) {
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            Toast.makeText(this, getString(R.string.launcher_cant_open, app.label), Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun openParentArea() {
        val intent = Intent(this, PinLockActivity::class.java)
            .putExtra(PinLockActivity.EXTRA_MODE, if (Preferences.isPinSet(this)) PinLockActivity.MODE_ENTER else PinLockActivity.MODE_SET)
            .putExtra(PinLockActivity.EXTRA_NEXT, PinLockActivity.NEXT_SETTINGS)
        startActivity(intent)
    }

    companion object {
        /** The parent's chosen picture, scaled down, inside the app's private files. */
        const val WALLPAPER_FILE = "wallpaper.jpg"
    }
}
