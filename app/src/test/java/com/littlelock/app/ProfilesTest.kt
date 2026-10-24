package com.littlelock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The rules for children sharing a tablet: who is shown, where Exit leads, the idle count, the order of the apps. No tablet needed. */
@RunWith(RobolectricTestRunner::class)
class ProfilesTest {

    private val ava = Profile("p1", "Ava")
    private val ben = Profile("p2", "Ben")
    private val two = listOf(ava, ben)

    private val min = 60_000L

    // ---- what the home screen shows

    @Test
    fun one_child_is_just_their_home_screen() {
        assertEquals(Profiles.Screen.Home(ava), Profiles.resolve(listOf(ava), "", ""))
        assertEquals(Profiles.Screen.Home(ava), Profiles.resolve(listOf(ava), "", Profiles.PICKER))
    }

    @Test
    fun with_two_children_the_picker_shows_unless_a_default_or_a_signed_in_child_is_set() {
        assertEquals(Profiles.Screen.Picker, Profiles.resolve(two, "", ""))
        assertEquals(Profiles.Screen.Home(ben), Profiles.resolve(two, "p2", ""))
        assertEquals(Profiles.Screen.Home(ava), Profiles.resolve(two, "p2", "p1"))
        assertEquals(Profiles.Screen.Picker, Profiles.resolve(two, "p2", Profiles.PICKER))
        assertEquals(Profiles.Screen.Home(ben), Profiles.resolve(two, "p2", "gone"))
        assertEquals(Profiles.Screen.Picker, Profiles.resolve(two, "gone", ""))
    }

    @Test
    fun leaving_goes_to_the_default_or_the_picker_as_the_parent_chose() {
        assertEquals("p2", Profiles.leaveTarget(two, "p2", false))
        assertEquals(Profiles.PICKER, Profiles.leaveTarget(two, "p2", true))
        assertEquals(Profiles.PICKER, Profiles.leaveTarget(two, "", false))          // no default to go to
        assertEquals(Profiles.PICKER, Profiles.leaveTarget(two, "gone", false))
    }

    // ---- the idle count

    @Test
    fun a_busy_tablet_never_counts_as_idle() {
        val s = Profiles.idleStep(1_000L, true, 1_000L + 50 * min, 30)
        assertTrue("a count that ran out while ticking is still honoured", s.revert)
        val still = Profiles.idleStep(0L, true, 100 * min, 30)
        assertEquals(0L, still.idleSince)
        assertFalse(still.revert)
        assertFalse(Profiles.idleStep(10 * min, true, 20 * min, 30).revert)
        assertEquals(0L, Profiles.idleStep(10 * min, true, 20 * min, 30).idleSince)        // using an app starts it over
    }

    @Test
    fun the_home_screen_or_a_sleeping_tablet_goes_back_after_the_chosen_minutes() {
        val start = Profiles.idleStep(0L, false, 10 * min, 30)
        assertEquals(10 * min, start.idleSince)
        assertFalse(start.revert)
        assertFalse(Profiles.idleStep(10 * min, false, 39 * min, 30).revert)
        assertTrue(Profiles.idleStep(10 * min, false, 40 * min, 30).revert)
        assertFalse("never", Profiles.idleStep(10 * min, false, 500 * min, 0).revert)
    }

    @Test
    fun a_tablet_that_slept_past_the_time_goes_back_the_moment_it_wakes_even_if_an_app_is_in_front() {
        assertTrue(Profiles.idleStep(10 * min, true, 300 * min, 30).revert)
    }

    @Test
    fun only_a_signed_in_child_who_is_not_already_the_target_goes_back() {
        assertTrue(Profiles.shouldRevert(two, "p2", "p1", false))
        assertFalse("already the default", Profiles.shouldRevert(two, "p2", "p2", false))
        assertFalse("already on the picker", Profiles.shouldRevert(two, "p2", Profiles.PICKER, true))
        assertTrue(Profiles.shouldRevert(two, "p2", "p2", true))                            // picker is the target, the default is a child like any
        assertFalse("one child on the tablet", Profiles.shouldRevert(listOf(ava), "", "p1", true))
    }

    @Test
    fun idle_times_read_plainly() {
        assertEquals("Never", Profiles.idleName(0))
        assertEquals("30 minutes", Profiles.idleName(30))
        assertEquals("1 hour", Profiles.idleName(60))
        assertEquals("2 hours", Profiles.idleName(120))
        assertEquals(30, Profiles.DEFAULT_IDLE_MIN)
        assertTrue(Profiles.DEFAULT_IDLE_MIN in Profiles.IDLE_CHOICES)
    }

    // ---- the order and rows of the apps

    private fun app(pkg: String, label: String) = AppInfo(label, pkg)
    private val apps = listOf(app("a", "Alpha"), app("b", "Beta"), app("c", "Gamma"), app("d", "Delta"))
    private fun names(rows: List<List<AppInfo>>) = rows.map { r -> r.joinToString("") { it.label.take(1) } }

