package com.littlelock.app

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit test verifying Discord notification integration.
 * Checks that DiscordWebhookService exists and has methods to send messages,
 * uses OkHttpClient to POST to a webhook URL, sends usage data in the payload,
 * and AccessibilityMonitorService calls DiscordWebhookService on app launch.
 */
class DiscordNotificationTest {

    private val srcMain = File("src/main")

    /**
     * Verify that the parent receives a Discord notification on usage.
     */
    @Test
    fun the_parent_receives_discord_notification_on_usage() {
        val webhookService = File(srcMain, "java/com/littlelock/app/DiscordWebhookService.kt")
        assertTrue("DiscordWebhookService.kt should exist", webhookService.exists())

        val serviceContent = webhookService.readText()

        // Verify it has methods to send messages
        assertTrue(
            "DiscordWebhookService should have sendAppLaunchNotification method",
            serviceContent.contains("fun sendAppLaunchNotification")
        )
        assertTrue(
            "DiscordWebhookService should have sendDailySummary method",
            serviceContent.contains("fun sendDailySummary")
        )

        // Verify it uses OkHttpClient to POST to a webhook URL
        assertTrue(
            "DiscordWebhookService should use OkHttpClient",
            serviceContent.contains("OkHttpClient")
        )
        assertTrue(
            "DiscordWebhookService should POST to a webhook URL",
            serviceContent.contains(".url(") && serviceContent.contains(".post(")
        )

        // Verify it sends usage data (launch count, app names) in the message payload
        assertTrue(
            "DiscordWebhookService should include app name in payload",
            serviceContent.contains("appName")
        )
        assertTrue(
            "DiscordWebhookService should include packageName in payload",
            serviceContent.contains("packageName")
        )
        assertTrue(
            "DiscordWebhookService should include launch count in daily summary",
            serviceContent.contains("launchCount")
        )

        // Verify AccessibilityMonitorService calls DiscordWebhookService on app launch
        val accessibilityService = File(srcMain, "java/com/littlelock/app/AccessibilityMonitorService.kt")
        assertTrue("AccessibilityMonitorService.kt should exist", accessibilityService.exists())
        val accessibilityContent = accessibilityService.readText()
        assertTrue(
            "AccessibilityMonitorService should call DiscordWebhookService",
            accessibilityContent.contains("DiscordWebhookService")
        )
    }
}
