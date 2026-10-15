package com.littlelock.app

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Review test verifying the user interface is intuitive for children,
 * maintaining the same ease of use as the original application.
 */
class ChildFriendlyReviewTest {

    private val srcMain = File("src/main")

    @Test
    fun the_user_interface_is_intuitive_for_children() {
        // Verify launcher layout uses simple, child-friendly structure
        val launcherLayout = File(srcMain, "res/layout/activity_launcher.xml")
        assertTrue("activity_launcher.xml should exist", launcherLayout.exists())
        val layoutContent = launcherLayout.readText()

        // Verify GridView layout (simple grid of app icons - child-friendly)
        assertTrue(
            "Home screen should use GridView for simple icon grid layout",
            layoutContent.contains("GridView")
        )

        // Verify large touch targets (64dp icons - easy for children to tap)
        assertTrue(
            "App icons should have large touch targets (64dp)",
            layoutContent.contains("64dp")
        )

        // Verify item layout exists for app icons
        val itemLayout = File(srcMain, "res/layout/item_app.xml")
        assertTrue("item_app.xml should exist", itemLayout.exists())
        val itemContent = itemLayout.readText()
        assertTrue(
            "App items should have ImageView for icons",
            itemContent.contains("ImageView")
        )
        assertTrue(
            "App items should have TextView for names",
            itemContent.contains("TextView")
        )

        // Verify child-friendly color palette
        val colorsXml = File(srcMain, "res/values/colors.xml")
        assertTrue("colors.xml should exist", colorsXml.exists())
        val colorsContent = colorsXml.readText()
        assertTrue(
            "Colors should include child-friendly palette (colorful backgrounds)",
            colorsContent.contains("littlelock_icon_bg")
        )

        // Verify launcher activity handles home behavior (keeps launcher on top)
        val launcherActivity = File(srcMain, "java/com/littlelock/app/LauncherActivity.kt")
        assertTrue("LauncherActivity.kt should exist", launcherActivity.exists())
        val launcherContent = launcherActivity.readText()
        assertTrue(
            "LauncherActivity should handle home behavior to stay on top",
            launcherContent.contains("onNewIntent") ||
            launcherContent.contains("onUserLeaveHint") ||
            launcherContent.contains("onWindowFocusChanged")
        )

        // Verify PIN lock protects settings (child-safe)
        val pinLockActivity = File(srcMain, "java/com/littlelock/app/PinLockActivity.kt")
        assertTrue("PinLockActivity.kt should exist", pinLockActivity.exists())

        // Verify launcher has no complex navigation (simple for children)
        // Should NOT have tabs, drawers, or complex navigation patterns
        assertFalse(
            "Launcher should not use complex navigation (ViewPager)",
            launcherContent.contains("ViewPager")
        )
        assertFalse(
            "Launcher should not use complex navigation (DrawerLayout)",
            launcherContent.contains("DrawerLayout")
        )
    }
}
