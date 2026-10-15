package com.littlelock.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.util.Log

/**
 * Accessibility service for monitoring app usage.
 * Tracks app launches and sends Discord notifications.
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Only care about window state changed events (app launches)
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString()

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

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
    }
}
