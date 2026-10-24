package com.littlelock.app

import android.content.Context
import android.content.SharedPreferences
import java.util.Base64

/**
 * SharedPreferences helper for the app's settings, the allowed apps and the (hashed) parent PIN.
 */
object Preferences {

    private const val PREFS_NAME = "littlelock_prefs"
    private const val KEY_PIN = "pin"
    private const val KEY_WEBHOOK_URL = "webhook_url"
    private const val KEY_NOTIF_APP_LAUNCH = "notif_app_launch"
    private const val KEY_NOTIF_DAILY_SUMMARY = "notif_daily_summary"
    private const val KEY_NOTIF_BLOCKED_APPS = "notif_blocked_apps"
    private const val KEY_PIN_SET = "pin_set"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_FAILED_ATTEMPTS = "pin_failed_attempts"
    private const val KEY_LOCKOUT_UNTIL = "pin_lockout_until"
    private const val KEY_ALLOWED_APPS = "allowed_apps"
    private const val KEY_KID_MODE = "kid_mode"
    private const val KEY_KID_NAME = "kid_name"
    private const val KEY_TILE_SIZE = "tile_size"
    private const val KEY_WALLPAPER = "wallpaper"
    private const val KEY_BLOCK_SHADE = "block_shade"
    private const val KEY_KIOSK = "kiosk"
    private const val KEY_LAYOUT = "layout_mode"
    private const val KEY_USAGE_EVERY = "usage_every_h"
    private const val KEY_USAGE_LAST = "usage_last_sent"
    private const val KEY_USAGE_LAST_USED = "usage_last_used"
    private const val KEY_IDLE = "idle_minutes"
    private const val KEY_ACCENT = "accent"
    private const val KEY_ALIGN = "layout_align"
    private const val KEY_GAP = "layout_gap"
    private const val KEY_LIMIT_ON = "limit_on"
    private const val KEY_LIMIT_MIN = "limit_minutes"
    private const val KEY_USED_MS = "used_ms"
    private const val KEY_USED_DAY = "used_day"
    private const val KEY_FORCE_LOCK = "force_lock"
    private const val KEY_EXTENDED_UNTIL = "extended_until"
    private const val KEY_BED_ON = "bed_on"
    private const val KEY_BED_START = "bed_start_min"
    private const val KEY_BED_MINUTES = "bed_minutes"       // before 1.8: how long it lasted; now only read to carry that over
    private const val KEY_BED_END = "bed_end_min"
    private const val KEY_SUMMARY_MIN = "summary_min"
    private const val KEY_DAY_START = "day_start_min"
    private const val KEY_NOTIF_WRONG_PIN = "notif_wrong_pin"
    private const val KEY_REMOTE_ON = "remote_on"
    private const val KEY_DISCORD_TOKEN = "discord_bot_token"
    private const val KEY_DISCORD_CHANNEL = "discord_channel"
    private const val KEY_DISCORD_USERS = "discord_users"
    private const val KEY_DISCORD_LAST = "discord_last_id"

    const val MAX_ATTEMPTS = 5
    const val LOCKOUT_MS = 30_000L

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** True once a parent PIN exists (hashed, or an old plain one from before 1.2 that is migrated on first use). */
    fun isPinSet(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.contains(KEY_PIN_HASH) || !prefs.getString(KEY_PIN, null).isNullOrEmpty()
    }

    /** Stores the PIN as a salted hash; the PIN itself is never written. */
    fun setPin(context: Context, pin: String) {
        require(PinHasher.isValidPin(pin)) { "PIN must be ${PinHasher.PIN_LENGTH} digits" }
        val salt = PinHasher.newSalt()
        getPrefs(context).edit()
            .putString(KEY_PIN_SALT, b64(salt))
            .putString(KEY_PIN_HASH, b64(PinHasher.hash(pin, salt)))
            .remove(KEY_PIN)
            .putBoolean(KEY_PIN_SET, true)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = getPrefs(context)
        val hash = prefs.getString(KEY_PIN_HASH, null)
        val salt = prefs.getString(KEY_PIN_SALT, null)
        if (hash != null && salt != null) {
            return PinHasher.verify(pin, unb64(salt), unb64(hash))
        }
        // a PIN saved in plain text by version 1.1 or older: check it once, then store it hashed
        val legacy = prefs.getString(KEY_PIN, null)
        if (!legacy.isNullOrEmpty() && legacy == pin) {
            setPin(context, pin)
            return true
        }
        return false
    }

