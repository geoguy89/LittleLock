package com.littlelock.app.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent settings using SharedPreferences.
 */
class SettingsRepository(private val context: Context) {

    companion object {
        const val PREFS_NAME = "littlelock_prefs"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PROTECTION_ENABLED = "protection_enabled"
        const val KEY_WEBHOOK_URL = "webhook_url"
        const val KEY_DAILY_LIMIT_MINUTES = "daily_limit_minutes"
        const val KEY_FIRST_LAUNCH = "first_launch"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var pinHash: String?
        get() = prefs.getString(KEY_PIN_HASH, null)
        set(value) = prefs.edit().putString(KEY_PIN_HASH, value).apply()

    var protectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROTECTION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, value).apply()

    var webhookUrl: String?
        get() = prefs.getString(KEY_WEBHOOK_URL, null)
        set(value) = prefs.edit().putString(KEY_WEBHOOK_URL, value).apply()

    var dailyLimitMinutes: Int
        get() = prefs.getInt(KEY_DAILY_LIMIT_MINUTES, 0)
        set(value) = prefs.edit().putInt(KEY_DAILY_LIMIT_MINUTES, value).apply()

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()

    var screenTimeEnabled: Boolean
        get() = prefs.getBoolean("screen_time_enabled", true)
        set(value) = prefs.edit().putBoolean("screen_time_enabled", value).apply()

    var appRestrictionsEnabled: Boolean
        get() = prefs.getBoolean("app_restrictions_enabled", true)
        set(value) = prefs.edit().putBoolean("app_restrictions_enabled", value).apply()

    var wallpaperEnabled: Boolean
        get() = prefs.getBoolean("wallpaper_enabled", false)
        set(value) = prefs.edit().putBoolean("wallpaper_enabled", value).apply()
}
