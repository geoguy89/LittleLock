package com.littlelock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferencesTest {

    private val context get() = TestApps.context

    @Test
    fun the_pin_is_stored_hashed_never_in_plain_text() {
        assertFalse(Preferences.isPinSet(context))
        Preferences.setPin(context, "4821")
        assertTrue(Preferences.isPinSet(context))
        assertTrue(Preferences.verifyPin(context, "4821"))
        assertFalse(Preferences.verifyPin(context, "1111"))
        val stored = Preferences.getPrefs(context).all.values.joinToString(" ")
        assertFalse("the PIN itself must not be saved", stored.contains("4821"))
    }

    @Test
    fun a_plain_pin_from_an_older_version_still_works_and_gets_hashed() {
        Preferences.getPrefs(context).edit().putString("pin", "2468").putBoolean("pin_set", true).commit()
        assertTrue(Preferences.isPinSet(context))
        assertTrue(Preferences.verifyPin(context, "2468"))
        assertFalse(Preferences.getPrefs(context).contains("pin"))
        assertTrue(Preferences.verifyPin(context, "2468"))
    }

    @Test
    fun five_wrong_pins_lock_the_pad_for_thirty_seconds() {
        Preferences.setPin(context, "4821")
        val now = 1_000_000L
        repeat(Preferences.MAX_ATTEMPTS - 1) { Preferences.recordFailedPin(context, now) }
        assertEquals(0L, Preferences.pinLockoutRemaining(context, now))
        Preferences.recordFailedPin(context, now)
        assertEquals(Preferences.LOCKOUT_MS, Preferences.pinLockoutRemaining(context, now))
        assertEquals(0L, Preferences.pinLockoutRemaining(context, now + Preferences.LOCKOUT_MS))
    }

    @Test
    fun allowed_apps_and_kid_mode_are_remembered() {
        assertTrue(Profiles.allowedNow(context).isEmpty())
        assertTrue("kid mode is on by default", Preferences.isKidMode(context))
        Profiles.update(context, Profiles.MAIN) { it.copy(allowed = setOf("a.b", "c.d")) }
        Preferences.setKidMode(context, false)
        assertEquals(setOf("a.b", "c.d"), Profiles.allowedNow(context))
        assertFalse(Preferences.isKidMode(context))
    }

    @Test
    fun the_webhook_url_is_remembered_and_can_be_cleared() {
        Preferences.setWebhookUrl(context, "https://discord.com/api/webhooks/1/abc")
        assertEquals("https://discord.com/api/webhooks/1/abc", Preferences.getWebhookUrl(context))
        Preferences.setWebhookUrl(context, null)
        assertEquals(null, Preferences.getWebhookUrl(context))
    }
}
