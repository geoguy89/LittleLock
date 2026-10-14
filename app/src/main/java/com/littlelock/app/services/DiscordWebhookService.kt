package com.littlelock.app.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.littlelock.app.data.local.SettingsRepository
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Service that sends usage reports and alerts to Discord via webhooks.
 * Sends periodic summaries and critical events (blocked apps, etc.).
 */
class DiscordWebhookService : Service(), KoinComponent {

    private val settingsRepository: SettingsRepository by inject()

    private val client = OkHttpClient()
    private val mediaType = "application/json".toMediaType()

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check if webhook URL is configured
        val webhookUrl = settingsRepository.webhookUrl
        if (webhookUrl.isBlank()) {
            Log.w("DiscordWebhookService", "No Discord webhook URL configured")
            stopSelf()
            return START_NOT_STICKY
        }

        // Send initial connection message
        sendWebhookMessage(webhookUrl, "LittleLock protection activated on device")

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Send a message to Discord webhook.
     */
    fun sendWebhookMessage(webhookUrl: String, message: String) {
        val json = """
            {
                "content": "$message",
                "username": "LittleLock",
                "avatar_url": ""
            }
        """.trimIndent()

        val requestBody = json.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(webhookUrl)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DiscordWebhookService", "Failed to send webhook", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("DiscordWebhookService", "Webhook sent successfully")
                } else {
                    Log.e("DiscordWebhookService", "Webhook response: ${response.code}")
                }
                response.close()
            }
        })
    }

    /**
     * Send a usage summary report.
     */
    fun sendUsageSummary(totalAppsUsed: Int, totalBlocked: Int, totalUsageSeconds: Int) {
        val webhookUrl = settingsRepository.webhookUrl
        if (webhookUrl.isBlank()) return

        val message = buildString {
            append("📊 Daily Usage Summary\n")
            append("Apps used: $totalAppsUsed\n")
            append("Apps blocked: $totalBlocked\n")
            append("Total usage: ${formatDuration(totalUsageSeconds)}")
        }

        sendWebhookMessage(webhookUrl, message)
    }

    /**
     * Send an alert for a blocked app attempt.
     */
    fun sendBlockedAppAlert(packageName: String, appName: String) {
        val webhookUrl = settingsRepository.webhookUrl
        if (webhookUrl.isBlank()) return

        val message = buildString {
            append("🚫 Blocked app attempt\n")
            append("App: $appName ($packageName)")
        }

        sendWebhookMessage(webhookUrl, message)
    }

    private fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) {
              "$hours h $minutes m"
        } else {
              "$minutes m"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }
}
