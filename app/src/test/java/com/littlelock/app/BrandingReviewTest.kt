package com.littlelock.app

import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrandingReviewTest {

    @Test
    fun the_app_launches_and_displays_a_custom_LittleLock_branding() {
        val context = TestApps.context
        assertEquals("LittleLock", context.applicationInfo.loadLabel(context.packageManager).toString())
        TestApps.parentSetUp()
        val activity = Robolectric.buildActivity(LauncherActivity::class.java).setup().get()
        val title = activity.findViewById<TextView>(R.id.tvAppName).text.toString()
        assertEquals(activity.getString(R.string.launcher_title), title)
    }
}
