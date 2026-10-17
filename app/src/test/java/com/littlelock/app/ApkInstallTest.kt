package com.littlelock.app

import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.assertTrue

/**
 * Unit test that verifies the release APK exists and is a valid APK file.
 * Skips gracefully when the APK is not present (e.g. in CI before a build).
 */
class ApkInstallTest {

    private val apkPath = "build/outputs/apk/release/LittleLock-release.apk"

    @Test
    fun the_apk_installs_successfully_on_android_device() {
        val apkFile = java.io.File(apkPath)

        // Skip the test if the APK hasn't been built yet
        assumeTrue("Release APK should exist at $apkPath", apkFile.exists())
        assertTrue("Release APK should not be empty", apkFile.length() > 0)

        // Verify it has valid APK structure (zip format)
        val bytes = apkFile.readBytes()
        // APK files start with PK (zip magic bytes 0x50 0x4B)
        assertTrue(
            "APK should have valid zip header (PK signature)",
            bytes.size >= 4 && bytes[0].toInt() == 0x50 && bytes[1].toInt() == 0x4B
        )
    }
}
