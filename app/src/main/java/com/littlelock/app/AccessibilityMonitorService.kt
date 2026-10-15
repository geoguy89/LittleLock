package com.littlelock.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service for monitoring app usage.
 */
class AccessibilityMonitorService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Track app launches
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            val className = event.className?.toString()

            if (packageName != null && className != null) {
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
        }
    }

    override fun onInterrupt() {
        // Handle interruption
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
    }
}
