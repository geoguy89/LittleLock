package com.littlelock.app

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit test verifying no paywall or subscription code exists.
 * Searches all .kt and .xml files for monetization-related strings.
 */
class PaywallTest {

    private val srcMain = File("src/main")

    /**
     * Verify that no paywall/subscription code exists in source files.
     */
    @Test
    fun the_app_has_no_paywall_prompts() {
        val forbiddenPatterns = listOf(
            "paywall",
            "subscription",
            "premium",
            "pro version",
            "upgrade",
            "purchase",
            "buy",
            "paid",
            "monet",
            "in-app purchase",
            "iap"
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
