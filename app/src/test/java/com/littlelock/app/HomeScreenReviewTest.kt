package com.littlelock.app

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Review test verifying the home screen layout and app drawer functionality
 * operate identically to the original launcher without ads or promotional content.
 */
class HomeScreenReviewTest {

    private val srcMain = File("src/main")

    @Test
    fun the_home_screen_layout_and_app_drawer_functionality() {
        // Verify LauncherActivity exists and sets up home screen with GridView
        val launcherActivity = File(srcMain, "java/com/littlelock/app/LauncherActivity.kt")
        assertTrue("LauncherActivity.kt should exist", launcherActivity.exists())
        val launcherContent = launcherActivity.readText()

        // Verify app drawer functionality (queries installed apps)
        assertTrue(
            "LauncherActivity should query installed apps via PackageManager",
            launcherContent.contains("PackageManager")
        )
        assertTrue(
            "LauncherActivity should launch apps via Intent",
            launcherContent.contains("startActivity")
        )
        assertTrue(
            "LauncherActivity should load apps list",
            launcherContent.contains("loadApps")
        )

        // Verify LauncherActivity sets content view to home screen layout
        assertTrue(
            "LauncherActivity should set content view",
            launcherContent.contains("setContentView")
        )

        // Verify activity_launcher.xml has GridView for app drawer
        val launcherLayout = File(srcMain, "res/layout/activity_launcher.xml")
        assertTrue("activity_launcher.xml should exist", launcherLayout.exists())
        val layoutContent = launcherLayout.readText()
        assertTrue(
            "Home screen layout should contain GridView for app drawer",
            layoutContent.contains("GridView")
        )

        // Verify AppAdapter exists for displaying apps
        val appAdapter = File(srcMain, "java/com/littlelock/app/AppAdapter.kt")
        assertTrue("AppAdapter.kt should exist", appAdapter.exists())
        val adapterContent = appAdapter.readText()
        assertTrue(
            "AppAdapter should extend ArrayAdapter",
            adapterContent.contains("ArrayAdapter")
        )

        // Verify no ads or promotional content in any source or layout files
        val filesToCheck = mutableListOf<File>()
        val javaDir = File(srcMain, "java")
        if (javaDir.exists()) filesToCheck.addAll(collectFiles(javaDir, ".kt"))
        val resDir = File(srcMain, "res")
        if (resDir.exists()) filesToCheck.addAll(collectFiles(resDir, ".xml"))

        val forbiddenPatterns = listOf(
            "com.google.android.gms.ads",
            "com.google.firebase.analytics",
            "com.facebook.android",
            "admob",
            "advertisement",
            "promot",
            "sponsor",
            "interstitial",
            "rewarded",
            "native ad",
            "applovin",
            "unityads",
            "ironsource",
            "startapp",
            "tapjoy",
            "chartboost",
            "mobfox",
            "inmobi",
            "smaato",
            "openx"
        )

        for (file in filesToCheck) {
            val content = file.readText().lowercase()
            for (pattern in forbiddenPatterns) {
                assertFalse(
                    "Found ad/promotional content '$pattern' in ${file.relativeTo(srcMain)}",
                    content.contains(pattern.lowercase())
                )
            }
        }
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
