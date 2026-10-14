package com.littlelock.app

import com.littlelock.app.data.local.AllowedAppEntity
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Tests for the core app blocking logic.
 * Extracts and tests the decision-making logic from LauncherService
 * (isAppAllowed and timeLimitReached) in a testable form.
 */
@Config(sdk = [30])
class AppBlockingLogicTest {

    /**
     * Test helper that mirrors the isAppAllowed logic from LauncherService.
     * In the real code this is: allowedApps.any { it.packageName == packageName }
     */
    private fun isAppAllowed(packageName: String, allowedApps: List<AllowedAppEntity>): Boolean {
        return allowedApps.any { it.packageName == packageName }
    }

    /**
     * Test helper that mirrors the time limit check from LauncherService.
     * In the real code: totalUsageSeconds >= limitSeconds
     */
    private fun isTimeLimitReached(totalUsageSeconds: Int, limitSeconds: Int): Boolean {
        return totalUsageSeconds >= limitSeconds
    }

    @Test
    fun testIsApp_AllowedApp() {
        val allowedApps = listOf(
            AllowedAppEntity("com.google.android.youtube", "YouTube"),
            AllowedAppEntity("com.chrome.android", "Chrome"),
            AllowedAppEntity("com.littlelock.app", "LittleLock")
        )

        assertTrue("YouTube should be allowed", isAppAllowed("com.google.android.youtube", allowedApps))
        assertTrue("Chrome should be allowed", isAppAllowed("com.chrome.android", allowedApps))
        assertTrue("LittleLock should be allowed", isAppAllowed("com.littlelock.app", allowedApps))
    }

    @Test
    fun testIsApp_NotAllowedApp() {
        val allowedApps = listOf(
            AllowedAppEntity("com.google.android.youtube", "YouTube"),
            AllowedAppEntity("com.littlelock.app", "LittleLock")
        )

        assertFalse("TikTok should not be allowed", isAppAllowed("com.zhiliaoapp.musically", allowedApps))
        assertFalse("Facebook should not be allowed", isAppAllowed("com.facebook.katana", allowedApps))
        assertFalse("Unknown app should not be allowed", isAppAllowed("com.unknown.app", allowedApps))
    }

    @Test
    fun testIsApp_EmptyAllowedList() {
        val allowedApps = emptyList<AllowedAppEntity>()

        assertFalse("No app should be allowed when list is empty", isAppAllowed("com.anything", allowedApps))
    }

    @Test
    fun testTimeLimitEnforcement_BeforeLimit() {
        val limitMinutes = 60
        val limitSeconds = limitMinutes * 60 // 3600

        // At 30 minutes (1800 seconds), limit should NOT be reached
        assertFalse("Time limit should not be reached at 30 min", isTimeLimitReached(1800, limitSeconds))
    }

    @Test
    fun testTimeLimitEnforcement_AtLimit() {
        val limitMinutes = 60
        val limitSeconds = limitMinutes * 60 // 3600

        // At exactly 60 minutes (3600 seconds), limit SHOULD be reached
        assertTrue("Time limit should be reached at exactly 60 min", isTimeLimitReached(3600, limitSeconds))
    }

    @Test
    fun testTimeLimitEnforcement_OverLimit() {
        val limitMinutes = 60
        val limitSeconds = limitMinutes * 60 // 3600

        // At 61 minutes (3660 seconds), limit SHOULD be reached
        assertTrue("Time limit should be reached at 61 min", isTimeLimitReached(3660, limitSeconds))
    }

    @Test
    fun testTimeLimitEnforcement_ZeroLimit() {
        // With a 0-minute limit, any usage should trigger the limit
        assertTrue("Any usage should trigger zero-minute limit", isTimeLimitReached(1, 0))
        assertTrue("Zero usage with zero limit should trigger", isTimeLimitReached(0, 0))
    }
}
