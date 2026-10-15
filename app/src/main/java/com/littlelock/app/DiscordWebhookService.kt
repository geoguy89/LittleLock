package com.littlelock.app

import okhttp3.*
import okio.ByteString
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility for sending notifications to Discord via webhook.
 */
object DiscordWebhookService {

    private val client = OkHttpClient()

    /**
     * Send a message about an app launch event.
     */
    fun sendAppLaunchNotification(
        context: android.content.Context,
        webhookUrl: String,
        appName: String,
        packageName: String
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val embed = """
            {
                "title": "App Launched",
                "description": "Child launched **$appName** ($packageName)",
                "color": 15258703,
                "footer": {
                    "text": "LittleLock • $timestamp"
                }
            }
        """.trimIndent()

        sendWebhook(context, webhookUrl, "📱 App Launch", embed)
    }

    /**
     * Send a daily usage summary.
     */
    fun sendDailySummary(
        context: android.content.Context,
        webhookUrl: String,
        launchCount: Int
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val embed = """
            {
                "title": "Daily Usage Summary",
                "description": "Apps launched today: **$launchCount**",
                "color": 3066993,
                "footer": {
                    "text": "LittleLock • $timestamp"
                }
            }
        """.trimIndent()

        sendWebhook(context, webhookUrl, "📊 Daily Summary", embed)
    }

    /**
     * Send a blocked app attempt notification.
     */
    fun sendBlockedAppNotification(
        context: android.content.Context,
        webhookUrl: String,
        appName: String,
        packageName: String
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val embed = """
            {
                "title": "Blocked App Attempt",
                "description": "Attempted to launch **$appName** ($packageName)",
                "color": 15548997,
                "footer": {
                    "text": "LittleLock • $timestamp"
                }
            }
        """.trimIndent()

        sendWebhook(context, webhookUrl, "🚫 Blocked App", embed)
    }

    private fun sendWebhook(
        context: android.content.Context,
        webhookUrl: String,
        username: String,
        embedJson: String
    ) {
        val bodyJson = """
            {
                "username": "$username",
                "embeds": [$embedJson]
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(webhookUrl)
            .post(RequestBody.create(MediaType.parse("application/json"), bodyJson))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Silently fail - don't crash the app
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }
}
