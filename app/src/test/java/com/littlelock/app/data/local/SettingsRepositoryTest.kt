package com.littlelock.app.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for SettingsRepository - verifies that settings persist correctly
 * via SharedPreferences using Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SettingsRepositoryTest {

    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = SettingsRepository(context)
    }

    @Test
    fun testPinHashPersistence() {
        val testHash = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"

        repository.pinHash = testHash
        val readBack = repository.pinHash

        assertNotNull("PIN hash should not be null after setting", readBack)
        assertEquals("PIN hash should match what was set", testHash, readBack)
    }

    @Test
    fun testWebhookUrlPersistence() {
        val testUrl = "https://discord.com/api/webhooks/123456/abcdef"

        repository.webhookUrl = testUrl
        val readBack = repository.webhookUrl

        assertNotNull("Webhook URL should not be null after setting", readBack)
        assertEquals("Webhook URL should match what was set", testUrl, readBack)
    }

    @Test
    fun testDailyLimitPersistence() {
        val testLimit = 120 // 120 minutes

        repository.dailyLimitMinutes = testLimit
        val readBack = repository.dailyLimitMinutes

        assertEquals("Daily limit should match what was set", testLimit, readBack)
    }

    @Test
    fun testProtectionEnabledToggle() {
        // Default should be false
        assertFalse("Protection should be disabled by default", repository.protectionEnabled)

        // Enable it
        repository.protectionEnabled = true
        assertTrue("Protection should be enabled after toggle", repository.protectionEnabled)

        // Disable it
        repository.protectionEnabled = false
        assertFalse("Protection should be disabled after toggle", repository.protectionEnabled)
    }
}
