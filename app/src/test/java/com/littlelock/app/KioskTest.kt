package com.littlelock.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskTest {

    @Test
    fun the_lockdown_runs_only_for_a_device_owner_in_kid_mode_with_the_parent_signed_out() {
        assertTrue(Kiosk.wanted(kioskOn = true, isOwner = true, kidMode = true, parentUnlocked = false))
        assertFalse(Kiosk.wanted(kioskOn = false, isOwner = true, kidMode = true, parentUnlocked = false))
        assertFalse(Kiosk.wanted(kioskOn = true, isOwner = false, kidMode = true, parentUnlocked = false))
        assertFalse(Kiosk.wanted(kioskOn = true, isOwner = true, kidMode = false, parentUnlocked = false))
        assertFalse(Kiosk.wanted(kioskOn = true, isOwner = true, kidMode = true, parentUnlocked = true))
    }

    @Test
    fun lock_task_allows_the_allowed_apps_littlelock_and_the_keyboards_once_each() {
        val pkgs = Kiosk.lockTaskPackages(setOf("com.a", "com.b"), "com.littlelock.app", setOf("com.kb", "com.a"))
        assertArrayEquals(arrayOf("com.a", "com.b", "com.kb", "com.littlelock.app"), pkgs)
        assertEquals(1, Kiosk.lockTaskPackages(emptySet(), "com.littlelock.app", setOf("")).size)
    }

    @Test
    fun the_adb_command_names_the_receiver() {
        assertEquals("adb shell dpm set-device-owner com.littlelock.app/.DeviceAdminReceiver", Kiosk.adbCommand("com.littlelock.app"))
    }
}
