package com.littlelock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimeLimitTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private fun at(h: Int, m: Int = 0): Long = Calendar.getInstance(utc).apply { timeInMillis = 0; set(2026, Calendar.OCTOBER, 18, h, m, 0) }.timeInMillis
    private val min = 60_000L

    @Test
    fun nothing_is_locked_without_a_limit_or_a_lock() {
        assertFalse(TimeLimit.state(TimeLimit.Settings(), at(12)).locked)
    }

    @Test
    fun the_tablet_locks_when_the_daily_limit_is_used_up() {
        val s = TimeLimit.Settings(limitOn = true, limitMinutes = 60, usedMs = 59 * min)
        assertFalse(TimeLimit.state(s, at(12)).locked)
        assertEquals(1 * min, TimeLimit.state(s, at(12)).remainingMs)
        val st = TimeLimit.state(s.copy(usedMs = 60 * min), at(12))
        assertTrue(st.locked)
        assertEquals(TimeLimit.Why.LIMIT, st.why)
    }

    @Test
    fun lock_now_stays_until_extended_or_unlocked() {
        val s = TimeLimit.Settings(forceLock = true)
        assertTrue(TimeLimit.state(s, at(12)).locked)
        assertEquals(TimeLimit.Why.FORCED, TimeLimit.state(s, at(12)).why)
    }

    @Test
    fun an_extension_opens_everything_until_its_time_and_then_the_lock_comes_back() {
        val s = TimeLimit.Settings(limitOn = true, limitMinutes = 30, usedMs = 90 * min, forceLock = true, extendedUntil = at(20, 30))
        assertFalse(TimeLimit.state(s, at(19, 0)).locked)
        assertFalse(TimeLimit.state(s, at(20, 29)).locked)
        assertTrue(TimeLimit.state(s, at(20, 30)).locked)
        assertTrue(TimeLimit.state(s, at(21, 0)).locked)
    }

    @Test
    fun usage_counts_only_while_open_and_a_limit_is_set() {
        val open = TimeLimit.Settings(limitOn = true, limitMinutes = 60)
        assertEquals(5 * min, TimeLimit.addUsage(open, 5 * min, at(12)).usedMs)
        assertEquals(0L, TimeLimit.addUsage(TimeLimit.Settings(), 5 * min, at(12)).usedMs)
        assertEquals(60 * min, TimeLimit.addUsage(open.copy(usedMs = 60 * min), 5 * min, at(12)).usedMs)
    }

    @Test
    fun the_next_occurrence_is_today_when_still_ahead_and_tomorrow_otherwise() {
        assertEquals(at(20, 30), TimeLimit.nextOccurrence(at(19), 20, 30, utc))
        assertEquals(at(19) + 24 * 60 * min, TimeLimit.nextOccurrence(at(19), 19, 0, utc))
        assertEquals(at(7, 0) + 24 * 60 * min, TimeLimit.nextOccurrence(at(19), 7, 0, utc))
    }

    @Test
    fun clock_and_duration_read_naturally() {
        assertEquals("8:30 PM", TimeLimit.clockText(at(20, 30), utc))
        assertEquals("1 h 5 min", TimeLimit.durationText(65 * min))
        assertEquals("2 h", TimeLimit.durationText(120 * min))
        assertEquals("23 min", TimeLimit.durationText(23 * min))
        assertEquals("under a minute", TimeLimit.durationText(10_000))
    }
}

class RemoteCommandsTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val min = 60_000L
    private val now = Calendar.getInstance(utc).apply { timeInMillis = 0; set(2026, Calendar.OCTOBER, 18, 19, 0, 0) }.timeInMillis

    @Test
    fun commands_are_understood_in_the_ways_a_parent_would_type_them() {
        assertEquals(RemoteCommands.Command.Status, RemoteCommands.parse("status"))
        assertEquals(RemoteCommands.Command.Status, RemoteCommands.parse("!Status"))
        assertEquals(RemoteCommands.Command.Lock, RemoteCommands.parse("lock"))
        assertEquals(RemoteCommands.Command.Unlock, RemoteCommands.parse("littlelock unlock"))
        assertEquals(RemoteCommands.Command.ExtendMinutes(30), RemoteCommands.parse("extend 30"))
        assertEquals(RemoteCommands.Command.ExtendMinutes(30), RemoteCommands.parse("extend 30 minutes"))
        assertEquals(RemoteCommands.Command.ExtendMinutes(60), RemoteCommands.parse("extend 1h"))
        assertEquals(RemoteCommands.Command.ExtendMinutes(90), RemoteCommands.parse("extend 1.5 hours"))
        assertEquals(RemoteCommands.Command.ExtendUntil(20, 30), RemoteCommands.parse("extend until 8:30pm"))
        assertEquals(RemoteCommands.Command.ExtendUntil(20, 0), RemoteCommands.parse("extend to 8 pm"))
        assertEquals(RemoteCommands.Command.ExtendUntil(18, 15), RemoteCommands.parse("extend until 18:15"))
        assertEquals(RemoteCommands.Command.ExtendUntil(0, 0), RemoteCommands.parse("extend until 12am"))
        assertEquals(RemoteCommands.Command.ExtendUntil(20, 30), RemoteCommands.parse("extend 8:30pm"))
        assertEquals(RemoteCommands.Command.ExtendUntil(20, 0), RemoteCommands.parse("extend 8pm"))
        assertEquals(RemoteCommands.Command.ExtendUntil(20, 30), RemoteCommands.parse("extend 20:30"))
        assertEquals(RemoteCommands.Command.Limit(90), RemoteCommands.parse("limit 90"))
        assertEquals(RemoteCommands.Command.Limit(120), RemoteCommands.parse("limit 2h"))
        assertEquals(RemoteCommands.Command.Limit(null), RemoteCommands.parse("limit off"))
        assertEquals(RemoteCommands.Command.ResetUsed, RemoteCommands.parse("reset"))
    }

    @Test
    fun ordinary_chat_and_nonsense_are_not_commands() {
        assertNull(RemoteCommands.parse("what time is dinner?"))
        assertNull(RemoteCommands.parse(""))
        assertNull(RemoteCommands.parse("dinner is at 6"))
    }

    @Test
    fun a_command_with_something_wrong_after_it_is_answered_with_how_to_write_it() {
        for (bad in listOf("extend", "extend banana", "extend 5000", "limit 1", "extend until 25:99", "bedtime whenever", "!dance")) {
            val c = RemoteCommands.parse(bad)
            assertTrue("$bad -> $c", c is RemoteCommands.Command.Invalid)
            assertTrue(RemoteCommands.apply(TimeLimit.Settings(), c!!, now, utc).reply.startsWith("🤔"))
        }
    }

    @Test
    fun the_new_commands_are_understood() {
        assertEquals(RemoteCommands.Command.Lock, RemoteCommands.parse("lock now"))
        assertEquals(RemoteCommands.Command.ResetUsed, RemoteCommands.parse("reset time"))
        assertEquals(RemoteCommands.Command.ShowSettings, RemoteCommands.parse("settings"))
        assertEquals(RemoteCommands.Command.ExtendMinutes(90), RemoteCommands.parse("extend 1h 30m"))
        assertEquals(RemoteCommands.Command.SummaryNow, RemoteCommands.parse("summary now"))
        assertEquals(RemoteCommands.Command.SummaryAt(21 * 60), RemoteCommands.parse("summary at 9pm"))
        assertEquals(RemoteCommands.Command.DayStart(6 * 60), RemoteCommands.parse("new day at 6am"))
        assertEquals(RemoteCommands.Command.DayStart(0), RemoteCommands.parse("new day at midnight"))
        assertEquals(RemoteCommands.Command.DayStart(6 * 60 + 30), RemoteCommands.parse("day starts 6:30am"))
    }

    @Test
    fun the_summary_time_and_new_day_time_can_be_set_from_discord() {
        val s = TimeLimit.Settings()
        assertEquals(23 * 60 + 59, s.summaryMin)
        assertEquals(0, s.dayStartMin)
        val a = RemoteCommands.apply(s, RemoteCommands.parse("summary at 9pm")!!, now, utc)
        assertEquals(21 * 60, a.settings.summaryMin)
        assertTrue(a.reply.contains("9:00 PM"))
        val b = RemoteCommands.apply(s, RemoteCommands.parse("new day at 6am")!!, now, utc)
        assertEquals(6 * 60, b.settings.dayStartMin)
        assertEquals(RemoteCommands.Action.SEND_SUMMARY_NOW, RemoteCommands.apply(s, RemoteCommands.Command.SummaryNow, now, utc).action)
    }

    @Test
    fun help_and_settings_read_plainly() {
        assertTrue(RemoteCommands.help().contains("bedtime 8pm to 7am"))
        val t = RemoteCommands.settingsText(TimeLimit.Settings(limitOn = true, limitMinutes = 90))
        assertTrue(t.contains("Daily limit: 1 h 30 min"))
        assertTrue(t.contains("Bedtime lockdown: 8:00 PM to 7:00 AM"))
        assertTrue(t.contains("Daily summary sent at: 11:59 PM"))
    }

    @Test
    fun extending_opens_the_tablet_until_the_time_and_says_so() {
        val locked = TimeLimit.Settings(limitOn = true, limitMinutes = 60, usedMs = 70 * min)
        val r = RemoteCommands.apply(locked, RemoteCommands.Command.ExtendUntil(20, 30), now, utc)
        assertFalse(TimeLimit.state(r.settings, now).locked)
        assertTrue(r.reply.contains("8:30 PM"))
        val r2 = RemoteCommands.apply(locked, RemoteCommands.Command.ExtendMinutes(45), now, utc)
        assertEquals(now + 45 * min, r2.settings.extendedUntil)
        assertTrue(r2.reply.contains("7:45 PM"))
    }

    @Test
    fun lock_locks_and_cancels_an_extension() {
        val open = TimeLimit.Settings(extendedUntil = now + 100 * min)
        val r = RemoteCommands.apply(open, RemoteCommands.Command.Lock, now, utc)
        assertTrue(TimeLimit.state(r.settings, now).locked)
    }

    @Test
    fun unlock_says_when_todays_limit_still_keeps_it_locked() {
        val s = TimeLimit.Settings(limitOn = true, limitMinutes = 30, usedMs = 40 * min, forceLock = true)
        val r = RemoteCommands.apply(s, RemoteCommands.Command.Unlock, now, utc)
        assertTrue(TimeLimit.state(r.settings, now).locked)
        assertTrue(r.reply.contains("still locked"))
    }

    @Test
    fun limit_and_reset_change_the_settings() {
        val s = TimeLimit.Settings(limitOn = false, usedMs = 50 * min)
        val l = RemoteCommands.apply(s, RemoteCommands.Command.Limit(90), now, utc)
        assertTrue(l.settings.limitOn)
        assertEquals(90, l.settings.limitMinutes)
        assertEquals(0L, RemoteCommands.apply(s, RemoteCommands.Command.ResetUsed, now, utc).settings.usedMs)
        assertFalse(RemoteCommands.apply(l.settings, RemoteCommands.Command.Limit(null), now, utc).settings.limitOn)
    }

    @Test
    fun status_reads_clearly_in_each_state() {
        assertTrue(RemoteCommands.status(TimeLimit.Settings(limitOn = true, limitMinutes = 60, usedMs = 37 * min), now, utc).contains("23 min left"))
        assertTrue(RemoteCommands.status(TimeLimit.Settings(forceLock = true), now, utc).startsWith("🔒"))
        assertTrue(RemoteCommands.status(TimeLimit.Settings(extendedUntil = now + 30 * min), now, utc).contains("7:30 PM"))
    }
}
