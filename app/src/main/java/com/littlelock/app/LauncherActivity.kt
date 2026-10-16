package com.littlelock.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlin.math.abs
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.GridView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.littlelock.app.databinding.ActivityLauncherBinding

/**
 * Main launcher activity - acts as the home screen for children.
 * Shows a grid of installed apps with a child-friendly UI.
 * 4-finger swipe up opens settings (PIN protected).
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLauncherBinding
    private lateinit var appAdapter: AppAdapter
    private lateinit var gestureDetector: GestureDetector
    private var installedApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGridView()
        setupGestureDetector()
        loadApps()
    }

    private fun setupGridView() {
        appAdapter = AppAdapter(this, installedApps)
        binding.gridApps.adapter = appAdapter
        binding.gridApps.onItemClickListener = null // we handle clicks manually
        binding.gridApps.setOnItemClickListener { parent, view, position, id ->
            val app = parent.getItemAtPosition(position) as AppInfo
            val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            }
        }
        binding.gridApps.setOnTouchListener { _, _ -> true }
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffY = e1.y - e2.y
                val diffX = e1.x - e2.x
                // 4-finger swipe up: 4 fingers, moving upward
                if (e1.pointerCount == 4 && diffY > 100 && abs(diffX) < 100) {
                    openSettings()
                    return true
                }
                return false
            }
        })
        binding.gridApps.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun loadApps() {
        val pm: PackageManager = packageManager
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val allApps = pm.queryIntentActivities(intent, 0)
            .map { resolveInfo ->
                val label = resolveInfo.loadLabel(pm).toString()
                val packageName = resolveInfo.activityInfo.packageName
                  val iconResId = getAppIconResId(packageName)
                AppInfo(label, packageName, iconResId)
            }
            .sortedBy { it.label.lowercase() }

        installedApps = allApps
        appAdapter = AppAdapter(this, installedApps)
        binding.gridApps.adapter = appAdapter
    }

    private fun getAppIconResId(packageName: String): Int {
        try {
            val pm: PackageManager = packageManager
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.MATCH_ALL)
            pm.getApplicationIcon(appInfo)
            return appInfo.icon
        } catch (e: Exception) {
            return R.drawable.ic_app_icon_1
        }
    }

    private fun openSettings() {
        if (Preferences.isPinSet(this)) {
            val intent = Intent(this, PinLockActivity::class.java)
            startActivity(intent)
        } else {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            gestureDetector.onTouchEvent(event)
        }
        return super.onTouchEvent(event)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Re-launch as home: reload apps
        loadApps()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // No-op: setTaskToHome() caused activity restart loops
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // No-op: setTaskToHome() caused activity restart loops
    }

    private fun setTaskToHome() {
        // Bring our task to front and ensure we act as home launcher
        val intent = Intent(this, javaClass)
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_TASK_ON_HOME or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }
}
