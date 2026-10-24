package com.littlelock.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** day ("yyyyMMdd") -> package -> what it did that day */
typealias UsageData = Map<String, Map<String, UsageLog.Stat>>

/**
 * What the child used, app by app, day by day: the time each app was in front and how many times it was opened.
 * It is kept on the tablet (so a day with no internet is still complete) and the last [KEEP_DAYS] days are remembered.
 * Plain numbers and text in, plain numbers and text out, so every rule can be tested without a tablet.
 *
 * Stored as one line per app and day:  yyyyMMdd|package|milliseconds|opens
 */
object UsageLog {

    data class Stat(val ms: Long = 0L, val opens: Int = 0)

    const val KEEP_DAYS = 14

    fun dayKey(now: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))

    fun parse(text: String?): UsageData {
        val out = LinkedHashMap<String, MutableMap<String, Stat>>()
        for (line in text.orEmpty().lineSequence()) {
            val p = line.split('|')
            if (p.size != 4) continue
            val ms = p[2].toLongOrNull() ?: continue
            val opens = p[3].toIntOrNull() ?: continue
            out.getOrPut(p[0]) { LinkedHashMap() }[p[1]] = Stat(ms, opens)
        }
        return out
    }

    fun serialize(data: UsageData): String =
        data.entries.joinToString("\n") { (day, apps) -> apps.entries.joinToString("\n") { (pkg, s) -> "$day|$pkg|${s.ms}|${s.opens}" } }
            .lines().filter { it.isNotEmpty() }.joinToString("\n")

    /** Adds [ms] of use and [opens] openings of [pkg] to [day]; days older than [KEEP_DAYS] fall away. */
    fun add(data: UsageData, day: String, pkg: String, ms: Long, opens: Int): UsageData {
        if (pkg.isEmpty() || pkg.contains('|') || pkg.contains('\n') || (ms <= 0L && opens <= 0)) return data
        val out = LinkedHashMap<String, Map<String, Stat>>(data)
        val apps = LinkedHashMap(out[day] ?: emptyMap())
        val old = apps[pkg] ?: Stat()
        apps[pkg] = Stat(old.ms + ms.coerceAtLeast(0L), old.opens + opens.coerceAtLeast(0))
        out[day] = apps
        val keep = out.keys.sortedDescending().take(KEEP_DAYS).toSet()
        out.keys.retainAll(keep)
        return out
    }

    /** The apps used on [day], the longest used first. */
    fun apps(data: UsageData, day: String): List<Pair<String, Stat>> =
        (data[day] ?: emptyMap()).entries.map { it.key to it.value }
            .sortedWith(compareByDescending<Pair<String, Stat>> { it.second.ms }.thenByDescending { it.second.opens }.thenBy { it.first })

    fun totalMs(data: UsageData, day: String): Long = (data[day] ?: emptyMap()).values.sumOf { it.ms }

    fun totalOpens(data: UsageData, day: String): Int = (data[day] ?: emptyMap()).values.sumOf { it.opens }

    /** "Friday, Oct 23" for a day key; the key itself when it can't be read. */
    fun dayName(day: String): String = try {
        SimpleDateFormat("EEEE, MMM d", Locale.US).format(SimpleDateFormat("yyyyMMdd", Locale.US).parse(day)!!)
    } catch (e: Exception) {
        day
    }

    /** "1 h 5 min", or "under a minute" - for a time an app was in front. */
    private fun time(ms: Long): String = TimeLimit.durationText(ms)

    /** One line for an app: "**YouTube Kids** - 1 h 5 min, opened 3 times". */
    fun line(name: String, s: Stat, bold: Boolean = true): String {
        val n = if (bold) "**$name**" else name
        val opened = when (s.opens) {
            0 -> ""
            1 -> ", opened once"
            else -> ", opened ${s.opens} times"
        }
        return "$n - ${time(s.ms)}$opened"
    }

    /**
     * The itemised list for [day] ("" when nothing was used). [label] turns a package name into the app's name.
     * At most [max] apps are listed; the rest are rolled into one line so the message stays short.
     */
    fun itemised(data: UsageData, day: String, label: (String) -> String, max: Int = 20, bold: Boolean = true): String {
        val all = apps(data, day)
        if (all.isEmpty()) return ""
        val shown = all.take(max).joinToString("\n") { "• " + line(label(it.first), it.second, bold) }
        val rest = all.drop(max)
        return if (rest.isEmpty()) shown else shown + "\n• and ${rest.size} more (${time(rest.sumOf { it.second.ms })})"
    }

    /** The headline above the list: "3 apps, 1 h 40 min in all, 9 openings". */
    fun headline(data: UsageData, day: String): String {
        val n = data[day]?.size ?: 0
        if (n == 0) return "No app use was recorded."
        val opens = totalOpens(data, day)
        return "${if (n == 1) "1 app" else "$n apps"} used, ${time(totalMs(data, day))} in all" +
            if (opens > 0) ", ${if (opens == 1) "opened once" else "opened $opens times"}" else ""
    }
}