    /** Milliseconds until the PIN can be tried again (0 = now). */
    fun pinLockoutRemaining(context: Context, now: Long = System.currentTimeMillis()): Long =
        (getPrefs(context).getLong(KEY_LOCKOUT_UNTIL, 0L) - now).coerceAtLeast(0L)

    /** Counts a wrong PIN; after [MAX_ATTEMPTS] in a row the PIN pad locks for [LOCKOUT_MS]. Returns which wrong try in a row this was. */
    fun recordFailedPin(context: Context, now: Long = System.currentTimeMillis()): Int {
        val prefs = getPrefs(context)
        val failed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        if (failed >= MAX_ATTEMPTS) {
            prefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).putLong(KEY_LOCKOUT_UNTIL, now + LOCKOUT_MS).apply()
        } else {
            prefs.edit().putInt(KEY_FAILED_ATTEMPTS, failed).apply()
        }
        return failed
    }

    /** Tell the parent in Discord when a wrong PIN is typed. On by default. */
    fun isWrongPinNotifEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_NOTIF_WRONG_PIN, true)

    fun setWrongPinNotifEnabled(context: Context, on: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NOTIF_WRONG_PIN, on).apply()
    }

    fun resetFailedPins(context: Context) {
        getPrefs(context).edit().putInt(KEY_FAILED_ATTEMPTS, 0).putLong(KEY_LOCKOUT_UNTIL, 0L).apply()
    }

    /** Package names of the apps the parent allowed. Empty until the parent picks some. */
    fun getAllowedApps(context: Context): Set<String> =
        getPrefs(context).getStringSet(KEY_ALLOWED_APPS, emptySet())?.toSet() ?: emptySet()

    fun setAllowedApps(context: Context, packages: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_ALLOWED_APPS, HashSet(packages)).apply()
    }

    /** Kid mode on: only allowed apps are shown and other apps are sent back home. On by default. */
    fun isKidMode(context: Context): Boolean = getPrefs(context).getBoolean(KEY_KID_MODE, true)

    fun setKidMode(context: Context, on: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_KID_MODE, on).apply()
    }

    /** The child's name for the greeting on the home screen ("" = none). */
    fun getKidName(context: Context): String = getPrefs(context).getString(KEY_KID_NAME, "") ?: ""

    fun setKidName(context: Context, name: String) {
        getPrefs(context).edit().putString(KEY_KID_NAME, name.trim().take(24)).apply()
    }

    /** Index into [Look.TILES]: how big the app tiles are on the home screen. */
    fun getTileSize(context: Context): Int = getPrefs(context).getInt(KEY_TILE_SIZE, Look.DEFAULT_TILE)

    fun setTileSize(context: Context, index: Int) {
        getPrefs(context).edit().putInt(KEY_TILE_SIZE, index.coerceIn(0, Look.TILES.lastIndex)).apply()
    }

    /** "" (the plain background), a colour ("color:ffd9d0ff") or [Look.IMAGE] (a picture the parent chose). */
    fun getWallpaper(context: Context): String = getPrefs(context).getString(KEY_WALLPAPER, "") ?: ""

    fun setWallpaper(context: Context, value: String) {
        getPrefs(context).edit().putString(KEY_WALLPAPER, value).apply()
    }

    /** Keep the child out of the notification shade and quick settings while kid mode is on. On by default. */
    fun isBlockShade(context: Context): Boolean = getPrefs(context).getBoolean(KEY_BLOCK_SHADE, true)

    fun setBlockShade(context: Context, on: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BLOCK_SHADE, on).apply()
    }

    /** Kiosk lockdown (needs the device-owner step, see [Kiosk]). On by default: if the parent set it up, they want it. */
    fun isKioskOn(context: Context): Boolean = getPrefs(context).getBoolean(KEY_KIOSK, true)

    fun setKioskOn(context: Context, on: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_KIOSK, on).apply()
    }

    /** [Layouts.GRID] (the picked size, centred) or [Layouts.AUTO] (the biggest tiles that fit, centred). */
    fun getLayoutMode(context: Context): Int = getPrefs(context).getInt(KEY_LAYOUT, Layouts.GRID)

    fun setLayoutMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_LAYOUT, if (mode == Layouts.AUTO) Layouts.AUTO else Layouts.GRID).apply()
    }

    /** Hours between usage updates posted to the Discord channel (0 = none). */
    fun getUsageEvery(context: Context): Int = getPrefs(context).getInt(KEY_USAGE_EVERY, 2)

    fun setUsageEvery(context: Context, hours: Int) {
        getPrefs(context).edit().putInt(KEY_USAGE_EVERY, hours.coerceIn(0, 24)).apply()
    }

    fun getUsageLast(context: Context): Long = getPrefs(context).getLong(KEY_USAGE_LAST, 0L)

    fun getUsageLastUsed(context: Context): Long = getPrefs(context).getLong(KEY_USAGE_LAST_USED, 0L)

    fun setUsageSent(context: Context, at: Long, usedMs: Long) {
        getPrefs(context).edit().putLong(KEY_USAGE_LAST, at).putLong(KEY_USAGE_LAST_USED, usedMs).apply()
    }

    /** Minutes without a touch before LittleLock puts the screen to sleep (0 = never). */
    fun getIdleMinutes(context: Context): Int = getPrefs(context).getInt(KEY_IDLE, Idle.DEFAULT_MINUTES)

    fun setIdleMinutes(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_IDLE, if (minutes in Idle.CHOICES) minutes else Idle.DEFAULT_MINUTES).apply()
    }

    /** The key of the accent colour the parent picked (see [Accent]). */
    fun getAccent(context: Context): String = getPrefs(context).getString(KEY_ACCENT, Accent.DEFAULT) ?: Accent.DEFAULT

    fun setAccent(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_ACCENT, Accent.choice(key).key).apply()
    }

    /** [Layouts.TOP], [Layouts.MIDDLE] or [Layouts.BOTTOM]: where the block of apps sits down the screen. */
    fun getLayoutAlign(context: Context): Int = getPrefs(context).getInt(KEY_ALIGN, Layouts.MIDDLE).coerceIn(Layouts.TOP, Layouts.BOTTOM)

    fun setLayoutAlign(context: Context, align: Int) {
        getPrefs(context).edit().putInt(KEY_ALIGN, align.coerceIn(Layouts.TOP, Layouts.BOTTOM)).apply()
    }

    /** Index into [Layouts.GAP_DP]: the space between the app tiles. */
    fun getLayoutGap(context: Context): Int = getPrefs(context).getInt(KEY_GAP, Layouts.DEFAULT_GAP).coerceIn(0, Layouts.GAP_DP.lastIndex)

    fun setLayoutGap(context: Context, step: Int) {
        getPrefs(context).edit().putInt(KEY_GAP, step.coerceIn(0, Layouts.GAP_DP.lastIndex)).apply()
    }

    /** When "today" starts over, as minutes after midnight (the parent can move it; midnight by default). */
    fun getDayStart(context: Context): Int = getPrefs(context).getInt(KEY_DAY_START, 0).coerceIn(0, 24 * 60 - 1)

    /** The day [now] belongs to, counting from the parent's "new day starts at" time. */
    fun today(context: Context, now: Long = System.currentTimeMillis()): String = UsageLog.dayKey(now, getDayStart(context))

    private fun dayKey(p: SharedPreferences, now: Long): String = UsageLog.dayKey(now, p.getInt(KEY_DAY_START, 0).coerceIn(0, 24 * 60 - 1))

    /** Daily summary time, as minutes after midnight (from the old hour / minute settings if those were used). */
    private fun summaryMin(p: SharedPreferences): Int =
        if (p.contains(KEY_SUMMARY_MIN)) p.getInt(KEY_SUMMARY_MIN, TimeLimit.DEFAULT_SUMMARY)
        else p.getInt("daily_summary_hour", 23) * 60 + p.getInt("daily_summary_minute", 59)

    fun getSummaryMin(context: Context): Int = summaryMin(getPrefs(context)).coerceIn(0, 24 * 60 - 1)

    /** The screen-time settings; today's used time starts again at 0 each new day. */
    fun timeSettings(context: Context, now: Long = System.currentTimeMillis()): TimeLimit.Settings {
        val p = getPrefs(context)
        val sameDay = p.getString(KEY_USED_DAY, "") == dayKey(p, now)
        val bedStart = p.getInt(KEY_BED_START, TimeLimit.DEFAULT_BED_START)
        return TimeLimit.Settings(
            limitOn = p.getBoolean(KEY_LIMIT_ON, false),
            limitMinutes = p.getInt(KEY_LIMIT_MIN, 60),
            usedMs = if (sameDay) p.getLong(KEY_USED_MS, 0L) else 0L,
            forceLock = p.getBoolean(KEY_FORCE_LOCK, false),
            extendedUntil = p.getLong(KEY_EXTENDED_UNTIL, 0L),
            bedOn = p.getBoolean(KEY_BED_ON, true),
            bedStartMin = p.getInt(KEY_BED_START, TimeLimit.DEFAULT_BED_START),
            bedEndMin = if (p.contains(KEY_BED_END)) p.getInt(KEY_BED_END, TimeLimit.DEFAULT_BED_END)
                        else if (p.contains(KEY_BED_MINUTES)) (bedStart + p.getInt(KEY_BED_MINUTES, 0)) % (24 * 60)    // carried over from "lasts N hours"
                        else TimeLimit.DEFAULT_BED_END,
            summaryMin = summaryMin(p),
            dayStartMin = p.getInt(KEY_DAY_START, 0)
        )
    }

    fun saveTimeSettings(context: Context, s: TimeLimit.Settings, now: Long = System.currentTimeMillis()) {
        getPrefs(context).edit()
            .putBoolean(KEY_LIMIT_ON, s.limitOn)
            .putInt(KEY_LIMIT_MIN, s.limitMinutes.coerceIn(TimeLimit.MIN_LIMIT, TimeLimit.MAX_LIMIT))
            .putLong(KEY_USED_MS, s.usedMs)
            .putString(KEY_USED_DAY, UsageLog.dayKey(now, s.dayStartMin.coerceIn(0, 24 * 60 - 1)))
            .putBoolean(KEY_FORCE_LOCK, s.forceLock)
            .putLong(KEY_EXTENDED_UNTIL, s.extendedUntil)
            .putBoolean(KEY_BED_ON, s.bedOn)
            .putInt(KEY_BED_START, s.bedStartMin.coerceIn(0, 24 * 60 - 1))
            .putInt(KEY_BED_END, s.bedEndMin.coerceIn(0, 24 * 60 - 1))
            .putInt(KEY_SUMMARY_MIN, s.summaryMin.coerceIn(0, 24 * 60 - 1))
            .putInt(KEY_DAY_START, s.dayStartMin.coerceIn(0, 24 * 60 - 1))
            .apply()
    }

    /** Counts [deltaMs] of screen time for today (only while a limit is set and the tablet is open). */
    fun addUsedMs(context: Context, deltaMs: Long, now: Long = System.currentTimeMillis()): TimeLimit.Settings {
        val s = TimeLimit.addUsage(timeSettings(context, now), deltaMs, now)
        saveTimeSettings(context, s, now)
        return s
    }

    /** Is the tablet locked by screen time right now? */
    fun isTimeLocked(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        TimeLimit.state(timeSettings(context, now), now).locked

    fun isRemoteOn(context: Context): Boolean = getPrefs(context).getBoolean(KEY_REMOTE_ON, false)

    fun setRemoteOn(context: Context, on: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_REMOTE_ON, on).apply()
    }

    fun getDiscordToken(context: Context): String = getPrefs(context).getString(KEY_DISCORD_TOKEN, "") ?: ""

    fun getDiscordChannel(context: Context): String = getPrefs(context).getString(KEY_DISCORD_CHANNEL, "") ?: ""

    fun getDiscordUsers(context: Context): String = getPrefs(context).getString(KEY_DISCORD_USERS, "") ?: ""

    fun setDiscordRemote(context: Context, token: String, channel: String, users: String) {
        getPrefs(context).edit()
            .putString(KEY_DISCORD_TOKEN, token.trim())
            .putString(KEY_DISCORD_CHANNEL, channel.trim())
            .putString(KEY_DISCORD_USERS, users.trim())
            .remove(KEY_DISCORD_LAST)                    // a changed channel starts fresh: nothing old is replayed
            .apply()
    }

    fun getDiscordLast(context: Context): String = getPrefs(context).getString(KEY_DISCORD_LAST, "") ?: ""

    fun setDiscordLast(context: Context, id: String) {
        getPrefs(context).edit().putString(KEY_DISCORD_LAST, id).apply()
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)

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

    // ------------------------------------------------------------------ what was used, app by app (see [UsageLog])

    private const val KEY_USAGE_LOG = "usage_log"
    private const val KEY_SUMMARY_DUE = "summary_due"
    private const val KEY_SUMMARY_PENDING = "summary_pending"
    private const val KEY_UPDATE_SEEN = "update_seen_tag"

    @Synchronized
    fun usageData(context: Context): UsageData = UsageLog.parse(getPrefs(context).getString(KEY_USAGE_LOG, null))

    /** Adds time in front and/or openings for [pkg] to today. */
    @Synchronized
    fun recordUsage(context: Context, pkg: String, ms: Long, opens: Int, now: Long = System.currentTimeMillis()) {
        val before = usageData(context)
        val after = UsageLog.add(before, today(context, now), pkg, ms, opens)
        if (after !== before) getPrefs(context).edit().putString(KEY_USAGE_LOG, UsageLog.serialize(after)).apply()
    }

    /** How many times an app was opened today (every app counts, however it is named). */
    fun getDailyLaunchCount(context: Context, now: Long = System.currentTimeMillis()): Int =
        UsageLog.totalOpens(usageData(context), today(context, now))

    /** "• YouTube Kids - 1 h 5 min, opened 3 times" lines for today, for the Discord notes. */
    fun usageToday(context: Context, now: Long = System.currentTimeMillis(), bold: Boolean = true): String =
        UsageLog.itemised(usageData(context), today(context, now), { appLabel(context, it) }, bold = bold)

    /** The app's name as the child sees it (the package name when it has been removed). */
    fun appLabel(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    /** The moment the next daily summary is meant for: it reports the day that moment falls on, even when it runs late. */
    fun getSummaryDue(context: Context): Long = getPrefs(context).getLong(KEY_SUMMARY_DUE, 0L)

    fun setSummaryDue(context: Context, at: Long) {
        getPrefs(context).edit().putLong(KEY_SUMMARY_DUE, at).apply()
    }

    /** The day of a summary that Discord hasn't accepted yet (no internet): it is built again from the log and retried. */
    fun getPendingSummary(context: Context): String? = getPrefs(context).getString(KEY_SUMMARY_PENDING, null)

    fun setPendingSummary(context: Context, text: String?) {
        getPrefs(context).edit().apply { if (text == null) remove(KEY_SUMMARY_PENDING) else putString(KEY_SUMMARY_PENDING, text) }.apply()
    }

    /** The release tag the parent has already been told about (so the notice shows once). */
    fun getUpdateSeen(context: Context): String = getPrefs(context).getString(KEY_UPDATE_SEEN, "") ?: ""

    fun setUpdateSeen(context: Context, tag: String) {
        getPrefs(context).edit().putString(KEY_UPDATE_SEEN, tag).apply()
    }
}
