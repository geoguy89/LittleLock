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
 *  - daily limit: when the minutes used today reach the limit the tablet locks (and stays locked until tomorrow or an extension)
 */
object TimeLimit {

    enum class Why { OPEN, LIMIT, FORCED }

    data class Settings(
        val limitOn: Boolean = false,
        val limitMinutes: Int = 60,
        val usedMs: Long = 0L,
        val forceLock: Boolean = false,
        val extendedUntil: Long = 0L
    )

    class State(val locked: Boolean, val why: Why, val remainingMs: Long, val extendedUntil: Long)

    const val MIN_LIMIT = 5
    const val MAX_LIMIT = 720

    fun state(s: Settings, now: Long): State {
        val limitMs = s.limitMinutes * 60_000L
        if (s.extendedUntil > now) {
            return State(false, Why.OPEN, if (s.limitOn) (limitMs - s.usedMs).coerceAtLeast(0) else Long.MAX_VALUE, s.extendedUntil)
        }
        if (s.forceLock) return State(true, Why.FORCED, 0L, s.extendedUntil)
        if (s.limitOn && s.usedMs >= limitMs) return State(true, Why.LIMIT, 0L, s.extendedUntil)
        return State(false, Why.OPEN, if (s.limitOn) limitMs - s.usedMs else Long.MAX_VALUE, s.extendedUntil)
    }

    /** Count [deltaMs] of screen time - only while the tablet is open and a limit is set. */
    fun addUsage(s: Settings, deltaMs: Long, now: Long): Settings =
        if (!s.limitOn || state(s, now).locked || deltaMs <= 0) s else s.copy(usedMs = s.usedMs + deltaMs)

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
