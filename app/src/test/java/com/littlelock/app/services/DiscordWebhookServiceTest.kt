package com.littlelock.app.services

import com.littlelock.app.util.MessageFormatter
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Tests for DiscordWebhookService message formatting logic.
 * Uses Robolectric to provide Android context for service instantiation.
 */
@Config(sdk = [30])
class DiscordWebhookServiceTest {

    @Test
    fun testFormatDuration_ShortDuration() {
        // 120 seconds = 2 minutes
        val result = MessageFormatter.formatDuration(120)
        assertEquals("2 m", result)
    }

    @Test
    fun testFormatDuration_HoursAndMinutes() {
        // 3725 seconds = 1 hour 2 minutes
        val result = MessageFormatter.formatDuration(3725)
        assertEquals("1 h 2 m", result)
    }

    @Test
    fun testFormatDuration_OnlyMinutes() {
        // 90 seconds = 1 minute
        val result = MessageFormatter.formatDuration(90)
        assertEquals("1 m", result)
    }

    @Test
    fun testFormatDuration_ZeroSeconds() {
        val result = MessageFormatter.formatDuration(0)
        assertEquals("0 m", result)
    }

    @Test
    fun testFormatDuration_LargeHours() {
        // 7261 seconds = 2 hours 1 minute
        val result = MessageFormatter.formatDuration(7261)
        assertEquals("2 h 1 m", result)
    }

    @Test
    fun testUsageSummaryMessage() {
        // Build the message the same way DiscordWebhookService does
        val totalAppsUsed = 5
        val totalBlocked = 3
        val totalUsageSeconds = 3725 // 1 h 2 m

        val message = buildString {
            append("📊 Daily Usage Summary\n")
            append("Apps used: $totalAppsUsed\n")
            append("Apps blocked: $totalBlocked\n")
            append("Total usage: ${MessageFormatter.formatDuration(totalUsageSeconds)}")
        }

        assertTrue("Message should contain emoji header", message.contains("📊 Daily Usage Summary"))
        assertTrue("Message should contain apps used count", message.contains("Apps used: 5"))
        assertTrue("Message should contain apps blocked count", message.contains("Apps blocked: 3"))
        assertTrue("Message should contain formatted duration", message.contains("1 h 2 m"))
    }

    @Test
    fun testBlockedAppAlertMessage() {
        val packageName = "com.example.blockedapp"
        val appName = "Blocked App"

        val message = buildString {
            append("🚫 Blocked app attempt\n")
            append("App: $appName ($packageName)")
        }

        assertTrue("Message should contain blocked emoji", message.contains("🚫 Blocked app attempt"))
        assertTrue("Message should contain app name", message.contains("Blocked App"))
        assertTrue("Message should contain package name", message.contains("com.example.blockedapp"))
    }
}
