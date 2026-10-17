package com.littlelock.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.util.Log

/**
 * Accessibility service that keeps kid mode in force and reports usage.
 *  - In kid mode, an app the parent didn't allow that comes to the front is sent straight back to the LittleLock
 *    home screen (see [Enforcer]); keyboards and the system UI are never blocked, and nothing is blocked while the
 *    parent is signed in.
 *  - Counts app launches and sends the optional Discord notifications.
 */
class AccessibilityMonitorService : AccessibilityService() {

    private companion object {
        private const val TAG = "AccessibilityMonitor"
        // System packages to filter out from notifications
        private val SYSTEM_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.launcher",
            "com.android.settings",
            "com.google.android.gms",
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.apps.turbo",
            "com.google.android.setupwizard"
        )

        private fun isSystemPackage(packageName: String): Boolean {
            return packageName.startsWith("android.") ||
                packageName.startsWith("com.google.") ||
                packageName.startsWith("com.android.") ||
                SYSTEM_PACKAGES.contains(packageName)
        }
    }

    private var lastPackageName: String? = null
    private var lastBlocked: String? = null
    private var lastBlockedAt = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var shield: View? = null
    private val shieldTick = object : Runnable {
        override fun run() {
            updateShield()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Only care about window state changed events (app launches)
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString()

        // the notification shade / quick settings opened anyway: close it again
        if (ShadeGuard.isShade(packageName, className) && shieldWanted()) {
            closeShade()
            return
        }

        if (Enforcer.shouldSendHome(packageName, this.packageName, Preferences.getAllowedApps(this),
                keyboardPackages(), Preferences.isKidMode(this), ParentSession.isUnlocked())) {
            sendHome(packageName, event)
            return
        }

        // Only trigger on activity resume (actual app launch, not just any state change)
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.source == null) return

        // Filter out system packages
        if (isSystemPackage(packageName)) {
            Log.d(TAG, "Filtering system package: $packageName")
            return
        }

        // Only trigger when a NEW app package is detected (not the same one repeatedly)
        if (packageName == lastPackageName) return

        Log.d(TAG, "New app launch detected: $packageName ($className)")
        lastPackageName = packageName

        // Record app usage
        Preferences.incrementDailyLaunchCount(this)

        // Send Discord notification if enabled
        if (Preferences.isAppLaunchNotifEnabled(this)) {
            val webhookUrl = Preferences.getWebhookUrl(this)
            if (!webhookUrl.isNullOrEmpty()) {
                val appName = event.text?.firstOrNull()?.toString() ?: packageName
                DiscordWebhookService.sendAppLaunchNotification(
                    this,
                    webhookUrl,
                    appName,
                    packageName
                )
            }
        }
    }

    /** Back to the LittleLock home screen, and (at most once a minute per app) tell the parent. */
    private fun sendHome(packageName: String, event: AccessibilityEvent) {
        Log.i(TAG, "Not allowed in kid mode: $packageName - back to the home screen")
        startActivity(Intent(this, LauncherActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        val now = SystemClock.elapsedRealtime()
        if (packageName == lastBlocked && now - lastBlockedAt < 60_000) return
        lastBlocked = packageName
        lastBlockedAt = now
        val webhookUrl = Preferences.getWebhookUrl(this)
        if (Preferences.isBlockedAppsNotifEnabled(this) && !webhookUrl.isNullOrEmpty()) {
            val appName = event.text?.firstOrNull()?.toString() ?: packageName
            DiscordWebhookService.sendBlockedAppNotification(this, webhookUrl, appName, packageName)
        }
    }

    /** Keyboards must keep working inside allowed apps. */
    private fun keyboardPackages(): Set<String> = try {
        getSystemService(InputMethodManager::class.java)?.enabledInputMethodList
            ?.map { it.packageName }?.toSet() ?: emptySet()
    } catch (e: Exception) {
        emptySet()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        handler.removeCallbacks(shieldTick)
        handler.post(shieldTick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(shieldTick)
        removeShield()
        super.onDestroy()
    }

    private fun shieldWanted(): Boolean = ShadeGuard.shieldWanted(
        Preferences.isKidMode(this), ParentSession.isUnlocked(), Preferences.isBlockShade(this))

    private fun closeShade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    /** Puts the shield over the status bar while it is wanted, and takes it away when it isn't. */
    private fun updateShield() {
        if (shieldWanted()) addShield() else removeShield()
    }

    /**
     * A transparent strip over the status bar that takes the touch, so pulling the shade down does nothing. It is an
     * accessibility overlay, which this service may draw without any extra permission.
     */
    private fun addShield() {
        if (shield != null) return
        try {
            val wm = getSystemService(WindowManager::class.java) ?: return
            val barId = resources.getIdentifier("status_bar_height", "dimen", "android")
            val bar = if (barId > 0) resources.getDimensionPixelSize(barId) else (28 * resources.displayMetrics.density).toInt()
            val view = View(this).apply { setOnTouchListener { _, _ -> true } }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                ShadeGuard.shieldHeightPx(bar, resources.displayMetrics.density),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP }
            wm.addView(view, params)
            shield = view
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't put up the status-bar shield: ${e.message}")
        }
    }

    private fun removeShield() {
        val view = shield ?: return
        shield = null
        try {
            getSystemService(WindowManager::class.java)?.removeView(view)
        } catch (e: Exception) {
            // already gone
        }
    }
}
