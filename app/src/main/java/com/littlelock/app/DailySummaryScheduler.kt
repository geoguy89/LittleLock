package com.littlelock.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Schedules a daily alarm to send a usage summary via Discord webhook.
 * Fire time: set by the parent (11:59 PM unless changed).
 */
object DailySummaryScheduler {

    private const val TAG = "DailySummaryScheduler"
    const val ALARM_ACTION = "com.littlelock.app.DAILY_SUMMARY_ALARM"

    /**
     * Schedule the daily summary alarm for the configured time (default 23:59).
     */
    fun scheduleDailySummary(context: Context) {
        // an alarm that never arrived (the tablet was off, or Android held it back) is sent now, before the next one is set
        maintain(context)
        val at = Preferences.getSummaryMin(context)          // the parent sets it (11:59 PM unless changed)
        val hour = at / 60
        val minute = at % 60

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

        // Alarm for the configured time today/tomorrow
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

        Preferences.setSummaryDue(context, calendar.timeInMillis)

        // inexact on purpose: an exact alarm needs the SCHEDULE_EXACT_ALARM permission on Android 12+ (without it
        // this throws), and a daily summary a few minutes late is fine
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

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

    @Volatile private var lastRetry = 0L

    /**
     * Called from the alarm and every few seconds by the monitoring service: sends the summary whose time has come (the
     * alarm is inexact, and may be late or lost), and tries again for one that couldn't be delivered.
     */
    fun maintain(context: Context, now: Long = System.currentTimeMillis()) {
        val due = Preferences.getSummaryDue(context)
        if (due in 1..now) {
            Preferences.setSummaryDue(context, 0L)     // taken: scheduling sets the next one
            handleDailySummary(context, due)
            scheduleDailySummary(context)
            return
        }
        val pending = Preferences.getPendingSummary(context) ?: return
        if (now - lastRetry < RETRY_MS) return
        lastRetry = now
        send(context, pending)
    }

    private const val RETRY_MS = 5 * 60_000L

    /** The alarm went off: send the summary it was set for, then set tomorrow's. */
    fun onAlarm(context: Context, now: Long = System.currentTimeMillis()) {
        val due = Preferences.getSummaryDue(context)
        if (due == 0L) {
            // an alarm set by an older version (it didn't note its time): it was meant for the evening just gone
            handleDailySummary(context, now - 30 * 60_000L)
        } else {
            maintain(context, maxOf(now, due))
        }
        scheduleDailySummary(context)
    }

    /**
     * The summary for the day that [due] falls on - the alarm is meant for 11:59 PM but often arrives after midnight, so
     * "today" at that moment would be the wrong (empty) day. Lists every app that was used and for how long.
     */
    fun handleDailySummary(context: Context, due: Long = Preferences.getSummaryDue(context)) {
        // a minute before the due time, so a summary due exactly when the new day starts reports the day that just ended
        val day = UsageLog.dayKey((if (due > 0) due else System.currentTimeMillis()) - 60_000L, Preferences.getDayStart(context))
        if (!Preferences.getWebhookUrl(context).isNullOrEmpty() && Preferences.isDailySummaryEnabled(context)) {
            send(context, day)
        }
        Log.d(TAG, "Daily summary for $day handled")
    }

    /** The summary of the day so far, sent right away (the parent asked for it). */
    fun sendToday(context: Context) = send(context, Preferences.today(context), force = true)

    private fun send(context: Context, day: String, force: Boolean = false) {
        val url = Preferences.getWebhookUrl(context)
        if (url.isNullOrEmpty() || (!force && !Preferences.isDailySummaryEnabled(context))) {
            Preferences.setPendingSummary(context, null)
            return
        }
        val data = Preferences.usageData(context)
        val app = context.applicationContext
        val list = UsageLog.itemised(data, day, { Preferences.appLabel(app, it) })
        Preferences.setPendingSummary(app, day)        // until Discord accepts it
        DiscordWebhookService.sendDailySummary(
            app, url, UsageLog.dayName(day), UsageLog.totalOpens(data, day), UsageLog.headline(data, day), list
        ) { ok -> if (ok && Preferences.getPendingSummary(app) == day) Preferences.setPendingSummary(app, null) }
    }
}

/**
 * BroadcastReceiver that fires when the daily summary alarm triggers.
 */
class DailySummaryReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DailySummaryScheduler.ALARM_ACTION) {
            DailySummaryScheduler.onAlarm(context)
        }
    }
}
