package com.littlelock.app

import android.content.Intent
import android.view.View
import android.widget.Button
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Several children on one tablet, driven the way people use it: the picker, a child's own apps, Exit, PINs, the idle return. */
@RunWith(RobolectricTestRunner::class)
class ProfileScreensTest {

    private val ctx get() = TestApps.context
    private val day = 24 * 60 * 60_000L

    @Before
    fun setUp() {
        TestApps.install("com.example.puzzles", "Puzzles")
        TestApps.install("com.example.drawing", "Drawing")
        TestApps.install("com.example.music", "Music")
        TestApps.parentSetUp("com.example.puzzles")                       // the first child (Main) has Puzzles
    }

    /** A second child, Ben, with Drawing and Music. Returns his id. */
    private fun addBen(pin: String? = null): String {
        val ben = Profiles.add(ctx, "Ben")!!
        Profiles.update(ctx, ben.id) { it.copy(allowed = setOf("com.example.drawing", "com.example.music")) }
        if (pin != null) assertEquals(Profiles.PinCheck.OK, Profiles.setPin(ctx, ben.id, pin))
        return ben.id
    }

    private fun home(): LauncherActivity = Robolectric.buildActivity(LauncherActivity::class.java).setup().get()

    private fun labels(a: LauncherActivity): List<String> {
        val grid = a.findViewById<AppGrid>(R.id.gridApps)
        return (0 until grid.childCount).map { grid.getChildAt(it).contentDescription.toString() }
    }

    // ---- from one child to several

    @Test
    fun the_single_child_of_earlier_versions_becomes_the_first_profile() {
        val p = Preferences.getPrefs(ctx)
        p.edit().putString("kid_name", "Ava").putString("accent", "teal").putString("wallpaper", "color:ffd9d0ff")
            .putInt("tile_size", 1).putStringSet("allowed_apps", setOf("com.example.puzzles", "com.example.music")).remove("profiles").commit()
        val first = Profiles.all(ctx).single()
        assertEquals(Profiles.MAIN, first.id)
        assertEquals("Ava", first.name)
        assertEquals("teal", first.accent)
        assertEquals("color:ffd9d0ff", first.wallpaper)
        assertEquals(1, first.tileSize)
        assertEquals(setOf("com.example.puzzles", "com.example.music"), first.allowed)
    }

