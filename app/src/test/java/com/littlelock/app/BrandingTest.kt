package com.littlelock.app

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit test verifying no references to original app names exist.
 * Ensures branding has been fully replaced with "LittleLock".
 */
class BrandingTest {

    private val projectRoot = File(javaClass.classLoader.getResource(".")?.file?.path ?: "app/src/main")
        .parentFile.parentFile.parentFile.parentFile

    private val srcMain = File(projectRoot, "app/src/main")

    /**
     * Verify that strings.xml contains "LittleLock" as app_name.
     */
    @Test
    fun testAppNameIsLittleLock() {
        val stringsXml = File(srcMain, "res/values/strings.xml")
        assertTrue("strings.xml should exist", stringsXml.exists())

        val content = stringsXml.readText()
        assertTrue(
            "strings.xml should contain LittleLock as app_name",
            content.contains("<string name=\"app_name\">LittleLock</string>")
        )
    }

    /**
     * Verify that no source files contain references to original app names
     * (case-insensitive search across all .kt and .xml files).
     */
    @Test
    fun testNoOriginalAppNames() {
        val forbiddenPatterns = listOf(
            "kids home launcher",
            "com.kids.launcher",
            "kids launcher",
            "parental control launcher"
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
                assertFalse(
                    "Found '$pattern' in ${file.relativeTo(srcMain)}",
                    content.contains(pattern.lowercase())
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
