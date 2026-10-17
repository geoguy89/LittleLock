package com.littlelock.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Only care about window state changed events (app launches)
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString()

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
    }
}
