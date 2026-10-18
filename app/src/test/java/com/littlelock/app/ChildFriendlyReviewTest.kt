package com.littlelock.app

import android.view.View
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** A child sees only what the parent chose - never Settings or the Play Store - and a friendly message otherwise. */
@RunWith(RobolectricTestRunner::class)
class ChildFriendlyReviewTest {

    @Before
    fun setUp() {
        TestApps.install("com.android.settings", "Settings")
        TestApps.install("com.android.vending", "Play Store")
        TestApps.install("com.example.puzzles", "Puzzles")
    }

    @Test
    fun the_user_interface_is_intuitive_for_children() {
        TestApps.parentSetUp("com.example.puzzles")
        val activity = Robolectric.buildActivity(LauncherActivity::class.java).setup().get()
        val grid = activity.findViewById<AppGrid>(R.id.gridApps)
        val shown = (0 until grid.childCount).map { grid.getChildAt(it).contentDescription.toString() }
        assertEquals(listOf("Puzzles"), shown)
    }

    @Test
    fun with_nothing_allowed_the_child_sees_a_message_not_an_empty_screen() {
        TestApps.parentSetUp()
        val activity = Robolectric.buildActivity(LauncherActivity::class.java).setup().get()
        assertEquals(0, activity.findViewById<AppGrid>(R.id.gridApps).childCount)
        val empty = activity.findViewById<TextView>(R.id.tvEmpty)
        assertEquals(View.VISIBLE, empty.visibility)
        assertEquals(activity.getString(R.string.launcher_empty), empty.text.toString())
    }

    @Test
    fun on_first_run_the_parent_is_asked_to_set_a_pin() {
        val activity = Robolectric.buildActivity(LauncherActivity::class.java).setup().get()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(PinLockActivity::class.java.name, started.component?.className)
        assertEquals(PinLockActivity.MODE_SET, started.getStringExtra(PinLockActivity.EXTRA_MODE))
    }

    @Test
    fun back_does_not_leave_the_home_screen() {
        TestApps.parentSetUp("com.example.puzzles")
        val activity = Robolectric.buildActivity(LauncherActivity::class.java).setup().get()
        activity.onBackPressedDispatcher.onBackPressed()
        assertFalse(activity.isFinishing)
    }
}
