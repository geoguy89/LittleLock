package com.littlelock.app.services

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.app.Service
import android.provider.Settings
import android.util.Log
import com.littlelock.app.data.local.AppDatabase
import com.littlelock.app.data.local.UsageSessionDao
import com.littlelock.app.data.local.UsageSessionEntity
import com.littlelock.app.data.local.SettingsRepository
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import kotlinx.coroutines.*
import java.time.LocalDateTime

/**
 * Service that tracks app usage statistics.
 * Monitors which apps are used and for how long.
 * Stores usage sessions in the local database.
 */
class UsageTrackingService : Service(), KoinComponent {

    private val settingsRepository: SettingsRepository by inject()
    private lateinit var usageSessionDao: UsageSessionDao

    private var trackingJob: Job? = null
    private var currentApp: String? = null
    private var currentAppStartTime: LocalDateTime? = null

    override fun onCreate() {
        super.onCreate()
        usageSessionDao = AppDatabase.getDatabase(this).usageSessionDao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startTracking()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTracking() {
        trackingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(5000) // Check every 5 seconds
                trackCurrentApp()
            }
        }
    }

    private fun trackCurrentApp() {
        val currentAppPackage = getCurrentAppPackage()
        if (currentAppPackage == null) {
            return
        }

        if (currentApp != currentAppPackage) {
            // Save previous app session if exists
            if (currentApp != null && currentAppStartTime != null) {
                saveUsageSession(currentApp!!, currentAppStartTime!!)
            }

            // Start tracking new app
            currentApp = currentAppPackage
            currentAppStartTime = LocalDateTime.now()
        }
    }

    private fun getCurrentAppPackage(): String? {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val tasks = activityManager.runningTasks
            if (tasks.isNotEmpty()) {
                tasks[0].topActivity?.packageName
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun saveUsageSession(packageName: String, startTime: LocalDateTime) {
        val endTime = LocalDateTime.now()
        val durationSeconds = java.time.Duration.between(startTime, endTime).seconds

        if (durationSeconds < 10) {
            return // Ignore very short sessions
        }

        val session = UsageSessionEntity(
            appName = packageName,
            durationSeconds = durationSeconds.toInt(),
            timestamp = startTime.toString()
        )

        lifecycleScope.launch {
            try {
                usageSessionDao.insertSession(session)
                Log.d("UsageTrackingService", "Saved session: $packageName for ${durationSeconds}s")
            } catch (e: Exception) {
                Log.e("UsageTrackingService", "Failed to save session", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        trackingJob?.cancel()

        // Save final session
        if (currentApp != null && currentAppStartTime != null) {
            saveUsageSession(currentApp!!, currentAppStartTime!!)
        }
    }
}
