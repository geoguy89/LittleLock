package com.littlelock.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.littlelock.app.data.local.AppDatabase
import com.littlelock.app.di.AppModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application class for LittleLock.
 * Initializes Koin DI, notification channels, and shared preferences.
 */
class LittleLockApp : Application(), KoinComponent {

    companion object {
        const val CHANNEL_ID_FOREGROUND = "littlelock_foreground"
        const val CHANNEL_ID_USAGE = "littlelock_usage"
        const val CHANNEL_ID_WEBHOOK = "littlelock_webhook"
    }

    private val database: AppDatabase by inject()

    override fun onCreate() {
        super.onCreate()

        // Initialize Koin
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@LittleLockApp)
            modules(AppModule.modules)
        }

        // Create notification channels
        createNotificationChannels()

        // Initialize database
        try {
            database.close()
        } catch (e: Exception) {
            // Database may not be ready yet, that's fine
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Foreground service channel (launcher overlay)
        val foregroundChannel = NotificationChannel(
            CHANNEL_ID_FOREGROUND,
            "LittleLock Protection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when LittleLock protection is active"
        }
        notificationManager.createNotificationChannel(foregroundChannel)

        // Usage tracking channel
        val usageChannel = NotificationChannel(
            CHANNEL_ID_USAGE,
            "Usage Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Tracks app usage for children"
        }
        notificationManager.createNotificationChannel(usageChannel)

        // Webhook notification channel
        val webhookChannel = NotificationChannel(
            CHANNEL_ID_WEBHOOK,
            "Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Discord webhook notifications"
        }
        notificationManager.createNotificationChannel(webhookChannel)
    }
}
