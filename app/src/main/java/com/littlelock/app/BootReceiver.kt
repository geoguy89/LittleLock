package com.littlelock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After a reboot (or an update of LittleLock) re-arms the daily summary. Kid mode itself needs nothing started:
 * LittleLock is the home app and the accessibility service is started by Android.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            DailySummaryScheduler.scheduleDailySummary(context)
        }
    }
}
