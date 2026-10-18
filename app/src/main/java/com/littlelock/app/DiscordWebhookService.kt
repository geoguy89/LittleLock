package com.littlelock.app

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
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

    // ------------------------------------------------------------------ the optional remote: read commands, answer them

    private const val API = "https://discord.com/api/v10"
    private const val AGENT = "DiscordBot (LittleLock-parental-control, 1.5)"

    /**
     * The latest messages in the parent's private channel (newest first), read with the parent's own bot. null when it can't be
     * reached or the token / channel is wrong. Call from a background thread.
     */
    fun fetchMessages(token: String, channel: String, after: String?): List<DiscordRemote.Msg>? {
        if (token.isBlank() || channel.isBlank() || !channel.all { it.isDigit() }) return null
        val suffix = if (!after.isNullOrEmpty() && after.all { it.isDigit() }) "&after=$after" else ""
        val request = Request.Builder().url("$API/channels/$channel/messages?limit=30$suffix")
            .header("Authorization", "Bot ${token.trim()}").header("User-Agent", AGENT).build()
        return try {
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return null
                val arr = org.json.JSONArray(r.body?.string() ?: "[]")
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val a = o.optJSONObject("author")
                    DiscordRemote.Msg(
                        o.optString("id"), a?.optString("id") ?: "", a?.optBoolean("bot") ?: false, o.optString("content"),
                        runCatching { java.time.OffsetDateTime.parse(o.optString("timestamp")).toInstant().toEpochMilli() }.getOrDefault(0L)
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Posts [text] in the parent's channel as their bot. Returns whether Discord accepted it. Call from a background thread. */
    fun postBotMessage(token: String, channel: String, text: String): Boolean {
        if (token.isBlank() || channel.isBlank() || !channel.all { it.isDigit() }) return false
        val body = org.json.JSONObject().put("content", text.take(1900)).toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$API/channels/$channel/messages").post(body)
            .header("Authorization", "Bot ${token.trim()}").header("User-Agent", AGENT).build()
        return try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    /** A short plain note to the parent's channel (for example "Time's up"). */
    fun sendNote(webhookUrl: String, title: String, text: String) {
        val body = org.json.JSONObject().put("content", "**" + title + "** - " + text).toString().toRequestBody("application/json".toMediaType())
        try {
            client.newCall(Request.Builder().url(webhookUrl).post(body).build()).execute().close()
        } catch (e: Exception) {
            // the note is a courtesy
        }
    }
}
