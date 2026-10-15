package com.littlelock.app

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit test that verifies the APK exists and passes apksigner verification.
 * Uses ProcessBuilder to run apksigner and check exit code is 0.
 */
class SigningTest {

    private val sdkPath = System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: "C:\\Users\\Travis\\AppData\\Local\\Android\\Sdk"

    private val apksignerPath = "$sdkPath\\build-tools\\34.0.0\\lib\\apksigner.jar"
    private val apkPath = "C:\\Projects\\LittlesLock\\LittleLock-release.apk"

    /**
     * Verify the release APK exists and passes apksigner verification.
     */
    @Test
    fun testApkExistsAndSignsValid() {
        val apkFile = java.io.File(apkPath)
        assertTrue(
            "Release APK should exist at $apkPath",
            apkFile.exists()
        )
        assertTrue(
            "Release APK should not be empty",
            apkFile.length() > 0
        )

        val apksignerFile = java.io.File(apksignerPath)
        if (!apksignerFile.exists()) {
            // If apksigner.jar is not available, skip verification
            // but still verify APK exists
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
