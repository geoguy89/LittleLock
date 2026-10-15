package com.littlelock.app

import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit test that verifies the APK exists and passes apksigner verification.
 * Uses ProcessBuilder to run apksigner and check exit code is 0.
 * Skips gracefully when the APK or apksigner is not available (e.g. in CI).
 */
class SigningTest {

    private val sdkPath = System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: ""

    private val apksignerPath = if (sdkPath.isNotEmpty()) {
        "$sdkPath/build-tools/34.0.0/lib/apksigner.jar"
    } else {
        ""
    }

    private val apkPath = "app/build/outputs/apk/release/LittleLock-release.apk"

    /**
     * Verify the release APK exists and passes apksigner verification.
     */
    @Test
    fun the_generated_apk_is_signed_and_verifiable() {
        val apkFile = java.io.File(apkPath)

        // Skip the test if the APK hasn't been built yet
        assumeTrue("Release APK should exist at $apkPath", apkFile.exists())
        assertTrue("Release APK should not be empty", apkFile.length() > 0)

        val apksignerFile = java.io.File(apksignerPath)
        if (sdkPath.isEmpty() || !apksignerFile.exists()) {
            // If ANDROID_HOME isn't set or apksigner.jar is not available,
            // skip verification but still verify APK exists
            return
        }

        // Run apksigner verify --verbose
        val processBuilder = ProcessBuilder(
            "java", "-jar", apksignerPath, "verify", "--verbose", apkPath
        )
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()

        // Read output for debugging
        val output = process.inputStream.bufferedReader().readText()

        val exitCode = process.waitFor()
        assertEquals(
            "APK should pass apksigner verification (exit code 0). Output: $output",
            0,
            exitCode
        )
    }
}
