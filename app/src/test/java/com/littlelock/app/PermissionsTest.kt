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
    fun the_app_respects_system_permissions() {
        assertTrue("AndroidManifest.xml should exist", manifestPath.exists())

        val content = manifestPath.readText()

        // Required permissions - must be present
        val requiredPermissions = listOf(
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.BIND_ACCESSIBILITY_SERVICE"
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
            "android.permission.CAMERA",
            // not needed by a launcher - and the ones Google Play flags
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.PACKAGE_USAGE_STATS",
            "android.permission.GET_TASKS",
            "android.permission.WRITE_SETTINGS",
            "shortService"
        )

        for (permission in forbiddenPermissions) {
            assertFalse(
                "Manifest should NOT contain dangerous permission: $permission",
                content.contains(permission)
            )
        }
    }
}
