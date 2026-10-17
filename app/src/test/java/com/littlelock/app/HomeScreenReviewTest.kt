package com.littlelock.app

import android.widget.GridView
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The home screen shows the allowed apps and opens one when it's tapped. */
@RunWith(RobolectricTestRunner::class)
class HomeScreenReviewTest {

    @Before
    fun setUp() {
        TestApps.install("com.example.puzzles", "Puzzles")
        TestApps.install("com.example.drawing", "Drawing")
        TestApps.install("com.android.chrome", "Chrome")
        TestApps.parentSetUp("com.example.puzzles", "com.example.drawing")
    }

    @Test
    fun the_home_screen_layout_and_app_drawer_functionality() {
        val activity = Robolectric.buildActivity(LauncherActivity::class.java).setup().get()
        val grid = activity.findViewById<GridView>(R.id.gridApps)
        val labels = (0 until grid.adapter.count).map { (grid.adapter.getItem(it) as AppInfo).label }
        assertEquals(listOf("Drawing", "Puzzles"), labels)

        // tapping an app opens it
        grid.performItemClick(grid.adapter.getView(1, null, grid), 1, 1L)
        val started = shadowOf(activity).nextStartedActivity
        assertEquals("com.example.puzzles", started.component?.packageName ?: started.`package`)
    }

    @Test
    fun the_grid_picks_up_changes_to_the_allowed_list() {
        val controller = Robolectric.buildActivity(LauncherActivity::class.java).setup()
        Preferences.setAllowedApps(TestApps.context, setOf("com.example.drawing"))
        controller.pause().resume()
        val grid = controller.get().findViewById<GridView>(R.id.gridApps)
        assertEquals(1, grid.adapter.count)
    }
}
