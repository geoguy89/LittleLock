package com.littlelock.app

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Screen time: a daily limit, a "lock now", and an extension the parent grants until a clock time. Plain numbers in, a decision
 * out, so every rule can be tested without a tablet.
 *
 *  - extended: until the chosen time everything is open, whatever else is set
 *  - locked now: the tablet stays locked until the parent extends or unlocks it
 *  - bedtime: every night from the start time until the morning time everything is off (on by default: 8 PM until 7 AM)
 *  - daily limit: when the minutes used today reach the limit the tablet locks (and stays locked until tomorrow or an extension)
 */
object TimeLimit {

    enum class Why { OPEN, LIMIT, FORCED, BEDTIME }

    data class Settings(
        val limitOn: Boolean = false,
        val limitMinutes: Int = 60,
        val usedMs: Long = 0L,
        val forceLock: Boolean = false,
        val extendedUntil: Long = 0L,
        val bedOn: Boolean = true,
        val bedStartMin: Int = DEFAULT_BED_START,            // minutes after midnight
        val bedEndMin: Int = DEFAULT_BED_END,                // minutes after midnight: the tablet wakes up at this time
        val summaryMin: Int = DEFAULT_SUMMARY,               // minutes after midnight: when the daily summary goes to Discord
        val dayStartMin: Int = 0                             // minutes after midnight: when "today" (used time, app use) starts over
    ) {
        /** How long the nightly lockdown lasts, from the start time to the morning time (across midnight if need be). */
        val bedMinutes: Int get() = (bedEndMin - bedStartMin + 24 * 60) % (24 * 60)
    }

    /** [bedEndsAt] is when the nightly lockdown ends while it is the reason for the lock (else 0). */
    class State(val locked: Boolean, val why: Why, val remainingMs: Long, val extendedUntil: Long, val bedEndsAt: Long = 0L)

    const val MIN_LIMIT = 5
    const val MAX_LIMIT = 720
    const val DEFAULT_BED_START = 20 * 60
    const val DEFAULT_BED_END = 7 * 60
    const val DEFAULT_SUMMARY = 23 * 60 + 59
    const val MIN_BED = 30
    const val MAX_BED = 23 * 60

    /** When the nightly lockdown that covers [now] ends, or 0 when [now] is not inside one. */
    fun bedEnd(s: Settings, now: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        if (!s.bedOn) return 0L
        val len = s.bedMinutes.coerceIn(MIN_BED, MAX_BED) * 60_000L
        val start = s.bedStartMin.coerceIn(0, 24 * 60 - 1)
        for (dayOffset in intArrayOf(0, -1)) {                      // tonight's window, or last night's that runs past midnight
            val c = Calendar.getInstance(tz)
            c.timeInMillis = now
            c.add(Calendar.DAY_OF_YEAR, dayOffset)
            c.set(Calendar.HOUR_OF_DAY, start / 60)
            c.set(Calendar.MINUTE, start % 60)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            val begin = c.timeInMillis
            if (now >= begin && now < begin + len) return begin + len
        }
        return 0L
    }

    fun state(s: Settings, now: Long, tz: TimeZone = TimeZone.getDefault()): State {
        val limitMs = s.limitMinutes * 60_000L
        if (s.extendedUntil > now) {
            return State(false, Why.OPEN, if (s.limitOn) (limitMs - s.usedMs).coerceAtLeast(0) else Long.MAX_VALUE, s.extendedUntil)
        }
        if (s.forceLock) return State(true, Why.FORCED, 0L, s.extendedUntil)
        val bedEnds = bedEnd(s, now, tz)
        if (bedEnds > 0) return State(true, Why.BEDTIME, 0L, s.extendedUntil, bedEnds)
        if (s.limitOn && s.usedMs >= limitMs) return State(true, Why.LIMIT, 0L, s.extendedUntil)
        return State(false, Why.OPEN, if (s.limitOn) limitMs - s.usedMs else Long.MAX_VALUE, s.extendedUntil)
    }

    /** Count [deltaMs] of screen time - only while the tablet is open and a limit is set. */
    fun addUsage(s: Settings, deltaMs: Long, now: Long, tz: TimeZone = TimeZone.getDefault()): Settings =
        if (!s.limitOn || state(s, now, tz).locked || deltaMs <= 0) s else s.copy(usedMs = s.usedMs + deltaMs)

    /** The next time the clock shows [hour]:[minute], strictly after [now] (today if it is still ahead, otherwise tomorrow). */
    fun nextOccurrence(now: Long, hour: Int, minute: Int, tz: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = now
        c.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        c.set(Calendar.MINUTE, minute.coerceIn(0, 59))
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** "8:00 PM" for a time given as minutes after midnight. */
    fun minuteOfDayText(minutes: Int): String {
        val h = (minutes / 60) % 24
        val ap = if (h < 12) "AM" else "PM"
        return "${if (h % 12 == 0) 12 else h % 12}:${"%02d".format(minutes % 60)} $ap"
    }

    fun clockText(ms: Long, tz: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = tz }.format(ms)

    /** "1 h 5 min", "23 min", "under a minute". */
    fun durationText(ms: Long): String {
        val minutes = (ms / 60_000L).toInt()
        return when {
            minutes <= 0 -> "under a minute"
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0 -> "${minutes / 60} h"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
    }
}
