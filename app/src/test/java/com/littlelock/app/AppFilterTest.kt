package com.littlelock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterTest {

    private val own = "com.littlelock.app"
    private val all = listOf(
        AppInfo("YouTube Kids", "com.google.android.apps.youtube.kids"),
        AppInfo("Settings", "com.android.settings"),
        AppInfo("Play Store", "com.android.vending"),
        AppInfo("abc Puzzles", "com.example.puzzles"),
        AppInfo("LittleLock", own),
        AppInfo("abc Puzzles", "com.example.puzzles") // the same app twice (two launcher activities)
    )

    @Test
    fun only_allowed_apps_are_shown_sorted_by_name() {
        val shown = AppFilter.visibleApps(all, setOf("com.example.puzzles", "com.google.android.apps.youtube.kids"), own)
        assertEquals(listOf("abc Puzzles", "YouTube Kids"), shown.map { it.label })
    }

    @Test
    fun settings_and_play_store_are_hidden_unless_the_parent_allows_them() {
        val shown = AppFilter.visibleApps(all, setOf("com.example.puzzles"), own).map { it.packageName }
        assertTrue("com.android.settings" !in shown)
        assertTrue("com.android.vending" !in shown)
        val allowed = AppFilter.visibleApps(all, setOf("com.android.settings"), own).map { it.packageName }
        assertEquals(listOf("com.android.settings"), allowed)
    }

    @Test
    fun nothing_is_shown_until_the_parent_allows_something() {
        assertTrue(AppFilter.visibleApps(all, emptySet(), own).isEmpty())
    }

    @Test
    fun littlelock_never_lists_itself() {
        assertTrue(AppFilter.visibleApps(all, setOf(own), own).isEmpty())
        assertTrue(AppFilter.choosableApps(all, own).none { it.packageName == own })
    }
}
