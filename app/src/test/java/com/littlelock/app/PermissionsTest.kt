package com.littlelock.app

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit test that reads AndroidManifest.xml and verifies proper permissions.
 * Checks for required permissions and absence of dangerous ones.
 */
class PermissionsTest {

    private val manifestPath = File("src/main/AndroidManifest.xml")

    /**
     * Verify required permissions are present and dangerous ones are absent.
     */
    @Test
    fun testPermissions() {
        assertTrue("AndroidManifest.xml should exist", manifestPath.exists())

        val content = manifestPath.readText()

        // Required permissions - must be present
        val requiredPermissions = listOf(
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.SET_WALLPAPER",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.FOREGROUND_SERVICE"
        )

        for (permission in requiredPermissions) {
            assertTrue(
                "Manifest should contain permission: $permission",
                content.contains(permission)
            )
        }

        // Dangerous permissions - must NOT be present
        val forbiddenPermissions = listOf(
            "android.permission.READ_CONTACTS",
            "android.permission.READ_PHONE_STATE",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.CAMERA"
        )

        for (permission in forbiddenPermissions) {
            assertFalse(
                "Manifest should NOT contain dangerous permission: $permission",
                content.contains(permission)
            )
        }
    }
}
