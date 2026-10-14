package com.littlelock.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.littlelock.app.R
import com.littlelock.app.data.local.AllowedAppDao
import com.littlelock.app.data.local.SettingsRepository
import com.littlelock.app.ui.launcher.KidsLauncherActivity
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

/**
 * Foreground service that maintains the launcher overlay.
 * Uses UsageStatsManager for reliable foreground app detection
 * and blocks unauthorized apps by bringing the KidsLauncher to front.
 */
class LauncherService : Service(), KoinComponent {

    private val settingsRepository: SettingsRepository by inject()
    private val allowedAppDao: AllowedAppDao by inject()

    private val CHANNEL_ID = "launcher_channel"
    private val NOTIFICATION_ID = 1

    private var monitorTimer: Timer? = null
    private var lastPackageName: String? = null
    private var blockCount: Int = 0
    private var lastSummaryDate: Long = 0

    // Usage tracking fields
    private var totalUsageSeconds: Int = 0
    private var uniqueAppsUsed: Int = 0
    private val appUsageMap = mutableMapOf<String, Long>() // packageName -> seconds
    private var lastAppTimestamp: Long = 0
    private var lastTrackedApp: String? = null
    private var lastDayOfMonth: Int = 0

    private val discordService by inject<DiscordWebhookService>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        lastSummaryDate = System.currentTimeMillis()
        lastDayOfMonth = java.util.Date().day
        startAppMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "LittleLock Protection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "LittleLock is actively protecting this device"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, KidsLauncherActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LittleLock Active")
            .setContentText("Child launcher is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun startAppMonitoring() {
        monitorTimer = Timer("launcher-monitor", true)
        monitorTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                checkCurrentApp()
                checkDailySummary()
            }
        }, 0, 1000) // Check every second
    }

    private fun checkCurrentApp() {
        val currentApp = getCurrentPackageName()
        if (currentApp == null || currentApp == packageName) {
            return
        }

        // Usage tracking: record duration for previous app when app changes
        val now = System.currentTimeMillis()
        if (lastAppTimestamp > 0 && lastTrackedApp != null) {
            val elapsed = ((now - lastAppTimestamp) / 1000).toInt()
            if (elapsed > 0 && lastTrackedApp != currentApp) {
                // Reset counters if day changed
                val currentDay = java.util.Date().day
                if (currentDay != lastDayOfMonth) {
                    totalUsageSeconds = 0
                    uniqueAppsUsed = 0
                    appUsageMap.clear()
                    lastDayOfMonth = currentDay
                }

                // Record duration for the previous app
                val prevApp = lastTrackedApp!!
                appUsageMap[prevApp] = appUsageMap.getOrDefault(prevApp, 0L) + elapsed
                totalUsageSeconds += elapsed
                if (!appUsageMap.containsKey(prevApp) || appUsageMap[prevApp]!! == elapsed.toLong()) {
                    // First time tracking this app today
                    uniqueAppsUsed++
                }
            }
        }

        // Update tracking state
        lastTrackedApp = currentApp
        lastAppTimestamp = now

        if (currentApp != lastPackageName) {
            lastPackageName = currentApp
            if (!isAppAllowed(currentApp)) {
                blockApp(currentApp)
            }
        }
    }

    /**
     * Get the current foreground app package name using UsageStatsManager.
     * Falls back to null if permission is not granted.
     */
    private fun getCurrentPackageName(): String? {
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.SECONDS.toMillis(2)

        val events: UsageEvents = usageStatsManager.queryEvents(startTime, endTime)
        if (events == null) return null

        var lastEventTime = 0L
        var lastEventPackage: String? = null

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                && event.timeStamp > lastEventTime) {
                lastEventTime = event.timeStamp
                lastEventPackage = event.packageName
            }
        }

        return lastEventPackage
    }

    private fun isAppAllowed(packageName: String): Boolean {
        return kotlinx.coroutines.runBlocking {
            val allowedApps = allowedAppDao.getAllowedApps()
            allowedApps.any { it.packageName == packageName }
        }
    }

    private fun blockApp(packageName: String) {
        blockCount++

        // Get app name for Discord alert
        val appName = try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }

        // Bring launcher to front with required flags
        try {
            val intent = Intent(this, KidsLauncherActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("LauncherService", "Failed to bring launcher to front", e)
        }

        // Kill the blocked app's process
        try {
            val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            activityManager.killBackgroundProcesses(packageName)
            Log.d("LauncherService", "Killed background process: $packageName")
        } catch (e: Exception) {
            Log.e("LauncherService", "Failed to kill process: $packageName", e)
        }

        // Send Discord webhook alert
        try {
            discordService.sendBlockedAppAlert(packageName, appName)
        } catch (e: Exception) {
            Log.e("LauncherService", "Failed to send Discord alert", e)
        }

        Log.w("LauncherService", "Blocked app: $appName ($packageName) (count: $blockCount)")
    }

    /**
     * Check if a daily summary should be sent (once per day).
     */
    private fun checkDailySummary() {
        val now = System.currentTimeMillis()
        val lastDate = java.util.Date(lastSummaryDate)
        val currentDate = java.util.Date(now)

        if (lastDate.day != currentDate.day || lastDate.month != currentDate.month) {
            lastSummaryDate = now
            sendDailySummary()
        }
    }

    private fun sendDailySummary() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val webhookUrl = settingsRepository.webhookUrlOrDefault
                if (webhookUrl.isBlank()) return@launch

                val message = buildString {
                    append("📊 Daily Usage Summary\n")
                    append("Total usage: $totalUsageSeconds seconds\n")
                    append("Unique apps used: $uniqueAppsUsed\n")
                    append("Apps blocked today: $blockCount")
                }

                discordService.sendWebhookMessage(webhookUrl, message)
                Log.d("LauncherService", "Daily summary sent (blocked: $blockCount, usage: $totalUsageSeconds s, apps: $uniqueAppsUsed)")
            } catch (e: Exception) {
                Log.e("LauncherService", "Failed to send daily summary", e)
            }
        }
    }

    /**
     * Returns a formatted string with today's usage summary.
     */
    fun getTodayUsageSummary(): String {
        val hours = totalUsageSeconds / 3600
        val minutes = (totalUsageSeconds % 3600) / 60
        val blockedApps = appUsageMap.size - uniqueAppsUsed + blockCount

        val appDetails = appUsageMap.entries.joinToString("\n") { (pkg, secs) ->
            val h = secs / 3600
            val m = (secs % 3600) / 60
            val label = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            } catch (e: Exception) {
                pkg
            }
            "- $label: ${if (h > 0) "$h h " else ""}$m min"
        }

        return buildString {
            append("📊 Today's Usage Summary\n")
            append("Total usage: ${if (hours > 0) "$h " else ""}$minutes min\n")
            append("Unique apps used: $uniqueAppsUsed\n")
            append("Apps blocked: $blockCount\n")
            if (appDetails.isNotEmpty()) {
                append("\nApp breakdown:\n$appDetails")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorTimer?.cancel()
        monitorTimer = null
    }
}
