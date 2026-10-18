package com.littlelock.app

import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * The words a parent can send from their phone (through the optional Discord remote) to control screen time, and what each one does.
 * Examples:  status   lock   unlock   extend 30   extend 1h   extend until 8:30pm   limit 90   limit off   reset
 */
object RemoteCommands {

    sealed class Command {
        object Status : Command()
        object Lock : Command()
        object Unlock : Command()
        object ResetUsed : Command()
        object Help : Command()
        object Usage : Command()
        data class ExtendMinutes(val minutes: Int) : Command()
        data class ExtendUntil(val hour: Int, val minute: Int) : Command()
        data class Limit(val minutes: Int?) : Command()      // null = no daily limit
        data class Bedtime(val on: Boolean, val startMin: Int?, val minutes: Int?) : Command()
    }

    class Result(val settings: TimeLimit.Settings, val reply: String)

    private val CLOCK = Regex("""(?:until|till|to|at|by)\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")
    private val BARE_AMPM = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)""")
    private val BARE_24 = Regex("""(\d{1,2}):(\d{2})()""")
    private val HOURS = Regex("""(\d+(?:\.\d+)?)\s*(?:h|hr|hrs|hour|hours)\b""")
    private val MINUTES = Regex("""(\d+)\s*(?:m|min|mins|minute|minutes)?\b""")

    /** What the message asks for, or null when it is not a command (so ordinary chat in the channel is ignored). */
    fun parse(raw: String): Command? {
        var t = raw.trim().lowercase()
        t = t.removePrefix("!").removePrefix("/").trim()
        for (p in listOf("littlelock", "little lock", "ll")) if (t.startsWith("$p ")) t = t.removePrefix(p).trim()
        if (t.isEmpty()) return null
        val word = t.substringBefore(' ')
        val rest = t.substringAfter(' ', "").trim()
        return when (word) {
            "status", "time", "state" -> Command.Status
            "usage", "stats", "stat", "report", "summary" -> Command.Usage
            "help", "commands" -> Command.Help
            "lock" -> Command.Lock
            "unlock" -> Command.Unlock
            "reset" -> Command.ResetUsed
            "extend", "more", "add" -> extend(rest)
            "limit" -> limit(rest)
            "bedtime", "bed", "night" -> bedtime(rest)
            else -> null
        }
    }

    private fun extend(rest: String): Command? {
        // "until 8:30pm", or a bare clock time ("8:30pm", "8pm", "20:30") - until then, not that many minutes
        val m = CLOCK.find(rest) ?: BARE_AMPM.matchEntire(rest.trim()) ?: BARE_24.matchEntire(rest.trim())
        if (m != null) return clock(m)
        val minutes = minutesIn(rest) ?: return null
        return if (minutes in 1..MAX_EXTEND_MINUTES) Command.ExtendMinutes(minutes) else null
    }

    private fun clock(m: MatchResult): Command? {
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        val ap = m.groupValues[3]
        if (ap == "pm" && h < 12) h += 12
        if (ap == "am" && h == 12) h = 0
        return if (h in 0..23 && min in 0..59) Command.ExtendUntil(h, min) else null
    }

    private fun limit(rest: String): Command? {
        if (rest in listOf("off", "none", "no", "0")) return Command.Limit(null)
        val minutes = minutesIn(rest) ?: return null
        return if (minutes in TimeLimit.MIN_LIMIT..TimeLimit.MAX_LIMIT) Command.Limit(minutes) else null
    }

    /** "bedtime off", "bedtime on", "bedtime 9pm", "bedtime 9pm 8h", "bedtime 8pm for 6 hours". */
    private fun bedtime(rest: String): Command? {
        if (rest in listOf("off", "none", "no")) return Command.Bedtime(false, null, null)
        if (rest in listOf("on", "yes")) return Command.Bedtime(true, null, null)
        val found = BARE_AMPM.find(rest) ?: BARE_24.find(rest) ?: return null
        val start = (clock(found) as? Command.ExtendUntil)?.let { it.hour * 60 + it.minute } ?: return null
        val after = rest.removeRange(found.range).replace("for", "").trim()
        val len = if (after.isEmpty()) null else minutesIn(after) ?: return null
        if (len != null && len !in TimeLimit.MIN_BED..TimeLimit.MAX_BED) return null
        return Command.Bedtime(true, start, len)
    }

