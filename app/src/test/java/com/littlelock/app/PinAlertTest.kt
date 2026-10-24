package com.littlelock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class PinAlertTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val now = 1_800_000_000_000L          // a fixed moment

    @Test
    fun the_alert_says_when_it_happened_and_what_the_tablet_was_doing() {
        val bed = PinAlert.text(TimeLimit.Why.BEDTIME, 2, false, now, utc)
        assertTrue(bed, bed.contains("during the bedtime lockdown"))
        assertTrue(bed.contains("Wrong try 2 in a row."))
        assertTrue(PinAlert.text(TimeLimit.Why.OPEN, 1, false, now, utc).contains("while the tablet was open"))
        assertTrue(PinAlert.text(TimeLimit.Why.FORCED, 1, false, now, utc).contains("while the tablet was locked"))
        assertTrue(PinAlert.text(TimeLimit.Why.LIMIT, 1, false, now, utc).contains("after today's time ran out"))
    }

    @Test
    fun the_fifth_wrong_try_says_the_pad_is_locked() {
        val t = PinAlert.text(TimeLimit.Why.OPEN, Preferences.MAX_ATTEMPTS, true, now, utc)
        assertTrue(t.contains("locked for 30 seconds"))
    }

    @Test
    fun the_day_can_start_later_than_midnight() {
        val twoAm = UsageLog.dayKey(now, 0)
        // at 6 AM-start, the small hours still belong to the day before
        val c = java.util.Calendar.getInstance().apply { clear(); set(2026, java.util.Calendar.OCTOBER, 24, 3, 0) }.timeInMillis
        assertEquals("20261023", UsageLog.dayKey(c, 6 * 60))
        assertEquals("20261024", UsageLog.dayKey(c, 0))
        assertTrue(twoAm.length == 8)
    }
}
