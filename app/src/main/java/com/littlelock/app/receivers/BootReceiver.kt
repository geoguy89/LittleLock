package com.littlelock.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.littlelock.app.data.local.SettingsRepository
import com.littlelock.app.services.LauncherService
import com.littlelock.app.services.UsageTrackingService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Boot receiver that starts protection services after device reboot.
 */
class BootReceiver : BroadcastReceiver(), KoinComponent {

    private val settingsRepository: SettingsRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        if (!settingsRepository.protectionEnabled) {
            return
        }

        Log.d("BootReceiver", "Device booted, starting protection services")

        // Start launcher service
        val launcherIntent = Intent(context, LauncherService::class.java)
        context.startForegroundService(launcherIntent)

        // Start usage tracking service
        val usageIntent = Intent(context, UsageTrackingService::class.java)
        context.startService(usageIntent)
    }
}
