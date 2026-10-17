package com.littlelock.app

import android.content.Intent
import android.widget.Button
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Getting into parent settings: the lock button, the PIN pad, and settings refusing to open without the PIN. */
@RunWith(RobolectricTestRunner::class)
class LauncherFeaturesTest {

    @Before
    fun setUp() {
        TestApps.install("com.example.puzzles", "Puzzles")
        TestApps.parentSetUp("com.example.puzzles")
    }

    private fun pinPad(mode: String, next: String = PinLockActivity.NEXT_SETTINGS) =
        Robolectric.buildActivity(PinLockActivity::class.java, Intent(TestApps.context, PinLockActivity::class.java)
            .putExtra(PinLockActivity.EXTRA_MODE, mode).putExtra(PinLockActivity.EXTRA_NEXT, next)).setup().get()

    private fun PinLockActivity.type(pin: String) = pin.forEach { enterDigit(it.toString()) }

    @Test
    fun the_launcher_handles_core_interactions_without_crashes() {
        val activity = Robolectric.buildActivity(LauncherActivity::class.java).setup().get()
        activity.findViewById<android.view.View>(R.id.btnSettings).performClick()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(PinLockActivity::class.java.name, started.component?.className)
        assertEquals(PinLockActivity.MODE_ENTER, started.getStringExtra(PinLockActivity.EXTRA_MODE))
    }

    @Test
    fun the_right_pin_opens_parent_settings() {
        val pad = pinPad(PinLockActivity.MODE_ENTER)
        pad.type("1234")
        assertTrue(ParentSession.isUnlocked())
        assertEquals(SettingsActivity::class.java.name, shadowOf(pad).nextStartedActivity.component?.className)
        assertTrue(pad.isFinishing)
    }

    @Test
    fun a_wrong_pin_keeps_settings_shut() {
        val pad = pinPad(PinLockActivity.MODE_ENTER)
        pad.type("9999")
        assertFalse(ParentSession.isUnlocked())
        assertNull(shadowOf(pad).nextStartedActivity)
        assertFalse(pad.isFinishing)
    }

    @Test
    fun setting_a_pin_needs_it_typed_twice() {
        Preferences.getPrefs(TestApps.context).edit().clear().commit()
        val pad = pinPad(PinLockActivity.MODE_SET)
        pad.type("5678")
        pad.type("5679")
        assertFalse(Preferences.isPinSet(TestApps.context))
        pad.type("5678")
        pad.type("5678")
        assertTrue(Preferences.verifyPin(TestApps.context, "5678"))
    }

    @Test
    fun changing_the_pin_without_being_signed_in_asks_for_the_old_one() {
        val pad = pinPad(PinLockActivity.MODE_SET, PinLockActivity.NEXT_NONE)
        pad.type("0000")
        pad.type("0000")
        assertTrue("the PIN must not have changed", Preferences.verifyPin(TestApps.context, "1234"))
    }

    @Test
    fun parent_settings_will_not_open_without_the_pin() {
        val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertTrue(settings.isFinishing)
        assertEquals(PinLockActivity::class.java.name, shadowOf(settings).nextStartedActivity.component?.className)
    }

    @Test
    fun parent_settings_turns_kid_mode_off_and_back_on() {
        ParentSession.unlock()
        val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertFalse(settings.isFinishing)
        val button = settings.findViewById<Button>(R.id.btnKidMode)
        button.performClick()
        assertFalse(Preferences.isKidMode(TestApps.context))
        button.performClick()
        assertTrue(Preferences.isKidMode(TestApps.context))
        settings.findViewById<Button>(R.id.btnBack).performClick()
        assertFalse("leaving settings locks again", ParentSession.isUnlocked())
    }
}
