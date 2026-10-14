package com.littlelock.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.ActivityManager
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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

/**
 * Foreground service that maintains the launcher overlay.
 * Monitors app usage and blocks unauthorized apps.
 * Runs continuously while protection is enabled.
 */
class LauncherService : Service(), KoinComponent {

    private val settingsRepository: SettingsRepository by inject()
    private val allowedAppDao: AllowedAppDao by inject()

    private val CHANNEL_ID = "launcher_channel"
    private val NOTIFICATION_ID = 1

    private var monitorTimer: Timer? = null
    private var lastPackageName: String? = null
    private var blockCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
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
            }
        }, 0, 1000) // Check every second
    }

    private fun checkCurrentApp() {
        val currentApp = getCurrentPackageName()
        if (currentApp == null || currentApp == packageName) {
            return
        }

        if (currentApp != lastPackageName) {
            lastPackageName = currentApp
            if (!isAppAllowed(currentApp)) {
                blockApp(currentApp)
            }
        }
    }

    private fun getCurrentPackageName(): String? {
        // Get the currently running app's package
        return try {
            val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val processes = activityManager.runningAppProcesses
            if (processes.isNotEmpty()) {
                processes[0].importanceRank
                processes[0].processName
            } else null
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun isAppAllowed(packageName: String): Boolean {
        var result = false
        CoroutineScope(Dispatchers.IO).launch {
            val allowedApps = allowedAppDao.getAllowedApps()
            result = allowedApps.any { it.packageName == packageName }
        }.join()
        return result
    }

    private fun blockApp(packageName: String) {
        blockCount++

        // Bring launcher to front
        try {
            val intent = Intent(this, KidsLauncherActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Ignore
        }

        // Log blocked app
        android.util.Log.w("LauncherService", "Blocked app: $packageName (count: $blockCount)")
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorTimer?.cancel()
        monitorTimer = null
    }
}
