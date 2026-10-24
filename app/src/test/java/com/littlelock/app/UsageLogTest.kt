package com.littlelock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageLogTest {

    private val day = "20261023"
    private val names = mapOf("a.yt" to "YouTube Kids", "b.pbs" to "PBS Kids")

    private fun sample(): UsageData {
        var d: UsageData = emptyMap()
        d = UsageLog.add(d, day, "a.yt", 0, 1)
        d = UsageLog.add(d, day, "a.yt", 65 * 60_000L, 0)
        d = UsageLog.add(d, day, "a.yt", 0, 1)
        d = UsageLog.add(d, day, "b.pbs", 12 * 60_000L, 1)
        return d
    }

    @Test
    fun time_and_openings_add_up_per_app() {
        val d = sample()
        assertEquals(UsageLog.Stat(65 * 60_000L, 2), d[day]!!["a.yt"])
        assertEquals(77 * 60_000L, UsageLog.totalMs(d, day))
        assertEquals(3, UsageLog.totalOpens(d, day))
    }

    @Test
    fun the_longest_used_app_is_listed_first_with_its_time() {
        val text = UsageLog.itemised(sample(), day, { names[it] ?: it })
        val lines = text.lines()
        assertEquals("• **YouTube Kids** - 1 h 5 min, opened 2 times", lines[0])
        assertEquals("• **PBS Kids** - 12 min, opened once", lines[1])
    }

    @Test
    fun an_app_opened_but_barely_used_still_shows_up() {
        val d = UsageLog.add(emptyMap(), day, "a.yt", 0, 1)
        assertEquals("• a.yt - under a minute, opened once", UsageLog.itemised(d, day, { it }, bold = false))
    }

    @Test
    fun a_long_list_is_cut_short_with_a_rolled_up_line() {
        var d: UsageData = emptyMap()
        for (i in 1..5) d = UsageLog.add(d, day, "app$i", i * 60_000L, 1)
        val lines = UsageLog.itemised(d, day, { it }, max = 3).lines()
        assertEquals(4, lines.size)
        assertEquals("• and 2 more (3 min)", lines[3])
    }

    @Test
    fun it_survives_being_stored_as_text() {
        val d = sample()
        assertEquals(d, UsageLog.parse(UsageLog.serialize(d)))
        assertTrue(UsageLog.parse("garbage\n|||\nx|y|z|w").isEmpty())
    }

    @Test
    fun only_the_last_two_weeks_are_kept() {
        var d: UsageData = emptyMap()
        for (i in 1..20) d = UsageLog.add(d, "202610%02d".format(i), "a", 1000, 1)
        assertEquals(UsageLog.KEEP_DAYS, d.size)
        assertFalse(d.containsKey("20261001"))
        assertTrue(d.containsKey("20261020"))
    }

    @Test
    fun a_day_with_nothing_says_so() {
        assertEquals("", UsageLog.itemised(emptyMap(), day, { it }))
        assertEquals("No app use was recorded.", UsageLog.headline(emptyMap(), day))
        assertEquals("2 apps used, 1 h 17 min in all, opened 3 times", UsageLog.headline(sample(), day))
    }

    @Test
    fun the_day_is_named_for_the_parent() {
        assertEquals("Friday, Oct 23", UsageLog.dayName(day))
    }
}
