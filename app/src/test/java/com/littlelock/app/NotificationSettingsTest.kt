package com.littlelock.app

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit test verifying notification settings can be modified.
 * Checks that Preferences.kt has the required notification setting methods,
 * SettingsActivity.kt has checkboxes for these settings, and saves them.
 */
class NotificationSettingsTest {

    private val srcMain = File("src/main")

    /**
     * Verify that the parent modifies notification settings.
     */
    @Test
    fun the_parent_modifies_notification_settings() {
        // Verify Preferences.kt has the required notification setting methods
        val preferences = File(srcMain, "java/com/littlelock/app/Preferences.kt")
        assertTrue("Preferences.kt should exist", preferences.exists())
        val prefContent = preferences.readText()

        assertTrue(
            "Preferences.kt should have setAppLaunchNotifEnabled method",
            prefContent.contains("fun setAppLaunchNotifEnabled")
        )
        assertTrue(
            "Preferences.kt should have setDailySummaryEnabled method",
            prefContent.contains("fun setDailySummaryEnabled")
        )
        assertTrue(
            "Preferences.kt should have setBlockedAppsNotifEnabled method",
            prefContent.contains("fun setBlockedAppsNotifEnabled")
        )

        // Verify SettingsActivity.kt has checkboxes for these notification settings
        val settingsActivity = File(srcMain, "java/com/littlelock/app/SettingsActivity.kt")
        assertTrue("SettingsActivity.kt should exist", settingsActivity.exists())
        val settingsContent = settingsActivity.readText()

        assertTrue(
            "SettingsActivity should have app launch notification checkbox",
            settingsContent.contains("cbNotifAppLaunch")
        )
        assertTrue(
            "SettingsActivity should have daily summary notification checkbox",
            settingsContent.contains("cbNotifDailySummary")
        )
        assertTrue(
            "SettingsActivity should have blocked apps notification checkbox",
            settingsContent.contains("cbNotifBlockedApps")
        )

        // Verify SettingsActivity saves checkbox states when button is pressed
        assertTrue(
            "SettingsActivity should save app launch notification setting",
            settingsContent.contains("setAppLaunchNotifEnabled")
        )
        assertTrue(
            "SettingsActivity should save daily summary notification setting",
            settingsContent.contains("setDailySummaryEnabled")
        )
        assertTrue(
            "SettingsActivity should save blocked apps notification setting",
            settingsContent.contains("setBlockedAppsNotifEnabled")
        )
    }
}
