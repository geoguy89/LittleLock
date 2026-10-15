package com.littlelock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Boot receiver to auto-start the launcher service on device boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            val serviceIntent = Intent(context, LauncherService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // Schedule daily summary alarm on boot
            DailySummaryScheduler.scheduleDailySummary(context)
        }
    }
}
