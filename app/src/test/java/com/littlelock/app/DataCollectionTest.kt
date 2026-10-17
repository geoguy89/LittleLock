package com.littlelock.app

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit test verifying no unauthorized data collection.
 * Searches for Firebase, analytics, telemetry, crashlytics, sentry, tracking,
 * google.analytics, facebook.sdk, admob, ads, advertisement.
 */
class DataCollectionTest {

    private val srcMain = File("src/main")

    /**
     * Verify that no unauthorized data collection code exists.
     */
    @Test
    fun the_app_collects_no_unauthorized_personal_data() {
        val forbiddenPatterns = listOf(
            "firebase",
            "analytics",
            "telemetry",
            "crashlytics",
            "sentry",
            "tracking",
            "google.analytics",
            "facebook.sdk",
            "admob",
            "ads",
            "advertisement"
        )

        val filesToCheck = mutableListOf<File>()

        // Scan .kt files
        val javaDir = File(srcMain, "java")
        if (javaDir.exists()) {
            filesToCheck.addAll(collectFiles(javaDir, ".kt"))
        }

        // Scan .xml files in res
        val resDir = File(srcMain, "res")
        if (resDir.exists()) {
            filesToCheck.addAll(collectFiles(resDir, ".xml"))
        }

        for (file in filesToCheck) {
            val content = file.readText().lowercase()
            for (pattern in forbiddenPatterns) {
                // whole words only: "ads" must not match "padStart", "buy" not "buyer's guide" in a variable name
                assertFalse(
                    "Found '$pattern' in ${file.relativeTo(srcMain)}",
                    Regex("\\b" + Regex.escape(pattern.lowercase()) + "\\b").containsMatchIn(content)
                )
            }
        }
    }

    private fun collectFiles(dir: File, extension: String): List<File> {
        val result = mutableListOf<File>()
        for (entry in dir.listFiles() ?: emptyArray()) {
            if (entry.isDirectory) {
                result.addAll(collectFiles(entry, extension))
            } else if (entry.name.endsWith(extension)) {
                result.add(entry)
            }
        }
        return result
    }
}
