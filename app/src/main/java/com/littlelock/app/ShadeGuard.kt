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

    /** Is this window event the system's notification shade / quick settings opening? */
    fun isShade(packageName: String?, className: String?): Boolean =
        packageName == "com.android.systemui" && className != null && SHADE_CLASSES.containsMatchIn(className)

    /** Height of the shield: the status bar plus a little, so the very top edge can't be pulled from. */
    fun shieldHeightPx(statusBarPx: Int, density: Float): Int = statusBarPx + (6 * density).toInt()
}
