package com.littlelock.app

/**
 * Keeping the child out of the notification shade and quick settings (the toggles for Wi-Fi, airplane mode, ...).
 * Two layers, both run by [AccessibilityMonitorService]:
 *  1. a transparent shield over the status-bar strip that swallows the swipe-down, so the shade never opens;
 *  2. if it opens anyway (a notification tap, an unusual phone), it is closed again the moment it shows.
 * Neither applies while kid mode is off, the parent is signed in, or the parent turned the option off.
 */
object ShadeGuard {

    private val SHADE_CLASSES = Regex("(?i)(notificationshade|notificationpanel|quicksettings|qspanel|shadewindow|statusbarwindow)")

    /** Should the shield be up right now? */
    fun shieldWanted(kidMode: Boolean, parentSignedIn: Boolean, optionOn: Boolean): Boolean =
        kidMode && !parentSignedIn && optionOn

    private val SHADE_WORDS = Regex("(?i)(notification shade|quick settings|notification panel|control center)")

    /**
     * Is this window event the system's notification shade / quick settings opening? Phone makers name the classes
     * differently (and Amazon's Fire tablets use their own system UI), so the window's own title counts too.
     */
    fun isShade(packageName: String?, className: String?, text: String = ""): Boolean {
        if (packageName == null || !packageName.contains("systemui", ignoreCase = true)) return false
        return (className != null && SHADE_CLASSES.containsMatchIn(className)) || SHADE_WORDS.containsMatchIn(text)
    }

    /** Is a system window of this package that tall (a share of the screen) the shade pulled down? Bars and toasts are small. */
    fun isShadeWindow(packageName: String?, heightPx: Int, screenHeightPx: Int, isSystemWindow: Boolean): Boolean =
        isSystemWindow && packageName != null && packageName.contains("systemui", ignoreCase = true) && heightPx > screenHeightPx * 0.3

    /** Height of the shield: the status bar itself - the app's content starts right below it (see [applySystemBarInsets]). */
    fun shieldHeightPx(statusBarPx: Int): Int = statusBarPx
}