    private fun minutesIn(text: String): Int? {
        HOURS.find(text)?.let { return (it.groupValues[1].toDouble() * 60).roundToInt() }
        return MINUTES.find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    const val MAX_EXTEND_MINUTES = 12 * 60

    fun help(): String = "Commands: status · usage · lock · unlock · extend 30 · extend 1h · extend until 8:30pm · limit 90 · limit off · bedtime 8pm 6h · bedtime off · reset"

    /** Applies [c] to the screen-time [s] at time [now]; returns the new settings and the answer to send back. */
    fun apply(s: TimeLimit.Settings, c: Command, now: Long, tz: TimeZone = TimeZone.getDefault(), launches: Int = 0): Result = when (c) {
        Command.Help -> Result(s, help())
        Command.Status -> Result(s, status(s, now, tz))
        Command.Usage -> Result(s, usage(s, now, tz, launches))
        Command.Lock -> {
            val n = s.copy(forceLock = true, extendedUntil = 0L)
            Result(n, "🔒 Locked now. It stays locked until you extend or unlock it.")
        }
        Command.Unlock -> {
            val n = s.copy(forceLock = false, extendedUntil = 0L)
            val st = TimeLimit.state(n, now, tz)
            Result(n, if (st.locked) "🔓 The lock-now is off, but today's limit is used up - the tablet is still locked. Send  extend 30  to open it for a while."
            else "🔓 Unlocked. ${remaining(n, st)}")
        }
        Command.ResetUsed -> Result(s.copy(usedMs = 0L), "↺ Today's used time is back to 0.")
        is Command.Limit -> {
            val n = if (c.minutes == null) s.copy(limitOn = false) else s.copy(limitOn = true, limitMinutes = c.minutes)
            Result(n, if (c.minutes == null) "No daily limit now." else "Daily limit: ${TimeLimit.durationText(c.minutes * 60_000L)}. ${remaining(n, TimeLimit.state(n, now, tz))}")
        }
        is Command.Bedtime -> {
            val n = s.copy(bedOn = c.on, bedStartMin = c.startMin ?: s.bedStartMin, bedMinutes = c.minutes ?: s.bedMinutes)
            Result(n, if (!c.on) "Bedtime lockdown is off." else "🌙 Bedtime lockdown: ${TimeLimit.minuteOfDayText(n.bedStartMin)} for ${TimeLimit.durationText(n.bedMinutes * 60_000L)} every night.")
        }
        is Command.ExtendMinutes -> {
            val until = now + c.minutes * 60_000L
            Result(s.copy(extendedUntil = until), "⏰ Open until ${TimeLimit.clockText(until, tz)} (${TimeLimit.durationText(c.minutes * 60_000L)}), then it locks again.")
        }
        is Command.ExtendUntil -> {
            val until = TimeLimit.nextOccurrence(now, c.hour, c.minute, tz)
            Result(s.copy(extendedUntil = until), "⏰ Open until ${TimeLimit.clockText(until, tz)}, then it locks again.")
        }
    }

    private fun remaining(s: TimeLimit.Settings, st: TimeLimit.State): String =
        if (!s.limitOn) "No daily limit." else "${TimeLimit.durationText(st.remainingMs)} left today."

    /** The usage numbers right now, on request (the scheduled update does the same on a timer). */
    fun usage(s: TimeLimit.Settings, now: Long, tz: TimeZone = TimeZone.getDefault(), launches: Int = 0): String {
        val st = TimeLimit.state(s, now, tz)
        val left = if (s.limitOn) " ${TimeLimit.durationText(st.remainingMs)} left of ${TimeLimit.durationText(s.limitMinutes * 60_000L)}." else " No daily limit."
        return "📊 Used today: ${TimeLimit.durationText(s.usedMs)}.$left Apps opened today: $launches.\n${status(s, now, tz)}"
    }

    fun status(s: TimeLimit.Settings, now: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val st = TimeLimit.state(s, now, tz)
        return when {
            st.locked && st.why == TimeLimit.Why.FORCED -> "🔒 Locked (you locked it). Used today: ${TimeLimit.durationText(s.usedMs)}."
            st.locked && st.why == TimeLimit.Why.BEDTIME -> "🌙 Bedtime lockdown until ${TimeLimit.clockText(st.bedEndsAt, tz)}. Send  extend 30  to open it for a while."
            st.locked -> "🔒 Locked - today's ${TimeLimit.durationText(s.limitMinutes * 60_000L)} limit is used up."
            st.extendedUntil > now -> "🔓 Open until ${TimeLimit.clockText(st.extendedUntil, tz)}. Used today: ${TimeLimit.durationText(s.usedMs)}."
            s.limitOn -> "🔓 Open. ${TimeLimit.durationText(st.remainingMs)} left of ${TimeLimit.durationText(s.limitMinutes * 60_000L)} today."
            else -> "🔓 Open, no daily limit. Used today: ${TimeLimit.durationText(s.usedMs)}."
        }
    }
}
