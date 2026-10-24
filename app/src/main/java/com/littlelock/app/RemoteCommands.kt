package com.littlelock.app

import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * The words a parent can send from their phone (through the optional Discord remote) to control the tablet, and what each one does.
 * Examples:  status   usage   lock now   unlock   extend 30   extend 1h   extend until 8:30pm   limit 90   limit off
 *            bedtime 8pm to 7am   bedtime off   reset time   summary at 9pm   summary now   new day at 6am   settings   help
 * A word that is a command but has something wrong after it is answered with how to write it; ordinary chat is ignored.
 */
object RemoteCommands {

    sealed class Command {
        object Status : Command()
        object Lock : Command()
        object Unlock : Command()
        object ResetUsed : Command()
        object Help : Command()
        object Usage : Command()
        object ShowSettings : Command()
        object SummaryNow : Command()
        data class ExtendMinutes(val minutes: Int) : Command()
        data class ExtendUntil(val hour: Int, val minute: Int) : Command()
        data class Limit(val minutes: Int?) : Command()      // null = no daily limit
        /** [startMin] / [endMin] are minutes after midnight; null keeps what is set. */
        data class Bedtime(val on: Boolean, val startMin: Int?, val endMin: Int?) : Command()
        data class SummaryAt(val minuteOfDay: Int) : Command()
        data class DayStart(val minuteOfDay: Int) : Command()
        /** A command word with something wrong after it: [hint] shows how to write it. */
        data class Invalid(val hint: String) : Command()
    }

    /** Something to do besides changing the settings. */
    enum class Action { SEND_SUMMARY_NOW }

    class Result(val settings: TimeLimit.Settings, val reply: String, val action: Action? = null)

