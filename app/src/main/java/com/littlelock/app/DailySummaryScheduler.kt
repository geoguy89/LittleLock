package com.littlelock.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Schedules a daily alarm to send a usage summary via Discord webhook.
 * Default fire time: 11:59 PM.
 */
object DailySummaryScheduler {

    private const val TAG = "DailySummaryScheduler"
    private const val PREFS_NAME = "littlelock_prefs"
    private const val KEY_DAILY_SUMMARY_HOUR = "daily_summary_hour"
    private const val KEY_DAILY_SUMMARY_MINUTE = "daily_summary_minute"

    private const val DEFAULT_HOUR = 23
    private const val DEFAULT_MINUTE = 59

    const val ALARM_ACTION = "com.littlelock.app.DAILY_SUMMARY_ALARM"

    /**
     * Schedule the daily summary alarm for the configured time (default 23:59).
     */
    fun scheduleDailySummary(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hour = prefs.getInt(KEY_DAILY_SUMMARY_HOUR, DEFAULT_HOUR)
        val minute = prefs.getInt(KEY_DAILY_SUMMARY_MINUTE, DEFAULT_MINUTE)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailySummaryReceiver::class.java).apply {
            action = ALARM_ACTION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set exact alarm for the configured time today/tomorrow
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            // If the time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }

        Log.d(TAG, "Daily summary scheduled for ${hour}:${minute.toString().padStart(2, '0')}")
    }

    /**
     * Cancel the scheduled daily summary alarm.
     */
    fun cancelDailySummary(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailySummaryReceiver::class.java).apply {
            action = ALARM_ACTION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Daily summary alarm cancelled")
        }
    }

    /**
     * Handle the daily summary alarm firing: send summary and reset count.
     */
    fun handleDailySummary(context: Context) {
        val launchCount = Preferences.getDailyLaunchCount(context)
        val webhookUrl = Preferences.getWebhookUrl(context)

        if (!webhookUrl.isNullOrEmpty() && Preferences.isDailySummaryEnabled(context)) {
            DiscordWebhookService.sendDailySummary(context, webhookUrl, launchCount)
        }

        // Reset the daily count after sending
        Preferences.resetDailyCount(context)
        Log.d(TAG, "Daily summary sent with $launchCount launches, count reset")
    }
}

/**
 * BroadcastReceiver that fires when the daily summary alarm triggers.
 */
class DailySummaryReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DailySummaryScheduler.ALARM_ACTION) {
            DailySummaryScheduler.handleDailySummary(context)
        }
    }
}