    @Test
    fun one_child_sees_no_picker_exit_or_switch_button() {
        val a = home()
        assertEquals(View.GONE, a.findViewById<View>(R.id.pickerRoot).visibility)
        assertEquals(View.GONE, a.findViewById<View>(R.id.btnExit).visibility)
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.btnSettings).visibility)
        assertEquals(listOf("Puzzles"), labels(a))
    }

    @Test
    fun the_last_profile_cannot_be_deleted_and_deleting_one_clears_it_as_default() {
        assertFalse(Profiles.delete(ctx, Profiles.MAIN))
        val ben = addBen()
        Profiles.setDefaultId(ctx, ben)
        assertTrue(Profiles.delete(ctx, ben))
        assertEquals(1, Profiles.all(ctx).size)
        assertEquals("", Profiles.defaultId(ctx))
    }

    @Test
    fun there_is_a_limit_on_how_many_children_can_be_added() {
        repeat(Profiles.MAX - 1) { assertNotNull(Profiles.add(ctx, "Kid")) }
        assertNull(Profiles.add(ctx, "One too many"))
    }

    // ---- the picker, each child's own apps, Exit

    @Test
    fun with_two_children_and_no_default_the_tablet_asks_who_is_using_it() {
        addBen()
        val a = home()
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.pickerRoot).visibility)
        assertEquals(View.GONE, a.findViewById<View>(R.id.scrollApps).visibility)
        assertEquals(2, a.findViewById<ProfileGrid>(R.id.gridProfiles).childCount)
        assertEquals("the parent can still get in from here", View.VISIBLE, a.findViewById<View>(R.id.btnSettings).visibility)
        assertEquals("nothing may be open on the picker", emptySet<String>(), Profiles.allowedNow(ctx))
    }

    @Test
    fun a_child_without_a_pin_taps_their_picture_and_sees_only_their_own_apps() {
        val ben = addBen()
        val a = home()
        val grid = a.findViewById<ProfileGrid>(R.id.gridProfiles)
        grid.cardAt(1)!!.performClick()                                       // Ben
        assertEquals(ben, Profiles.current(ctx)!!.id)
        assertEquals(listOf("Drawing", "Music"), labels(a))
        assertEquals(setOf("com.example.drawing", "com.example.music"), Profiles.allowedNow(ctx))
        assertEquals("their own profile has no way into parent settings", View.GONE, a.findViewById<View>(R.id.btnSettings).visibility)
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.btnExit).visibility)
        assertEquals("Exit", a.findViewById<Button>(R.id.btnExit).text.toString())
        assertEquals("Hi, Ben!", a.findViewById<android.widget.TextView>(R.id.tvAppName).text.toString())
    }

    @Test
    fun exit_goes_back_to_the_picker_or_the_default_as_the_parent_chose() {
        val ben = addBen()
        Profiles.signIn(ctx, ben)
        var a = home()
        a.findViewById<View>(R.id.btnExit).performClick()
        assertEquals("no default: the picker", View.VISIBLE, a.findViewById<View>(R.id.pickerRoot).visibility)

        Profiles.setDefaultId(ctx, Profiles.MAIN)
        Profiles.signIn(ctx, ben)
        a = home()
        a.findViewById<View>(R.id.btnExit).performClick()
        assertEquals("the default profile", Profiles.MAIN, Profiles.current(ctx)!!.id)
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.btnSettings).visibility)
        assertEquals("Switch profile", a.findViewById<Button>(R.id.btnExit).text.toString())

        Profiles.setExitToPicker(ctx, true)
        Profiles.signIn(ctx, ben)
        a = home()
        a.findViewById<View>(R.id.btnExit).performClick()
        assertNull("the parent chose the picker", Profiles.current(ctx))
    }

    @Test
    fun the_default_profile_opens_straight_away_with_the_parent_button_and_a_way_to_switch() {
        addBen()
        Profiles.setDefaultId(ctx, Profiles.MAIN)
        val a = home()
        assertEquals(listOf("Puzzles"), labels(a))
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.btnSettings).visibility)
        a.findViewById<View>(R.id.btnExit).performClick()                      // "Switch profile"
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.pickerRoot).visibility)
    }

    @Test
    fun a_profile_with_a_pin_asks_for_it_before_opening() {
        val ben = addBen(pin = "2468")
        val a = home()
        a.findViewById<ProfileGrid>(R.id.gridProfiles).cardAt(1)!!.performClick()
        val asked = shadowOf(a).nextStartedActivityForResult
        assertEquals(PinLockActivity::class.java.name, asked.intent.component!!.className)
        assertEquals(ben, asked.intent.getStringExtra(PinLockActivity.EXTRA_PROFILE))
        assertNull("still not signed in", Profiles.current(ctx))
    }

    @Test
    fun the_childs_pin_pad_signs_them_in_with_the_right_pin_only() {
        val ben = addBen(pin = "2468")
        val intent = Intent(ctx, PinLockActivity::class.java)
            .putExtra(PinLockActivity.EXTRA_MODE, PinLockActivity.MODE_ENTER)
            .putExtra(PinLockActivity.EXTRA_NEXT, PinLockActivity.NEXT_RESULT)
            .putExtra(PinLockActivity.EXTRA_PROFILE, ben)
        val pad = Robolectric.buildActivity(PinLockActivity::class.java, intent).setup().get()
        "1234".forEach { pad.enterDigit(it.toString()) }                          // the PARENT's pin
        assertNull(Profiles.current(ctx))
        "2468".forEach { pad.enterDigit(it.toString()) }
        assertEquals(ben, Profiles.current(ctx)!!.id)
        assertTrue(pad.isFinishing)
    }

    @Test
    fun a_childs_pin_does_not_open_parent_settings() {
        addBen(pin = "2468")
        val pad = Robolectric.buildActivity(PinLockActivity::class.java, Intent(ctx, PinLockActivity::class.java)
            .putExtra(PinLockActivity.EXTRA_MODE, PinLockActivity.MODE_ENTER)).setup().get()
        "2468".forEach { pad.enterDigit(it.toString()) }
        assertFalse(ParentSession.isUnlocked())
    }

    // ---- PINs

    @Test
    fun a_childs_pin_is_never_the_parents_and_never_another_childs() {
        val ben = addBen()
        val cleo = Profiles.add(ctx, "Cleo")!!.id
        assertEquals(Profiles.PinCheck.SAME_AS_PARENT, Profiles.setPin(ctx, ben, "1234"))
        assertFalse(Profiles.get(ctx, ben)!!.hasPin)
        assertEquals(Profiles.PinCheck.OK, Profiles.setPin(ctx, ben, "2468"))
        assertEquals(Profiles.PinCheck.SAME_AS_OTHER, Profiles.setPin(ctx, cleo, "2468"))
        assertEquals("changing your own PIN to what it already is is fine", Profiles.PinCheck.OK, Profiles.setPin(ctx, ben, "2468"))
        assertTrue(Profiles.isChildPin(ctx, "2468"))
        assertFalse(Profiles.isChildPin(ctx, "1111"))
        assertTrue(Profiles.verifyPin(ctx, ben, "2468"))
        assertFalse(Profiles.verifyPin(ctx, ben, "1234"))
        assertTrue("a profile with no PIN opens for anyone", Profiles.verifyPin(ctx, cleo, "0000"))
        Profiles.clearPin(ctx, ben)
        assertFalse(Profiles.get(ctx, ben)!!.hasPin)
    }

    @Test
    fun the_pin_is_stored_hashed_never_as_typed() {
        val ben = addBen(pin = "2468")
        val raw = Preferences.getPrefs(ctx).getString("profiles", "")!!
        assertFalse(raw.contains("2468"))
        assertTrue(Profiles.get(ctx, ben)!!.pinHash.isNotEmpty())
    }

    // ---- the idle return

    @Test
    fun an_idle_tablet_goes_back_to_the_default_profile_after_the_set_time() {
        val ben = addBen()
        Profiles.setDefaultId(ctx, Profiles.MAIN)
        Profiles.signIn(ctx, ben)
        val t0 = 1_000_000L
        assertFalse(Profiles.tickIdle(ctx, busy = false, now = t0))                  // the count starts
        assertFalse(Profiles.tickIdle(ctx, busy = false, now = t0 + 29 * 60_000L))
        assertEquals(ben, Profiles.current(ctx)!!.id)
        assertTrue(Profiles.tickIdle(ctx, busy = false, now = t0 + 30 * 60_000L))
        assertEquals(Profiles.MAIN, Profiles.current(ctx)!!.id)
    }

    @Test
    fun watching_a_show_is_never_cut_off() {
        val ben = addBen()
        Profiles.setDefaultId(ctx, Profiles.MAIN)
        Profiles.signIn(ctx, ben)
        val t0 = 1_000_000L
        for (i in 0..20) assertFalse(Profiles.tickIdle(ctx, busy = true, now = t0 + i * 10 * 60_000L))       // over three hours in an app
        assertEquals(ben, Profiles.current(ctx)!!.id)
    }

    @Test
    fun a_screen_that_went_off_counts_even_if_nothing_could_tick_while_it_slept() {
        val ben = addBen()
        Profiles.signIn(ctx, ben)
        Profiles.markIdle(ctx, 5_000_000L)
        assertTrue(Profiles.tickIdle(ctx, busy = true, now = 5_000_000L + 45 * 60_000L))      // woken with an app in front
        assertNull("no default: the picker", Profiles.current(ctx))
    }

    @Test
    fun idle_never_means_never_and_one_child_is_left_alone() {
        val ben = addBen()
        Profiles.setIdleMinutes(ctx, 0)
        Profiles.signIn(ctx, ben)
        Profiles.markIdle(ctx, 1_000L)
        assertFalse(Profiles.tickIdle(ctx, busy = false, now = 1_000L + 10 * day))
        Profiles.delete(ctx, ben)
        Profiles.setIdleMinutes(ctx, 30)
        assertFalse(Profiles.tickIdle(ctx, busy = false, now = 1_000L + 20 * day))
    }

    @Test
    fun the_home_screen_applies_an_idle_time_that_ran_out_while_it_was_away() {
        val ben = addBen()
        Profiles.setDefaultId(ctx, Profiles.MAIN)
        Profiles.signIn(ctx, ben)
        Profiles.markIdle(ctx, System.currentTimeMillis() - 2 * 60 * 60_000L)
        val a = home()
        assertEquals(Profiles.MAIN, Profiles.current(ctx)!!.id)
        assertEquals(listOf("Puzzles"), labels(a))
    }

    // ---- the lockdown and the blocker follow the child in front

    @Test
    fun the_blocker_allows_the_signed_in_childs_apps_and_the_lockdown_allows_everyones() {
        val ben = addBen()
        Profiles.signIn(ctx, Profiles.MAIN)
        assertEquals(setOf("com.example.puzzles"), Profiles.allowedNow(ctx))
        Profiles.signIn(ctx, ben)
        assertEquals(setOf("com.example.drawing", "com.example.music"), Profiles.allowedNow(ctx))
        assertEquals(setOf("com.example.puzzles", "com.example.drawing", "com.example.music"), Profiles.allowedEverywhere(ctx))
        assertTrue(Enforcer.shouldSendHome("com.example.puzzles", "x", Profiles.allowedNow(ctx), emptySet(), true, false))
        assertFalse(Enforcer.shouldSendHome("com.example.music", "x", Profiles.allowedNow(ctx), emptySet(), true, false))
    }

    // ---- each child's own look

    @Test
    fun each_child_has_their_own_colour_and_wallpaper() {
        val ben = addBen()
        Profiles.update(ctx, Profiles.MAIN) { it.copy(accent = "teal", wallpaper = Look.colorValue(Look.COLORS[1].second)) }
        Profiles.update(ctx, ben) { it.copy(accent = "red", wallpaper = Look.colorValue(Look.COLORS[6].second)) }
        Profiles.signIn(ctx, ben)
        val a = home()
        val bg = a.findViewById<View>(R.id.root).background
        assertEquals(Look.COLORS[6].second, (bg as android.graphics.drawable.ColorDrawable).color)
        assertEquals("red", Accent.current(ctx).key)
        Profiles.signIn(ctx, Profiles.MAIN)
        assertEquals("teal", Accent.current(ctx).key)
        assertNotEquals(Profiles.get(ctx, ben)!!.wallpaper, Profiles.get(ctx, Profiles.MAIN)!!.wallpaper)
    }

    @Test
    fun the_childs_picture_shows_beside_their_name() {
        val ben = addBen()
        Profiles.update(ctx, ben) { it.copy(avatar = Avatars.value("robot")) }
        Profiles.signIn(ctx, ben)
        val a = home()
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.ivAvatar).visibility)
        Profiles.signIn(ctx, Profiles.MAIN)
        val b = home()
        assertEquals("no picture chosen: nothing is drawn", View.GONE, b.findViewById<View>(R.id.ivAvatar).visibility)
    }

    @Test
    fun the_chosen_rows_and_order_show_on_the_home_screen() {
        val ben = addBen()
        Profiles.update(ctx, ben) { it.copy(order = listOf("com.example.music", Profiles.ROW_BREAK, "com.example.drawing")) }
        Profiles.signIn(ctx, ben)
        val a = home()
        assertEquals(listOf("Music", "Drawing"), labels(a))
        assertEquals(listOf(1, 1), a.findViewById<AppGrid>(R.id.gridApps).segments)
        Profiles.update(ctx, ben) { it.copy(order = emptyList()) }
        val b = home()
        assertEquals(listOf("Drawing", "Music"), labels(b))
        assertNull(b.findViewById<AppGrid>(R.id.gridApps).segments)
    }

    // ---- the parent's screens

    @Test
    fun settings_lists_every_child_and_only_asks_about_sharing_with_more_than_one() {
        ParentSession.unlock()
        var s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertEquals(1, s.findViewById<android.widget.LinearLayout>(R.id.llProfiles).childCount)
        assertEquals(View.GONE, s.findViewById<View>(R.id.llSharing).visibility)
        addBen()
        ParentSession.unlock()
        s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertEquals(2, s.findViewById<android.widget.LinearLayout>(R.id.llProfiles).childCount)
        assertEquals(View.VISIBLE, s.findViewById<View>(R.id.llSharing).visibility)
        assertEquals("none + each child", 3, s.findViewById<android.widget.RadioGroup>(R.id.rgDefaultProfile).childCount)
    }

    @Test
    fun the_editor_changes_only_the_child_being_edited() {
        val ben = addBen()
        ParentSession.unlock()
        val e = Robolectric.buildActivity(ProfileEditorActivity::class.java, Intent(ctx, ProfileEditorActivity::class.java)
            .putExtra(ProfileEditorActivity.EXTRA_ID, ben)).setup().get()
        e.findViewById<android.widget.LinearLayout>(R.id.llColors).getChildAt(3).performClick()
        e.findViewById<android.widget.LinearLayout>(R.id.llAvatars).getChildAt(2).performClick()
        assertEquals(Look.colorValue(Look.COLORS[3].second), Profiles.get(ctx, ben)!!.wallpaper)
        assertEquals(Avatars.value(Avatars.BUILT_IN[2].first), Profiles.get(ctx, ben)!!.avatar)
        assertEquals("", Profiles.get(ctx, Profiles.MAIN)!!.wallpaper)
        assertEquals("", Profiles.get(ctx, Profiles.MAIN)!!.avatar)
    }

    @Test
    fun the_arrange_screen_moves_apps_and_starts_rows() {
        val ben = addBen()
        ParentSession.unlock()
        val a = Robolectric.buildActivity(ArrangeAppsActivity::class.java, Intent(ctx, ArrangeAppsActivity::class.java)
            .putExtra(ArrangeAppsActivity.EXTRA_ID, ben)).setup().get()
        val root = a.findViewById<View>(android.R.id.content) as android.view.ViewGroup
        val buttons = ArrayList<Button>()
        fun collect(v: View) { if (v is Button) buttons.add(v) else if (v is android.view.ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i)) }
        collect(root)
        buttons.first { it.text == "▼" && it.isEnabled }.performClick()               // Drawing goes below Music
        assertEquals(listOf("com.example.music", "com.example.drawing"), Profiles.get(ctx, ben)!!.order.filter { it != Profiles.ROW_BREAK })
    }
}
