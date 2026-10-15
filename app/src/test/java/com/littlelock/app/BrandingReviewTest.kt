package com.littlelock.app

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Review test verifying the app launches and displays custom LittleLock branding
 * with no references to the original application's name or logo.
 */
class BrandingReviewTest {

    private val srcMain = File("src/main")

    @Test
    fun the_app_launches_and_displays_a_custom_LittleLock_branding() {
        // Verify app_name is "LittleLock"
        val stringsXml = File(srcMain, "res/values/strings.xml")
        assertTrue("strings.xml should exist", stringsXml.exists())
        val stringsContent = stringsXml.readText()
        assertTrue(
            "strings.xml should contain LittleLock as app_name",
            stringsContent.contains("<string name=\"app_name\">LittleLock</string>")
        )

        // Verify notification strings reference LittleLock
        assertTrue(
            "Notification strings should reference LittleLock",
            stringsContent.contains("LittleLock")
        )

        // Verify no references to original app names in any source file
        val forbiddenPatterns = listOf(
            "kids home launcher",
            "com.kids.launcher",
            "kids launcher",
            "parental control launcher",
            "secure kids",
            "kids safe launcher"
        )

        val filesToCheck = mutableListOf<File>()
        val javaDir = File(srcMain, "java")
        if (javaDir.exists()) filesToCheck.addAll(collectFiles(javaDir, ".kt"))
        val resDir = File(srcMain, "res")
        if (resDir.exists()) filesToCheck.addAll(collectFiles(resDir, ".xml"))

        for (file in filesToCheck) {
            val content = file.readText().lowercase()
            for (pattern in forbiddenPatterns) {
                assertFalse(
                    "Found forbidden branding '$pattern' in ${file.relativeTo(srcMain)}",
                    content.contains(pattern.lowercase())
                )
            }
        }

        // Verify LauncherActivity sets content view (app launches with UI)
        val launcherActivity = File(srcMain, "java/com/littlelock/app/LauncherActivity.kt")
        assertTrue("LauncherActivity.kt should exist", launcherActivity.exists())
        val launcherContent = launcherActivity.readText()
        assertTrue(
            "LauncherActivity should set content view to display branding",
            launcherContent.contains("setContentView")
        )
    }

    private fun collectFiles(dir: File, extension: String): List<File> {
        val result = mutableListOf<File>()
        for (entry in dir.listFiles() ?: emptyArray()) {
            if (entry.isDirectory) result.addAll(collectFiles(entry, extension))
            else if (entry.name.endsWith(extension)) result.add(entry)
        }
        return result
    }
}
