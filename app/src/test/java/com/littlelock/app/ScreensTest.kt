package com.littlelock.app

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The screens a parent and child actually use, driven the way a person would: tap, and see what changed. */
@RunWith(RobolectricTestRunner::class)
class ScreensTest {

    @Before
    fun setUp() {
        TestApps.install("com.example.puzzles", "Puzzles")
        TestApps.install("com.example.drawing", "Drawing")
        TestApps.install("com.example.music", "Music")
        TestApps.parentSetUp("com.example.puzzles", "com.example.drawing", "com.example.music")
        Preferences.setWallpaper(TestApps.context, "")
        Preferences.saveTimeSettings(TestApps.context, TimeLimit.Settings())
    }

    private fun settings(): SettingsActivity {
        ParentSession.unlock()
        return Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
    }

    @Test
    fun tapping_a_colour_swatch_changes_the_wallpaper() {
        val a = settings()
        val colors = a.findViewById<LinearLayout>(R.id.llColors)
        assertEquals(Look.COLORS.size, colors.childCount)
        colors.getChildAt(2).performClick()
        assertEquals(Look.colorValue(Look.COLORS[2].second), Preferences.getWallpaper(TestApps.context))
    }

    @Test
    fun tapping_an_animal_or_a_photo_changes_the_wallpaper() {
        val a = settings()
        a.findViewById<LinearLayout>(R.id.llAnimals).getChildAt(1).performClick()
        assertEquals(Look.animalValue(Look.ANIMALS[1].first), Preferences.getWallpaper(TestApps.context))
        a.findViewById<LinearLayout>(R.id.llPhotos).getChildAt(3).performClick()
        assertEquals(Look.photoValue(Look.PHOTOS[3].first), Preferences.getWallpaper(TestApps.context))
    }

    @Test
    fun the_picked_wallpaper_shows_on_the_childs_home_screen_and_every_choice_has_a_picture() {
        Look.ANIMALS.forEach { assertTrue("animal ${it.first}", Wallpapers.resOf(it.first) != 0) }
        Look.PHOTOS.forEach { assertTrue("photo ${it.first}", Wallpapers.photoRes(it.first) != 0) }
        Preferences.setWallpaper(TestApps.context, Look.colorValue(Look.COLORS[1].second))
        val home = Robolectric.buildActivity(LauncherActivity::class.java).setup().get()
        val bg = home.findViewById<View>(R.id.root).background
        assertTrue(bg is android.graphics.drawable.ColorDrawable)
        assertEquals(Look.COLORS[1].second, (bg as android.graphics.drawable.ColorDrawable).color)
    }

    @Test
    fun the_apps_sit_in_the_middle_however_many_there_are() {
        val home = Robolectric.buildActivity(LauncherActivity::class.java).setup().get()
        val grid = home.findViewById<AppGrid>(R.id.gridApps)
        grid.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
        grid.layout(0, 0, 1000, 1600)
        val kids = (0 until grid.childCount).map { grid.getChildAt(it) }
        assertEquals(3, kids.size)
        val left = kids.minOf { it.left } - grid.paddingLeft
        val right = (1000 - grid.paddingRight) - kids.maxOf { it.right }
        assertTrue("left $left right $right", kotlin.math.abs(left - right) <= 2)
        val top = kids.minOf { it.top } - grid.paddingTop
        val bottom = (1600 - grid.paddingBottom) - kids.maxOf { it.bottom }
        assertTrue("top $top bottom $bottom", kotlin.math.abs(top - bottom) <= 2)
    }

    @Test
    fun fill_the_screen_makes_the_tiles_bigger_than_the_picked_size() {
        val home = Robolectric.buildActivity(LauncherActivity::class.java).setup().get()
        val grid = home.findViewById<AppGrid>(R.id.gridApps)
        fun cellWidth(mode: Int): Int {
            grid.mode = mode
            grid.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
            return grid.getChildAt(0).measuredWidth
        }
        assertTrue(cellWidth(Layouts.AUTO) > cellWidth(Layouts.GRID))
    }

    @Test
    fun the_pin_pad_scrolls_and_every_key_is_inside_it() {
        val pad = Robolectric.buildActivity(PinLockActivity::class.java).setup().get()
        val root = pad.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        assertTrue("the PIN pad can scroll on a short screen", root is android.widget.ScrollView)
        listOf(R.id.btn0, R.id.btn1, R.id.btn9, R.id.btnClear).forEach { assertTrue(pad.findViewById<View>(it) != null) }
    }

    @Test
    fun when_time_is_up_home_leads_to_the_time_up_page_not_the_apps() {
        Preferences.saveTimeSettings(TestApps.context, TimeLimit.Settings(limitOn = true, limitMinutes = 30, usedMs = 40 * 60_000L))
        val home = Robolectric.buildActivity(LauncherActivity::class.java).setup().get()
        val started = shadowOf(home).nextStartedActivity
        assertEquals(TimeUpActivity::class.java.name, started.component?.className)
    }

    @Test
    fun a_forced_lock_is_lifted_by_an_extension() {
        Preferences.saveTimeSettings(TestApps.context, TimeLimit.Settings(forceLock = true))
        assertTrue(Preferences.isTimeLocked(TestApps.context))
        Preferences.saveTimeSettings(TestApps.context, Preferences.timeSettings(TestApps.context).copy(extendedUntil = System.currentTimeMillis() + 600_000L))
        assertFalse(Preferences.isTimeLocked(TestApps.context))
    }

    @Test
    fun the_parent_pin_on_the_time_up_page_answers_ok_without_signing_the_parent_in() {
        val intent = android.content.Intent(TestApps.context, PinLockActivity::class.java)
            .putExtra(PinLockActivity.EXTRA_MODE, PinLockActivity.MODE_ENTER)
            .putExtra(PinLockActivity.EXTRA_NEXT, PinLockActivity.NEXT_RESULT)
        val pad = Robolectric.buildActivity(PinLockActivity::class.java, intent).setup().get()
        ParentSession.lock()
        listOf("1", "2", "3", "4").forEach { pad.enterDigit(it) }
        assertEquals(Activity.RESULT_OK, shadowOf(pad).resultCode)
        assertFalse(ParentSession.isUnlocked())
    }

    @Test
    fun time_settings_start_each_day_at_zero_used() {
        val day1 = 1_800_000_000_000L
        Preferences.saveTimeSettings(TestApps.context, TimeLimit.Settings(limitOn = true, usedMs = 99_000L), day1)
        assertEquals(99_000L, Preferences.timeSettings(TestApps.context, day1).usedMs)
        assertEquals(0L, Preferences.timeSettings(TestApps.context, day1 + 26 * 3_600_000L).usedMs)
    }

    @Test
    fun a_picture_is_never_stretched() {
        val wide = android.graphics.Bitmap.createBitmap(400, 100, android.graphics.Bitmap.Config.ARGB_8888)
        val out = Wallpapers.cover(wide, 100, 200)
        assertEquals(100, out.width)
        assertEquals(200, out.height)
    }
}
