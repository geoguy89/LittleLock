package com.littlelock.app

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit test verifying webhook configuration is properly set up.
 * Checks that SettingsActivity loads a webhook URL field from preferences,
 * Preferences.kt has the required methods, and the settings layout has
 * a webhook URL input field.
 */
class WebhookConfigTest {

    private val srcMain = File("src/main")

    /**
     * Verify that SettingsActivity.kt loads a webhook URL field from preferences.
     */
    @Test
    fun the_parent_configures_discord_webhook() {
        val settingsActivity = File(srcMain, "java/com/littlelock/app/SettingsActivity.kt")
        assertTrue("SettingsActivity.kt should exist", settingsActivity.exists())

        val content = settingsActivity.readText()

        // Verify SettingsActivity loads webhook URL from preferences
        assertTrue(
            "SettingsActivity should load webhook URL from Preferences",
            content.contains("Preferences.getWebhookUrl")
        )

        // Verify SettingsActivity saves webhook URL to preferences
        assertTrue(
            "SettingsActivity should save webhook URL via Preferences",
            content.contains("Preferences.setWebhookUrl")
        )

        // Verify Preferences.kt has setWebhookUrl method
        val preferences = File(srcMain, "java/com/littlelock/app/Preferences.kt")
        assertTrue("Preferences.kt should exist", preferences.exists())
        val prefContent = preferences.readText()
        assertTrue(
            "Preferences.kt should have setWebhookUrl method",
            prefContent.contains("fun setWebhookUrl")
        )

        // Verify Preferences.kt has getWebhookUrl method
        assertTrue(
            "Preferences.kt should have getWebhookUrl method",
            prefContent.contains("fun getWebhookUrl")
        )

        // Verify the settings layout has a webhook URL input field
        val settingsLayout = File(srcMain, "res/layout/activity_settings.xml")
        assertTrue("activity_settings.xml should exist", settingsLayout.exists())
        val layoutContent = settingsLayout.readText()
        assertTrue(
            "Settings layout should contain a webhook URL input field (EditText)",
            layoutContent.contains("etWebhookUrl") || layoutContent.contains("webhookUrl")
        )
    }
}
