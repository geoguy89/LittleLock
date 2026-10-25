package com.littlelock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** Screen time for the whole tablet or for each child, and the parent's PIN opening any child's profile. */
@RunWith(RobolectricTestRunner::class)
class ChildLimitsTest {

    private val ctx get() = TestApps.context
    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        TestApps.parentSetUp("a.b")
        Profiles.add(ctx, "Ben")
        Profiles.signIn(ctx, Profiles.MAIN)
    }

    private fun limit(minutes: Int, used: Long = 0L) = TimeLimit.Settings(bedOn = false, limitOn = true, limitMinutes = minutes, usedMs = used)

    @Test
    fun by_default_one_limit_covers_the_whole_tablet() {
        assertFalse(Profiles.limitsPerChild(ctx))
        Preferences.saveTimeSettings(ctx, limit(30))
        Profiles.signIn(ctx, "p2")
        assertEquals(30, Preferences.timeSettings(ctx).limitMinutes)
    }

    @Test
    fun with_limits_per_child_each_child_has_their_own_time() {
        Preferences.saveTimeSettings(ctx, limit(60))
        Profiles.setLimitsPerChild(ctx, true)
        assertTrue(Profiles.limitsPerChild(ctx))
        assertEquals("children start from the tablet's limit", 60, Preferences.timeSettings(ctx, forId = "p2").limitMinutes)

        Preferences.saveTimeSettings(ctx, limit(20, 25 * 60_000L), forId = Profiles.MAIN)
        assertTrue(Preferences.isTimeLocked(ctx))                                   // the first child used 25 of 20 minutes
        Profiles.signIn(ctx, "p2")
        assertFalse("the other child is not held back", Preferences.isTimeLocked(ctx))
        assertEquals(60, Preferences.timeSettings(ctx).limitMinutes)
    }

    @Test
    fun time_is_counted_for_the_child_using_the_tablet_and_not_on_the_picker() {
        Profiles.setLimitsPerChild(ctx, true)
        Preferences.saveTimeSettings(ctx, limit(60), forId = "p2")
        Profiles.signIn(ctx, "p2")
        Preferences.addUsedMs(ctx, 10_000L, now)
        assertEquals(10_000L, Preferences.timeSettings(ctx, now, "p2").usedMs)
        assertEquals(0L, Preferences.timeSettings(ctx, now, Profiles.MAIN).usedMs)
        Profiles.showPicker(ctx)
        Preferences.addUsedMs(ctx, 10_000L, now)
        assertEquals(10_000L, Preferences.timeSettings(ctx, now, "p2").usedMs)
        assertFalse("the picker is never locked", Preferences.isTimeLocked(ctx))
    }

    @Test
    fun a_new_day_starts_each_childs_used_time_again() {
        Profiles.setLimitsPerChild(ctx, true)
        Preferences.saveTimeSettings(ctx, limit(60, 99_000L), now, "p2")
        assertEquals(99_000L, Preferences.timeSettings(ctx, now, "p2").usedMs)
        assertEquals(0L, Preferences.timeSettings(ctx, now + 26 * 3_600_000L, "p2").usedMs)
    }

    @Test
    fun the_summary_and_day_start_stay_one_setting_for_the_tablet() {
        Profiles.setLimitsPerChild(ctx, true)
        Preferences.saveTimeSettings(ctx, Preferences.timeSettings(ctx, forId = "p2").copy(summaryMin = 21 * 60), forId = "p2")
        assertEquals(21 * 60, Preferences.timeSettings(ctx, forId = Profiles.MAIN).summaryMin)
    }

    @Test
    fun switching_back_gives_the_tablet_limit_again_and_one_child_means_no_per_child_limits() {
        Preferences.saveTimeSettings(ctx, limit(45))
        Profiles.setLimitsPerChild(ctx, true)
        Preferences.saveTimeSettings(ctx, limit(10), forId = "p2")
        Profiles.setLimitsPerChild(ctx, false)
        assertEquals(45, Preferences.timeSettings(ctx, forId = "p2").limitMinutes)
        Profiles.setLimitsPerChild(ctx, true)
        Profiles.delete(ctx, "p2")
        assertFalse(Profiles.limitsPerChild(ctx))
    }

    @Test
    fun a_childs_own_time_survives_being_saved_and_read_back() {
        val p = Profile("p9", "Cleo", time = TimeLimit.Settings(limitOn = true, limitMinutes = 25, usedMs = 5L, forceLock = true,
            extendedUntil = 7L, bedOn = false, bedStartMin = 19 * 60, bedEndMin = 6 * 60), usedDay = "2026-10-24")
        assertEquals(listOf(p), Profiles.parse(Profiles.serialize(listOf(p))))
    }

    @Test
    fun the_parent_pin_opens_any_childs_profile_and_a_wrong_one_does_not() {
        Profiles.setPin(ctx, "p2", "5678")
        Profiles.showPicker(ctx)
        fun tryPin(pin: String): Boolean {
            val a = Robolectric.buildActivity(PinLockActivity::class.java, android.content.Intent(ctx, PinLockActivity::class.java)
                .putExtra(PinLockActivity.EXTRA_MODE, PinLockActivity.MODE_ENTER)
                .putExtra(PinLockActivity.EXTRA_PROFILE, "p2")).setup()
            pin.forEach { c -> a.get().findViewById<android.widget.Button>(ctx.resources.getIdentifier("btn$c", "id", ctx.packageName)).performClick() }
            return Profiles.current(ctx)?.id == "p2"
        }
        assertFalse(tryPin("0000"))
        assertTrue("the parent PIN overrides", tryPin("1234"))
    }
}
