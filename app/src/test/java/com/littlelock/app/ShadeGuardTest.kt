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
    fun the_shield_is_a_little_taller_than_the_status_bar() {
        assertEquals(84 + 12, ShadeGuard.shieldHeightPx(84, 2f))
    }
}
