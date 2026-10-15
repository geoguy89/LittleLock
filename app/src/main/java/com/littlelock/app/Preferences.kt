package com.littlelock.app

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences helper for storing app settings and PIN.
 */
object Preferences {

    private const val PREFS_NAME = "littlelock_prefs"
    private const val KEY_PIN = "pin"
    private const val KEY_WEBHOOK_URL = "webhook_url"
    private const val KEY_NOTIF_APP_LAUNCH = "notif_app_launch"
    private const val KEY_NOTIF_DAILY_SUMMARY = "notif_daily_summary"
    private const val KEY_NOTIF_BLOCKED_APPS = "notif_blocked_apps"
    private const val KEY_DAILY_LAUNCH_COUNT = "daily_launch_count"
    private const val KEY_DAILY_DATE = "daily_date"
    private const val KEY_PIN_SET = "pin_set"

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getPin(context: Context): String? {
        return getPrefs(context).getString(KEY_PIN, null)
    }

    fun setPin(context: Context, pin: String) {
        getPrefs(context).edit().putString(KEY_PIN, pin).putBoolean(KEY_PIN_SET, true).apply()
    }

    fun isPinSet(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PIN_SET, false)
    }

    fun getWebhookUrl(context: Context): String? {
        return getPrefs(context).getString(KEY_WEBHOOK_URL, null)
    }

    fun setWebhookUrl(context: Context, url: String?) {
        getPrefs(context).edit().putString(KEY_WEBHOOK_URL, url).apply()
    }

    fun isAppLaunchNotifEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIF_APP_LAUNCH, true)
    }

    fun setAppLaunchNotifEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NOTIF_APP_LAUNCH, enabled).apply()
    }

    fun isDailySummaryEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIF_DAILY_SUMMARY, true)
    }

    fun setDailySummaryEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NOTIF_DAILY_SUMMARY, enabled).apply()
    }

    fun isBlockedAppsNotifEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIF_BLOCKED_APPS, true)
    }

    fun setBlockedAppsNotifEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NOTIF_BLOCKED_APPS, enabled).apply()
    }

    fun getDailyLaunchCount(context: Context): Int {
        val prefs = getPrefs(context)
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
        val storedDate = prefs.getString(KEY_DAILY_DATE, "")
        return if (storedDate == today) {
            prefs.getInt(KEY_DAILY_LAUNCH_COUNT, 0)
        } else {
            0
        }
    }

    fun incrementDailyLaunchCount(context: Context) {
        val prefs = getPrefs(context)
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
        val storedDate = prefs.getString(KEY_DAILY_DATE, "")
        val count = if (storedDate == today) {
            prefs.getInt(KEY_DAILY_LAUNCH_COUNT, 0) + 1
        } else {
            1
        }
        prefs.edit()
            .putInt(KEY_DAILY_LAUNCH_COUNT, count)
            .putString(KEY_DAILY_DATE, today)
            .apply()
    }

    fun resetDailyCount(context: Context) {
        getPrefs(context).edit()
            .putInt(KEY_DAILY_LAUNCH_COUNT, 0)
            .putString(KEY_DAILY_DATE, "")
            .apply()
    }
}