    @Test
    fun with_no_order_the_apps_are_one_flowing_row_by_name() {
        assertEquals(listOf("ABDG"), names(Profiles.arrange(apps, emptyList())))
        assertTrue(Profiles.arrange(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun an_order_with_row_breaks_makes_those_rows() {
        val order = listOf("c", "a", Profiles.ROW_BREAK, "d", "b")
        assertEquals(listOf("GA", "DB"), names(Profiles.arrange(apps, order)))
    }

    @Test
    fun apps_missing_from_the_order_follow_by_name_and_unknown_or_repeated_ones_and_stray_breaks_are_ignored() {
        val order = listOf(Profiles.ROW_BREAK, "d", "gone", "d", Profiles.ROW_BREAK, Profiles.ROW_BREAK, "a")
        assertEquals(listOf("D", "ABG"), names(Profiles.arrange(apps, order)))             // Beta and Gamma are new: they join the last row
    }

    @Test
    fun an_order_without_breaks_is_a_single_row_in_that_order() {
        assertEquals(listOf("GDBA"), names(Profiles.arrange(apps, listOf("c", "d", "b", "a"))))
    }

    // ---- the chosen rows on the screen

    @Test
    fun the_chosen_rows_are_each_centred() {
        val r = Layouts.arrangeRows(listOf(3, 2), 1000, 1600, 200, 240, 10)
        assertEquals(2, r.rows)
        assertEquals(5, r.cells.size)
        val first = r.cells.take(3)
        val second = r.cells.drop(3)
        assertEquals(190, first.first().x)                        // (1000 - (3*200 + 2*10)) / 2
        assertEquals(295, second.first().x)                       // (1000 - (2*200 + 10)) / 2
        assertTrue(second.first().y > first.first().y)
    }

    @Test
    fun a_chosen_row_that_is_too_wide_splits_into_even_lines() {
        val r = Layouts.arrangeRows(listOf(7), 1000, 1600, 200, 240, 10)         // 4 fit across: 4 and 3
        assertEquals(2, r.rows)
        assertEquals(4, r.cells.groupBy { it.y }.values.maxOf { it.size })
    }

    @Test
    fun rows_that_do_not_match_the_apps_fall_back_to_filling_the_screen() {
        val plain = Layouts.forMode(Layouts.GRID, 5, 1000, 1600, 200, 240, 10, Layouts.MIDDLE, null)
        val wrong = Layouts.forMode(Layouts.GRID, 5, 1000, 1600, 200, 240, 10, Layouts.MIDDLE, listOf(2, 2))
        assertEquals(plain.cells, wrong.cells)
        val chosen = Layouts.forMode(Layouts.GRID, 5, 1000, 1600, 200, 240, 10, Layouts.MIDDLE, listOf(1, 4))
        assertEquals(2, chosen.rows)
    }

    @Test
    fun the_rows_in_auto_size_mode_still_fit_the_screen() {
        val r = Layouts.autoFitRows(listOf(2, 3), 1000, 1600, 200, 240, 10)
        assertTrue(r.contentW <= 1000 && r.contentH <= 1600)
        assertTrue(r.scale >= 1f)
    }

    // ---- how a profile is stored

    @Test
    fun a_profile_survives_being_saved_and_read_back() {
        val p = Profile("p3", "Cleo", Avatars.value("fox"), "h", "s", "teal", Look.colorValue(Look.COLORS[2].second), 1, Layouts.AUTO, Layouts.TOP, 3,
            setOf("x.y", "a.b"), listOf("x.y", Profiles.ROW_BREAK, "a.b"))
        val back = Profiles.parse(Profiles.serialize(listOf(ava, p)))
        assertEquals(listOf(ava, p), back)
    }

    @Test
    fun unreadable_storage_gives_no_profiles_and_odd_values_are_tidied() {
        assertTrue(Profiles.parse("not json").isEmpty())
        val odd = Profiles.parse("""[{"id":"x","accent":"nope","tile":99,"gap":-4},{"name":"no id"}]""")
        assertEquals(1, odd.size)
        assertEquals(Accent.choice(null).key, odd.first().accent)
        assertEquals(Look.TILES.lastIndex, odd.first().tileSize)
        assertEquals(0, odd.first().gap)
    }

    @Test
    fun every_built_in_picture_exists_and_is_found_by_its_stored_value() {
        Avatars.BUILT_IN.forEach { (key, _) ->
            assertTrue("picture $key", Avatars.resOf(key) != 0)
            assertEquals(key, Avatars.keyOf(Avatars.value(key)))
        }
        assertEquals(16, Avatars.BUILT_IN.size)
        assertNull(Avatars.keyOf("a:nothing"))
        assertNull(Avatars.keyOf(Avatars.IMAGE))
        assertNotNull(Avatars.keyOf(Avatars.value("cat")))
    }
}