    /** Same groups as [TIME] (the last two stay empty) so [clock] reads both. */
    private val CLOCK_WORD = Regex("""(?:until|till|to|at|by)\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?()()""")
    /** A time of day: "8pm", "8:30 pm" or "20:30" (a bare number is not a time). */
    private val TIME = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)|(\d{1,2}):(\d{2})()""")
    private val HOURS = Regex("""(\d+(?:\.\d+)?)\s*(?:h|hr|hrs|hour|hours)\b""")
    private val MINUTES_UNIT = Regex("""(\d+)\s*(?:m|min|mins|minute|minutes)\b""")
    private val BARE_NUMBER = Regex("""^\d+$""")

    private const val EXTEND_HINT = "Try  extend 30  ·  extend 1h  ·  extend until 8:30pm"
    private const val LIMIT_HINT = "Try  limit 90  ·  limit 2h  ·  limit off  (5 minutes to 12 hours)"
    private const val BED_HINT = "Try  bedtime 8pm to 7am  ·  bedtime off  ·  bedtime on"
    private const val SUMMARY_HINT = "Try  summary at 9pm  ·  summary now"
    private const val DAY_HINT = "Try  new day at 6am  (when used time and app use start over; midnight is the usual)"

    /** What the message asks for, or null when it is not a command (so ordinary chat in the channel is ignored). */
    fun parse(raw: String): Command? {
        var t = raw.trim().lowercase()
        val prefixed = t.startsWith("!") || t.startsWith("/")
        t = t.removePrefix("!").removePrefix("/").trim()
        for (p in listOf("littlelock", "little lock", "ll")) if (t.startsWith("$p ")) { t = t.removePrefix(p).trim() }
        if (t.isEmpty()) return null
        val word = t.substringBefore(' ')
        val rest = t.substringAfter(' ', "").trim()
        return when (word) {
            "status", "time", "state" -> Command.Status
            "usage", "stats", "stat", "report", "apps" -> Command.Usage
            "settings", "config", "setup" -> Command.ShowSettings
            "help", "commands", "?" -> Command.Help
            "lock" -> if (rest.isEmpty() || rest in listOf("now", "tablet", "it")) Command.Lock else Command.Invalid("Send  lock now")
            "unlock" -> Command.Unlock
            "reset" -> if (rest.isEmpty() || rest in listOf("time", "used", "today", "timer", "time used")) Command.ResetUsed
                       else Command.Invalid("Send  reset time  to start today's used time over")
            "extend", "more", "add", "give" -> extend(rest) ?: Command.Invalid(EXTEND_HINT)
            "limit", "daily" -> limit(rest) ?: Command.Invalid(LIMIT_HINT)
            "bedtime", "bed", "night" -> bedtime(rest) ?: Command.Invalid(BED_HINT)
            "summary" -> summary(rest) ?: Command.Invalid(SUMMARY_HINT)
            "newday", "dayreset", "rollover" -> dayStart(rest) ?: Command.Invalid(DAY_HINT)
            "new" -> if (rest.startsWith("day")) dayStart(rest.removePrefix("day").trim()) ?: Command.Invalid(DAY_HINT) else unknown(word, prefixed)
            "day" -> if (rest.startsWith("start") || rest.startsWith("reset")) dayStart(rest.substringAfter(' ', "").trim()) ?: Command.Invalid(DAY_HINT) else unknown(word, prefixed)
            else -> unknown(word, prefixed)
        }
    }

    /** An unknown first word is only answered when it was clearly meant as a command (a "!" in front). */
    private fun unknown(word: String, prefixed: Boolean): Command? = if (prefixed) Command.Invalid("I don't know \"$word\". Send  help  for the list.") else null

    private fun extend(rest: String): Command? {
        // "until 8:30pm", or a bare clock time ("8:30pm", "8pm", "20:30") - until then, not that many minutes
        val m = CLOCK_WORD.find(rest) ?: TIME.find(rest)
        if (m != null) return clock(m)?.let { Command.ExtendUntil(it / 60, it % 60) }
        val minutes = minutesIn(rest) ?: return null
        return if (minutes in 1..MAX_EXTEND_MINUTES) Command.ExtendMinutes(minutes) else null
    }

    /** Minutes after midnight for a matched clock time, or null when it is not a real time. */
    private fun clock(m: MatchResult): Int? {
        var h = (m.groupValues[1].ifEmpty { m.groupValues[4] }).toIntOrNull() ?: return null
        val min = (m.groupValues[2].ifEmpty { m.groupValues[5] }.ifEmpty { "0" }).toIntOrNull() ?: return null
        val ap = m.groupValues[3]
        if (ap.isNotEmpty() && h !in 1..12) return null
        if (ap == "pm" && h < 12) h += 12
        if (ap == "am" && h == 12) h = 0
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    private fun limit(rest: String): Command? {
        if (rest in listOf("off", "none", "no", "0")) return Command.Limit(null)
        val minutes = minutesIn(rest) ?: return null
        return if (minutes in TimeLimit.MIN_LIMIT..TimeLimit.MAX_LIMIT) Command.Limit(minutes) else null
    }

    /**
     * "bedtime off", "bedtime on", "bedtime 8pm to 7am", "bedtime 8pm-7am", "bedtime start 9pm", "bedtime ends 7:30am".
     * (The older "bedtime 8pm 6h" still works: it ends 6 hours after it starts.)
     */
    private fun bedtime(rest: String): Command? {
        if (rest in listOf("off", "none", "no")) return Command.Bedtime(false, null, null)
        if (rest in listOf("on", "yes")) return Command.Bedtime(true, null, null)
        val matches = TIME.findAll(rest).toList()
        val times = matches.map { clock(it) ?: return null }
        return when (times.size) {
            1 -> {
                val before = rest.substring(0, matches[0].range.first)
                val onlyEnd = Regex("""\b(end|ends|wake|wakes|until|till|morning|open|opens)\b""").containsMatchIn(before)
                val extra = rest.removeRange(matches[0].range).replace("for", "").trim()
                val len = if (extra.isEmpty() || onlyEnd) null else minutesIn(extra) ?: return null
                when {
                    onlyEnd -> Command.Bedtime(true, null, times[0])
                    len != null -> if (len in TimeLimit.MIN_BED..TimeLimit.MAX_BED) Command.Bedtime(true, times[0], (times[0] + len) % (24 * 60)) else null
                    else -> Command.Bedtime(true, times[0], null)
                }
            }
            2 -> Command.Bedtime(true, times[0], times[1])
            else -> null
        }
    }

    private fun summary(rest: String): Command? {
        if (rest in listOf("now", "today", "send", "send now", "")) return Command.SummaryNow
        val m = TIME.find(rest) ?: return null
        return clock(m)?.let { Command.SummaryAt(it) }
    }

    private fun dayStart(rest: String): Command? {
        val r = rest.removePrefix("at").removePrefix("to").trim()
        if (r == "midnight") return Command.DayStart(0)
        val m = TIME.find(r) ?: return null
        return clock(m)?.let { Command.DayStart(it) }
    }

    /** "1h 30m", "90", "1.5 hours", "45 min" -> minutes. A bare number means minutes. */
    private fun minutesIn(text: String): Int? {
        val t = text.trim()
        val h = HOURS.find(t)
        val m = MINUTES_UNIT.find(t)
        if (h != null || m != null) {
            return ((h?.groupValues?.get(1)?.toDouble() ?: 0.0) * 60).roundToInt() + (m?.groupValues?.get(1)?.toIntOrNull() ?: 0)
        }
        return if (BARE_NUMBER.matches(t)) t.toIntOrNull() else null
    }

    const val MAX_EXTEND_MINUTES = 12 * 60

    fun help(): String = """**Commands** (type them in this channel)
