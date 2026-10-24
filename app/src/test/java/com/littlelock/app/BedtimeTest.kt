package com.littlelock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class BedtimeTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.MARCH, day, hour, minute) }.timeInMillis

    private val s = TimeLimit.Settings()          // defaults: bedtime on, 8 PM until 7 AM

    @Test
    fun the_defaults_are_8pm_until_7am_and_on() {
        assertTrue(s.bedOn)
        assertEquals(20 * 60, s.bedStartMin)
        assertEquals(7 * 60, s.bedEndMin)
        assertEquals(11 * 60, s.bedMinutes)
    }

    @Test
    fun open_in_the_afternoon_locked_from_8pm_and_through_the_night_until_7am() {
        assertFalse(TimeLimit.state(s, at(10, 15), utc).locked)
        assertFalse(TimeLimit.state(s, at(10, 19, 59), utc).locked)
        val evening = TimeLimit.state(s, at(10, 20), utc)
        assertTrue(evening.locked)
        assertEquals(TimeLimit.Why.BEDTIME, evening.why)
        assertEquals(at(11, 7), evening.bedEndsAt)
        val night = TimeLimit.state(s, at(11, 1), utc)                // after midnight: still last night's lockdown
        assertTrue(night.locked)
        assertEquals(at(11, 7), night.bedEndsAt)
        assertTrue(TimeLimit.state(s, at(11, 6, 59), utc).locked)
        assertFalse(TimeLimit.state(s, at(11, 7), utc).locked)
    }

    @Test
    fun the_start_and_the_morning_time_are_adjustable() {
        val late = s.copy(bedStartMin = 21 * 60 + 30, bedEndMin = 7 * 60 + 30)       // 9:30 PM until 7:30 AM
        assertFalse(TimeLimit.state(late, at(10, 21, 0), utc).locked)
        assertTrue(TimeLimit.state(late, at(10, 21, 30), utc).locked)
        assertTrue(TimeLimit.state(late, at(11, 7, 0), utc).locked)
        assertFalse(TimeLimit.state(late, at(11, 7, 30), utc).locked)
    }

    @Test
    fun a_lockdown_that_starts_in_the_morning_works_too() {
        val nap = s.copy(bedStartMin = 13 * 60, bedEndMin = 14 * 60 + 30)
        assertTrue(TimeLimit.state(nap, at(10, 14), utc).locked)
        assertFalse(TimeLimit.state(nap, at(10, 14, 30), utc).locked)
    }

    @Test
    fun switched_off_it_never_locks() {
        assertFalse(TimeLimit.state(s.copy(bedOn = false), at(10, 23), utc).locked)
        assertEquals(0L, TimeLimit.bedEnd(s.copy(bedOn = false), at(10, 23), utc))
    }

    @Test
    fun a_parents_extension_opens_the_tablet_during_bedtime_and_it_locks_again_after() {
        val ext = s.copy(extendedUntil = at(10, 21))
        assertFalse(TimeLimit.state(ext, at(10, 20, 30), utc).locked)
        assertTrue(TimeLimit.state(ext, at(10, 21, 1), utc).locked)
    }

    @Test
    fun time_is_not_counted_while_it_is_bedtime() {
        val limited = s.copy(limitOn = true)
        assertEquals(0L, TimeLimit.addUsage(limited, 5_000, at(10, 22), utc).usedMs)
        assertEquals(5_000L, TimeLimit.addUsage(limited, 5_000, at(10, 12), utc).usedMs)
    }

    @Test
    fun the_clock_reads_as_am_and_pm() {
        assertEquals("8:00 PM", TimeLimit.minuteOfDayText(20 * 60))
        assertEquals("12:05 AM", TimeLimit.minuteOfDayText(5))
        assertEquals("12:30 PM", TimeLimit.minuteOfDayText(12 * 60 + 30))
    }

    @Test
    fun a_parent_can_set_bedtime_from_discord() {
        val c = RemoteCommands.parse("bedtime 9pm to 6:30am")
        assertEquals(RemoteCommands.Command.Bedtime(true, 21 * 60, 6 * 60 + 30), c)
        val n = RemoteCommands.apply(s, c!!, at(10, 12), utc)
        assertEquals(21 * 60, n.settings.bedStartMin)
        assertEquals(6 * 60 + 30, n.settings.bedEndMin)
        assertTrue(n.reply.contains("9:00 PM to 6:30 AM"))
        assertEquals(RemoteCommands.Command.Bedtime(true, 20 * 60, 7 * 60), RemoteCommands.parse("bedtime 8pm-7am"))
        assertEquals(RemoteCommands.Command.Bedtime(true, null, 7 * 60 + 30), RemoteCommands.parse("bedtime ends 7:30am"))
        assertEquals(RemoteCommands.Command.Bedtime(true, 21 * 60, null), RemoteCommands.parse("bedtime 9pm"))
        assertEquals(RemoteCommands.Command.Bedtime(false, null, null), RemoteCommands.parse("bedtime off"))
        assertFalse(RemoteCommands.apply(s, RemoteCommands.parse("bedtime off")!!, at(10, 12), utc).settings.bedOn)
        // the older "8pm for 6 hours" still works: it ends 6 hours after it starts
        assertEquals(RemoteCommands.Command.Bedtime(true, 20 * 60, 2 * 60), RemoteCommands.parse("bedtime 8pm for 6 hours"))
        assertTrue(RemoteCommands.parse("bedtime whenever") is RemoteCommands.Command.Invalid)
        assertNotNull(RemoteCommands.parse("bedtime on"))
        // the same time twice is refused and nothing changes
        val same = RemoteCommands.apply(s, RemoteCommands.parse("bedtime 8pm to 8pm")!!, at(10, 12), utc)
        assertEquals(s, same.settings)
        assertTrue(same.reply.startsWith("🤔"))
    }

    @Test
    fun the_status_says_it_is_bedtime() {
        assertTrue(RemoteCommands.status(s, at(10, 22), utc).contains("Bedtime lockdown until 7:00 AM"))
    }

    @Test
    fun an_extension_never_pushes_the_morning_reset_later() {
        val ext = s.copy(extendedUntil = at(10, 23))            // opened until 11 PM
        assertTrue(TimeLimit.state(ext, at(10, 23, 30), utc).locked)
        assertEquals(at(11, 7), TimeLimit.state(ext, at(10, 23, 30), utc).bedEndsAt)
        assertFalse(TimeLimit.state(ext, at(11, 7, 1), utc).locked)
        val late = s.copy(extendedUntil = at(11, 1))             // extended again past midnight
        assertEquals(at(11, 7), TimeLimit.state(late, at(11, 1, 30), utc).bedEndsAt)
        assertFalse(TimeLimit.state(late, at(11, 7, 30), utc).locked)
        val farOut = s.copy(extendedUntil = at(11, 1))
        assertFalse(TimeLimit.state(farOut, at(11, 9), utc).locked)
    }

    @Test
    fun usage_can_be_asked_for_any_time() {
        assertEquals(RemoteCommands.Command.Usage, RemoteCommands.parse("usage"))
        assertEquals(RemoteCommands.Command.Usage, RemoteCommands.parse("!stats"))
        val r = RemoteCommands.apply(s.copy(usedMs = 90 * 60_000L), RemoteCommands.Command.Usage, at(10, 12), utc, 7).reply
        assertTrue(r.contains("Used today: 1 h 30 min"))
        assertTrue(r.contains("Apps opened today: 7"))
    }
}
