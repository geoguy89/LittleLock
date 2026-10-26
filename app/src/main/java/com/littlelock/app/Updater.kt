package com.littlelock.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** A release on GitHub: [tag] looks like "v1.7.0"; [apkUrl] is the APK attached to it. */
data class Release(val tag: String, val version: String, val apkUrl: String, val sizeBytes: Long, val pageUrl: String, val notes: String)

/**
 * Reading GitHub's "latest release" answer, and deciding whether it is newer than the installed app. Pure text and numbers,
 * so it can be tested without a tablet.
 */
object ReleaseFeed {
    const val LATEST_URL = "https://api.github.com/repos/geoguy89/LittleLock/releases/latest"

    /** The release in [body], or null when it is a draft / pre-release or has no APK. */
    fun parse(body: String): Release? {
        val o = try {
            org.json.JSONObject(body)
        } catch (e: Exception) {
            return null
        }
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val tag = o.optString("tag_name")
        if (tag.isBlank() || numbers(tag).isEmpty()) return null
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                return Release(tag, tag.removePrefix("v").removePrefix("V"), a.optString("browser_download_url"),
                    a.optLong("size"), o.optString("html_url"), o.optString("body").trim())
            }
        }
        return null
    }

    private fun numbers(version: String): List<Int> =
        Regex("""\d+""").findAll(version.substringBefore('-')).mapNotNull { it.value.toIntOrNull() }.toList()

    /** Is [candidate] ("v1.7.0") a later version than [installed] ("1.6.6")? Compared number by number. */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = numbers(candidate)
        val b = numbers(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

/**
 * Self-update from GitHub Releases: find the latest release, download its APK, and install it through a
 * PackageInstaller session. Android only accepts the update when it is signed with the same key as the installed app,
 * and asks the parent to confirm unless it is allowed to update without asking (Android 12+, or when LittleLock is the device owner).
 */
class AndroidUpdater(private val context: Context) {

    val installedVersion: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "LittleLock-Android")
    }

    /** [done] gets the newest release, or null when it can't be reached; [failure] says why in plain words. Runs on a background thread. */
    fun latest(done: (Release?, String?) -> Unit) {
        thread(isDaemon = true, name = "littlelock-update-check") {
            try {
                val c = open(ReleaseFeed.LATEST_URL)
                c.setRequestProperty("Accept", "application/vnd.github+json")
                when (c.responseCode) {
                    200 -> {
                        val r = ReleaseFeed.parse(c.inputStream.bufferedReader().use { it.readText() })
                        if (r == null) done(null, "The newest release has no app file attached.") else done(r, null)
                    }
                    404 -> done(null, "No release has been published yet.")
                    403, 429 -> done(null, "GitHub asks for a short wait - try again in a few minutes.")
                    else -> done(null, "GitHub answered with error ${c.responseCode}.")
                }
            } catch (e: Exception) {
                done(null, "Couldn't reach GitHub: ${e.message ?: "no internet?"}")
            }
        }
    }

    /** Downloads and installs [release]. [progress] is 0..1; [done] gets null on success (Android takes over from there) or why not. */
    fun install(release: Release, progress: (Float) -> Unit, done: (String?) -> Unit) {
        thread(isDaemon = true, name = "littlelock-update") {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val apk = File(dir, "littlelock-${release.version}.apk")
                val c = open(release.apkUrl)
                if (c.responseCode != 200) throw IllegalStateException("The download failed (${c.responseCode}).")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
                c.inputStream.use { input ->
                    apk.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) progress((read.toFloat() / total).coerceAtMost(1f))
                        }
                    }
                }
                if (total > 0 && apk.length() != total) throw IllegalStateException("The download was cut short - try again.")
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("littlelock.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; session.fsync(out) }
                    val intent = Intent(context, InstallStatusReceiver::class.java).setAction(InstallStatusReceiver.ACTION).setPackage(context.packageName)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
                }
                done(null)
            } catch (e: Exception) {
                Log.w("AndroidUpdater", "Update failed", e)
                Preferences.setUpdateError(context, e.message ?: "unknown")
                done(e.message ?: "The update couldn't be installed.")
            }
        }
    }
}

/** Receives the installer's answer; when Android needs the parent to confirm, it opens that screen. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                ParentSession.unlock()                          // Android's confirm screen is another app: the blocker must let it be
                try {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Preferences.setUpdateError(context, "Android's install screen couldn't open (${e.message})")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // the installer restarts LittleLock; the service brings the home screen back
            else -> {
                val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown reason"
                Log.w("AndroidUpdater", "Install status: $why")
                Preferences.setUpdateError(context, why)
                Toast.makeText(context, "The update didn't install: $why", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val ACTION = "com.littlelock.app.INSTALL_STATUS"
    }
}
