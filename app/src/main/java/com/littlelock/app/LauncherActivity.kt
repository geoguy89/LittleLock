package com.littlelock.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.littlelock.app.databinding.ActivityLauncherBinding
import java.io.File

/**
 * The child's home screen: a grid of the apps the parent allowed, nothing else - with the child's name and the
 * parent's choice of tile size and wallpaper.
 * The lock button at the top opens parent settings behind the parent PIN (first run: the parent sets the PIN).
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLauncherBinding
    private var visibleApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.gridApps.emptyView = binding.tvEmpty
        binding.gridApps.setOnItemClickListener { _, _, position, _ ->
            visibleApps.getOrNull(position)?.let { launch(it) }
        }
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
        loadApps()
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
        val tile = Look.tile(Preferences.getTileSize(this))
        binding.gridApps.columnWidth = (tile.cellDp * resources.displayMetrics.density).toInt()
        binding.gridApps.adapter = AppAdapter(this, visibleApps, lightText = lightText)
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
        if (wallpaper == Look.IMAGE) {
            val file = File(filesDir, WALLPAPER_FILE)
            val bmp = if (file.isFile) BitmapFactory.decodeFile(file.path) else null
            if (bmp != null) {
                binding.root.background = BitmapDrawable(resources, bmp).apply { gravity = Gravity.FILL }
                return true
            }
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
