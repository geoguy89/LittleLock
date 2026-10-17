package com.littlelock.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnforcerTest {

    private val own = "com.littlelock.app"
    private val allowed = setOf("com.example.puzzles")
    private val keyboards = setOf("com.google.android.inputmethod.latin")

    private fun block(pkg: String?, kidMode: Boolean = true, parent: Boolean = false) =
        Enforcer.shouldSendHome(pkg, own, allowed, keyboards, kidMode, parent)

    @Test
    fun an_app_that_is_not_allowed_is_sent_home() {
        assertTrue(block("com.android.settings"))
        assertTrue(block("com.android.vending"))
        assertTrue(block("com.android.chrome"))
    }

    @Test
    fun allowed_apps_littlelock_keyboards_and_the_system_ui_are_never_blocked() {
        assertFalse(block("com.example.puzzles"))
        assertFalse(block(own))
        assertFalse(block("com.google.android.inputmethod.latin"))
        assertFalse(block("com.android.systemui"))
        assertFalse(block(null))
    }

    @Test
    fun nothing_is_blocked_with_kid_mode_off_or_while_the_parent_is_signed_in() {
        assertFalse(block("com.android.settings", kidMode = false))
        assertFalse(block("com.android.settings", parent = true))
    }

    @Test
    fun the_parent_session_unlocks_then_expires() {
        ParentSession.unlock(now = 1_000L)
        assertTrue(ParentSession.isUnlocked(now = 1_000L + ParentSession.WINDOW_MS - 1))
        assertFalse(ParentSession.isUnlocked(now = 1_000L + ParentSession.WINDOW_MS))
        ParentSession.unlock()
        ParentSession.lock()
        assertFalse(ParentSession.isUnlocked())
    }
}
