package com.littlelock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadeGuardTest {

    @Test
    fun the_shield_is_up_only_for_a_child_with_the_option_on() {
        assertTrue(ShadeGuard.shieldWanted(kidMode = true, parentSignedIn = false, optionOn = true))
        assertFalse(ShadeGuard.shieldWanted(kidMode = false, parentSignedIn = false, optionOn = true))
        assertFalse(ShadeGuard.shieldWanted(kidMode = true, parentSignedIn = true, optionOn = true))
        assertFalse(ShadeGuard.shieldWanted(kidMode = true, parentSignedIn = false, optionOn = false))
    }

    @Test
    fun the_shade_and_quick_settings_are_recognised_but_other_system_windows_are_not() {
        assertTrue(ShadeGuard.isShade("com.android.systemui", "com.android.systemui.shade.NotificationShadeWindowView"))
        assertTrue(ShadeGuard.isShade("com.android.systemui", "com.android.systemui.qs.QuickSettingsController"))
        assertFalse(ShadeGuard.isShade("com.android.systemui", "com.android.systemui.volume.VolumeDialog"))
        assertFalse(ShadeGuard.isShade("com.android.systemui", "com.android.systemui.globalactions.GlobalActionsDialog"))
        assertFalse(ShadeGuard.isShade("com.example.game", "NotificationShadeWindowView"))
        assertFalse(ShadeGuard.isShade("com.android.systemui", null))
    }

    @Test
    fun a_makers_own_system_ui_is_recognised_by_the_windows_title() {
        assertTrue(ShadeGuard.isShade("com.amazon.systemui", "com.amazon.Whatever", "Notification shade."))
        assertTrue(ShadeGuard.isShade("com.android.systemui", "x.Panel", "Quick Settings"))
        assertFalse(ShadeGuard.isShade("com.android.systemui", "x.Volume", "Volume"))
        assertFalse(ShadeGuard.isShade("com.example.game", "x", "Notification shade"))
    }

    @Test
    fun the_volume_panel_is_never_taken_for_the_shade() {
        assertFalse(ShadeGuard.isShadeWindow("com.android.systemui", 1200, 2000, true, 200, 1200))          // a narrow strip
        assertFalse(ShadeGuard.isShadeWindow("com.android.systemui", 1200, 2000, true, 1200, 1200, "Volume"))
        assertTrue(ShadeGuard.isShadeWindow("com.android.systemui", 1200, 2000, true, 1200, 1200, "Notification shade"))
    }

    @Test
    fun only_a_tall_system_ui_window_counts_as_the_shade() {
        assertTrue(ShadeGuard.isShadeWindow("com.android.systemui", 1200, 2000, true))
        assertFalse(ShadeGuard.isShadeWindow("com.android.systemui", 90, 2000, true))       // the status bar
        assertFalse(ShadeGuard.isShadeWindow("com.example.game", 1200, 2000, true))
        assertFalse(ShadeGuard.isShadeWindow("com.android.systemui", 1200, 2000, false))    // an application window
    }

    @Test
    fun the_shield_is_exactly_the_status_bar_so_nothing_below_it_is_covered() {
        assertEquals(84, ShadeGuard.shieldHeightPx(84))
    }
}
