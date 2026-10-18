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

    private val s = TimeLimit.Settings()          // defaults: bedtime on, 8 PM for 6 hours

    @Test
    fun the_defaults_are_8pm_for_6_hours_and_on() {
        assertTrue(s.bedOn)
        assertEquals(20 * 60, s.bedStartMin)
        assertEquals(6 * 60, s.bedMinutes)
    }

    @Test
    fun open_in_the_afternoon_locked_from_8pm_and_through_the_night_until_2am() {
        assertFalse(TimeLimit.state(s, at(10, 15), utc).locked)
        assertFalse(TimeLimit.state(s, at(10, 19, 59), utc).locked)
        val evening = TimeLimit.state(s, at(10, 20), utc)
        assertTrue(evening.locked)
        assertEquals(TimeLimit.Why.BEDTIME, evening.why)
        assertEquals(at(11, 2), evening.bedEndsAt)
        val night = TimeLimit.state(s, at(11, 1), utc)                // after midnight: still last night's lockdown
        assertTrue(night.locked)
        assertEquals(at(11, 2), night.bedEndsAt)
        assertFalse(TimeLimit.state(s, at(11, 2), utc).locked)
    }

    @Test
    fun the_start_and_length_are_adjustable() {
        val late = s.copy(bedStartMin = 21 * 60 + 30, bedMinutes = 10 * 60)       // 9:30 PM for 10 hours -> 7:30 AM
        assertFalse(TimeLimit.state(late, at(10, 21, 0), utc).locked)
        assertTrue(TimeLimit.state(late, at(10, 21, 30), utc).locked)
        assertTrue(TimeLimit.state(late, at(11, 7, 0), utc).locked)
        assertFalse(TimeLimit.state(late, at(11, 7, 30), utc).locked)
    }

    @Test
    fun a_lockdown_that_starts_in_the_morning_works_too() {
        val nap = s.copy(bedStartMin = 13 * 60, bedMinutes = 90)
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
        val c = RemoteCommands.parse("bedtime 9pm 8h")
        assertEquals(RemoteCommands.Command.Bedtime(true, 21 * 60, 480), c)
        val n = RemoteCommands.apply(s, c!!, at(10, 12), utc).settings
        assertEquals(21 * 60, n.bedStartMin)
        assertEquals(480, n.bedMinutes)
        assertEquals(RemoteCommands.Command.Bedtime(false, null, null), RemoteCommands.parse("bedtime off"))
        assertFalse(RemoteCommands.apply(s, RemoteCommands.parse("bedtime off")!!, at(10, 12), utc).settings.bedOn)
        assertEquals(RemoteCommands.Command.Bedtime(true, 20 * 60, 360), RemoteCommands.parse("bedtime 8pm for 6 hours"))
        assertNull(RemoteCommands.parse("bedtime whenever"))
        assertNotNull(RemoteCommands.parse("bedtime on"))
    }

    @Test
    fun the_status_says_it_is_bedtime() {
        assertTrue(RemoteCommands.status(s, at(10, 22), utc).contains("Bedtime lockdown until 2:00 AM"))
    }
}