**Info** - status (open or locked, time left) · usage (every app used today) · settings (what is set now)
**Lock** - lock now · unlock
**Extend playtime** - extend 30 · extend 1h · extend until 8:30pm
**Daily limit** - limit 90 · limit 2h · limit off
**Bedtime** - bedtime 8pm to 7am · bedtime off · bedtime on
**Start over** - reset time (today's used time back to 0)
**Daily summary** - summary now · summary at 9pm
**New day** - new day at 6am (when used time and app use start over)"""

    /** Applies [c] to the screen-time [s] at time [now]; returns the new settings and the answer to send back. */
    fun apply(s: TimeLimit.Settings, c: Command, now: Long, tz: TimeZone = TimeZone.getDefault(), launches: Int = 0, apps: String = ""): Result = when (c) {
        Command.Help -> Result(s, help())
        Command.Status -> Result(s, status(s, now, tz))
        Command.Usage -> Result(s, usage(s, now, tz, launches, apps))
        Command.ShowSettings -> Result(s, settingsText(s))
        is Command.Invalid -> Result(s, "🤔 ${c.hint}")
        Command.SummaryNow -> Result(s, "📊 Sending today's summary so far...", Action.SEND_SUMMARY_NOW)
        Command.Lock -> {
            val n = s.copy(forceLock = true, extendedUntil = 0L)
            Result(n, "🔒 Locked now. Nothing can be opened until you send  unlock  or  extend 30.")
        }
        Command.Unlock -> {
            val n = s.copy(forceLock = false, extendedUntil = 0L)
            val st = TimeLimit.state(n, now, tz)
            Result(n, when {
                st.locked && st.why == TimeLimit.Why.BEDTIME -> "🔓 The manual lock is off, but it is bedtime - the tablet is still locked until ${TimeLimit.clockText(st.bedEndsAt, tz)}. Send  extend 30  to open it for a while."
                st.locked -> "🔓 The manual lock is off, but today's limit is used up - the tablet is still locked. Send  extend 30  to open it for a while."
                else -> "🔓 Unlocked. ${remaining(n, st)}"
            })
        }
        Command.ResetUsed -> Result(s.copy(usedMs = 0L), "↺ Today's used time is back to 0 min.")
        is Command.Limit -> {
            val n = if (c.minutes == null) s.copy(limitOn = false) else s.copy(limitOn = true, limitMinutes = c.minutes)
            Result(n, if (c.minutes == null) "📏 No daily limit now. Used so far today: ${TimeLimit.durationText(s.usedMs)}." else "📏 Daily limit set to ${TimeLimit.durationText(c.minutes * 60_000L)}. ${remaining(n, TimeLimit.state(n, now, tz))}")
        }
        is Command.Bedtime -> {
            val start = c.startMin ?: s.bedStartMin
            val end = c.endMin ?: s.bedEndMin
            val n = s.copy(bedOn = c.on, bedStartMin = start, bedEndMin = end)
            when {
                !c.on -> Result(n, "🌙 Bedtime lockdown is off.")
                start == end -> Result(s, "🤔 Bedtime can't start and end at the same time. $BED_HINT")
                n.bedMinutes < TimeLimit.MIN_BED -> Result(s, "🤔 Bedtime has to last at least ${TimeLimit.MIN_BED} minutes. $BED_HINT")
                else -> Result(n, "🌙 Bedtime lockdown every night: ${TimeLimit.minuteOfDayText(start)} to ${TimeLimit.minuteOfDayText(end)} (${TimeLimit.durationText(n.bedMinutes * 60_000L)}).")
            }
        }
        is Command.SummaryAt -> Result(s.copy(summaryMin = c.minuteOfDay), "📊 The daily summary will be sent at ${TimeLimit.minuteOfDayText(c.minuteOfDay)} each day.")
        is Command.DayStart -> Result(s.copy(dayStartMin = c.minuteOfDay),
            "🔁 A new day (used time and app use start over) begins at ${TimeLimit.minuteOfDayText(c.minuteOfDay)}${if (c.minuteOfDay == 0) " - midnight" else ""}.")
        is Command.ExtendMinutes -> {
            val until = now + c.minutes * 60_000L
            Result(s.copy(extendedUntil = until), "⏰ Playtime extended by ${TimeLimit.durationText(c.minutes * 60_000L)} - the tablet is open until ${TimeLimit.clockText(until, tz)}, then it locks again.")
        }
        is Command.ExtendUntil -> {
            val until = TimeLimit.nextOccurrence(now, c.hour, c.minute, tz)
            Result(s.copy(extendedUntil = until), "⏰ Playtime extended - the tablet is open until ${TimeLimit.clockText(until, tz)}, then it locks again.")
        }
    }

    private fun remaining(s: TimeLimit.Settings, st: TimeLimit.State): String =
        if (!s.limitOn) "No daily limit." else "${TimeLimit.durationText(st.remainingMs)} left today."

    /** What is set right now, in plain words. */
    fun settingsText(s: TimeLimit.Settings): String = listOf(
        "⚙️ **Current settings**",
        "Daily limit: " + if (s.limitOn) TimeLimit.durationText(s.limitMinutes * 60_000L) else "none",
        "Bedtime lockdown: " + if (s.bedOn) "${TimeLimit.minuteOfDayText(s.bedStartMin)} to ${TimeLimit.minuteOfDayText(s.bedEndMin)}" else "off",
        "Daily summary sent at: ${TimeLimit.minuteOfDayText(s.summaryMin)}",
        "A new day starts at: ${TimeLimit.minuteOfDayText(s.dayStartMin)}"
    ).joinToString("\n")

    /** The usage numbers right now, on request (the scheduled update does the same on a timer). */
    fun usage(s: TimeLimit.Settings, now: Long, tz: TimeZone = TimeZone.getDefault(), launches: Int = 0, apps: String = ""): String {
        val st = TimeLimit.state(s, now, tz)
        val left = if (s.limitOn) " ${TimeLimit.durationText(st.remainingMs)} left of ${TimeLimit.durationText(s.limitMinutes * 60_000L)}." else " No daily limit."
        return "📊 Used today: ${TimeLimit.durationText(s.usedMs)}.$left Apps opened today: $launches.\n${status(s, now, tz)}" + if (apps.isEmpty()) "" else "\n$apps"
    }

    fun status(s: TimeLimit.Settings, now: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val st = TimeLimit.state(s, now, tz)
        return when {
            st.locked && st.why == TimeLimit.Why.FORCED -> "🔒 Locked (you locked it). Used today: ${TimeLimit.durationText(s.usedMs)}. Send  unlock  to open it."
            st.locked && st.why == TimeLimit.Why.BEDTIME -> "🌙 Bedtime lockdown until ${TimeLimit.clockText(st.bedEndsAt, tz)}. Send  extend 30  to open it for a while."
            st.locked -> "🔒 Locked - today's ${TimeLimit.durationText(s.limitMinutes * 60_000L)} limit is used up. Send  extend 30  to open it for a while."
            st.extendedUntil > now -> "🔓 Open until ${TimeLimit.clockText(st.extendedUntil, tz)} (extended). Used today: ${TimeLimit.durationText(s.usedMs)}."
            s.limitOn -> "🔓 Open. ${TimeLimit.durationText(st.remainingMs)} left of ${TimeLimit.durationText(s.limitMinutes * 60_000L)} today."
            else -> "🔓 Open, no daily limit. Used today: ${TimeLimit.durationText(s.usedMs)}."
        }
    }
}
