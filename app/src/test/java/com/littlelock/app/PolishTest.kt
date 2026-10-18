package com.littlelock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolishTest {

    private val min = 60_000L

    @Test
    fun the_screen_sleeps_after_the_chosen_minutes_without_a_touch_while_littlelock_is_showing() {
        assertFalse(Idle.shouldSleep(2, 0, 119_000, true, true))
        assertTrue(Idle.shouldSleep(2, 0, 2 * min, true, true))
        assertFalse(Idle.shouldSleep(2, 0, 10 * min, false, true))         // another app is in front: not ours to turn off
        assertFalse(Idle.shouldSleep(2, 0, 10 * min, true, false))         // already off
        assertFalse(Idle.shouldSleep(0, 0, 10 * min, true, true))          // never
        assertEquals("Never", Idle.name(0))
    }

    @Test
    fun a_tall_screen_does_not_blow_a_square_picture_up_all_the_way() {
        val s = Wallpapers.scales(1000, 1000, 1080, 2400)
        assertEquals(2.4f, s.fill, 0.001f)
        assertTrue(s.shown < s.fill)
        assertTrue(s.shown >= 1.08f)                                        // still at least as wide as the screen
        val wide = Wallpapers.scales(2000, 1000, 1000, 500)                  // same shape as the screen: exactly fills it
        assertEquals(wide.fill, wide.shown, 0.001f)
    }

    @Test
    fun the_widest_spacing_is_really_wide() {
        assertTrue(Layouts.gapDp(Layouts.GAP_DP.lastIndex) >= 100)
        assertEquals(10, Layouts.gapDp(Layouts.DEFAULT_GAP))
    }
}
