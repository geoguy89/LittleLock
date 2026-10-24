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
     * Send a daily usage summary: which apps were used on [dayName], for how long and how often. [onResult] says whether Discord
     * took it, so a summary that couldn't be delivered (no internet) can be sent again later.
     */
    fun sendDailySummary(
        context: android.content.Context,
        webhookUrl: String,
        dayName: String,
        launchCount: Int,
        headline: String,
        itemised: String,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val body = if (itemised.isEmpty()) headline else "$headline\n\n$itemised"
        val embed = org.json.JSONObject()
            .put("title", "Daily Usage Summary - $dayName")
            .put("description", body.take(4000))
            .put("color", 3066993)
            .put("footer", org.json.JSONObject().put("text", "LittleLock • $timestamp • $launchCount opens"))
            .toString()

        sendWebhook(context, webhookUrl, "📊 Daily Summary", embed, onResult)
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
        embedJson: String,
        onResult: ((Boolean) -> Unit)? = null
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
                onResult?.invoke(false)
            }

            override fun onResponse(call: Call, response: Response) {
                val ok = response.isSuccessful
                response.close()
                onResult?.invoke(ok)
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
        val ch = channel.trim()
        if (token.isBlank() || ch.isEmpty() || !ch.all { it.isDigit() }) return null
        val suffix = if (!after.isNullOrEmpty() && after.all { it.isDigit() }) "&after=$after" else ""
        val request = Request.Builder().url("$API/channels/$ch/messages?limit=30$suffix")
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
                        runCatching { java.time.OffsetDateTime.parse(o.optString("timestamp")).toInstant().toEpochMilli() }.getOrDefault(0L),
                        listOfNotNull(a?.optString("username"), a?.optString("global_name")).filter { it.isNotBlank() && it != "null" }.map { it.lowercase() }.toSet()
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Posts [text] in the parent's channel as their bot. Returns whether Discord accepted it. Call from a background thread. */
    fun postBotMessage(token: String, channel: String, text: String): Boolean = postBotMessageResult(token, channel, text) == null

    /** What a Discord answer means for the parent, in plain words. */
    fun explain(code: Int): String = when (code) {
        401 -> "Discord says the bot token is wrong (401). Copy it again from the bot page (Reset Token gives a fresh one)."
        403 -> "The bot can't use that channel (403). Invite it to your server and give it View Channel, Send Messages and Read Message History there."
        404 -> "There is no channel with that id (404). In Discord turn on Developer Mode, right-click the channel and choose Copy Channel ID (not the server id)."
        429 -> "Discord asks for a short wait (429) - try again in a minute."
        else -> "Discord answered with error $code."
    }

    /** Posts [text] as the bot; null when it worked, otherwise why not (in plain words). */
    fun postBotMessageResult(token: String, channel: String, text: String): String? {
        val ch = channel.trim()
        if (token.isBlank()) return "The bot token is empty."
        if (ch.isEmpty() || !ch.all { it.isDigit() }) return "The channel id must be only numbers (Copy Channel ID in Discord)."
        val body = org.json.JSONObject().put("content", text.take(1900)).toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$API/channels/$ch/messages").post(body)
            .header("Authorization", "Bot ${token.trim()}").header("User-Agent", AGENT).build()
        return try {
            client.newCall(request).execute().use { if (it.isSuccessful) null else explain(it.code) }
        } catch (e: Exception) {
            "Couldn't reach Discord: ${e.message ?: "no internet?"}"
        }
    }

    /** Can the bot read the channel? null when it can, otherwise why not. */
    fun readResult(token: String, channel: String): String? {
        val ch = channel.trim()
        val request = Request.Builder().url("$API/channels/$ch/messages?limit=1")
            .header("Authorization", "Bot ${token.trim()}").header("User-Agent", AGENT).build()
        return try {
            client.newCall(request).execute().use { if (it.isSuccessful) null else explain(it.code) }
        } catch (e: Exception) {
            "Couldn't reach Discord: ${e.message ?: "no internet?"}"
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
