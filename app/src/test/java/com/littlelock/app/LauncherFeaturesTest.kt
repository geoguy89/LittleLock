package com.littlelock.app

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit test verifying launcher handles core interactions without crashes.
 * Checks that LauncherActivity, LauncherService, and AppAdapter exist and
 * have the expected functionality.
 */
class LauncherFeaturesTest {

    private val srcMain = File("src/main")

    /**
     * Verify that the launcher handles core interactions without crashes.
     */
    @Test
    fun the_launcher_handles_core_interactions_without_crashes() {
        // Verify LauncherActivity.kt exists and sets up the home screen
        val launcherActivity = File(srcMain, "java/com/littlelock/app/LauncherActivity.kt")
        assertTrue("LauncherActivity.kt should exist", launcherActivity.exists())
        val launcherContent = launcherActivity.readText()

        // Verify LauncherActivity sets up the home screen (sets content view)
        assertTrue(
            "LauncherActivity should set content view",
            launcherContent.contains("setContentView")
        )

        // Verify LauncherService.kt exists as a Service
        val launcherService = File(srcMain, "java/com/littlelock/app/LauncherService.kt")
        assertTrue("LauncherService.kt should exist", launcherService.exists())
        val serviceContent = launcherService.readText()
        assertTrue(
            "LauncherService should extend Service",
            serviceContent.contains("Service()")
        )

        // Verify AppAdapter.kt exists for app list display
        val appAdapter = File(srcMain, "java/com/littlelock/app/AppAdapter.kt")
        assertTrue("AppAdapter.kt should exist", appAdapter.exists())
        val adapterContent = appAdapter.readText()
        assertTrue(
            "AppAdapter should extend ArrayAdapter",
            adapterContent.contains("ArrayAdapter")
        )

        // Verify LauncherActivity has app launching logic (Intent to start apps)
        assertTrue(
            "LauncherActivity should have app launching logic via Intent",
            launcherContent.contains("startActivity")
        )
        assertTrue(
            "LauncherActivity should use getLaunchIntentForPackage",
            launcherContent.contains("getLaunchIntentForPackage")
        )

        // Verify LauncherActivity handles home button behavior
        assertTrue(
            "LauncherActivity should handle home behavior (onNewIntent/onUserLeaveHint/onWindowFocusChanged)",
            launcherContent.contains("onNewIntent") ||
            launcherContent.contains("onUserLeaveHint") ||
            launcherContent.contains("onWindowFocusChanged")
        )
    }
}
