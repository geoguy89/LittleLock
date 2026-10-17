package com.littlelock.app

import android.content.Intent
import android.content.pm.PackageManager

/**
 * Decides which apps the child sees. Only apps the parent allowed are shown - an app that isn't on the list
 * (Settings, the Play Store, a browser...) never appears, however it got installed.
 */
object AppFilter {

    /** Apps that let a child change the device or install things - flagged in the parent's list. */
    val RISKY_PACKAGES = setOf(
        "com.android.settings",
        "com.android.vending",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller"
    )

    /** What the child's home screen shows: allowed apps only, never LittleLock itself, sorted by name. */
    fun visibleApps(all: List<AppInfo>, allowed: Set<String>, ownPackage: String): List<AppInfo> =
        choosableApps(all, ownPackage).filter { it.packageName in allowed }

    /** What the parent can pick from: every launchable app except LittleLock, one entry per app, sorted by name. */
    fun choosableApps(all: List<AppInfo>, ownPackage: String): List<AppInfo> =
        all.filter { it.packageName != ownPackage }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }

    /** Every app with a launcher icon on this device. */
    fun launchableApps(pm: PackageManager): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0).map { info ->
            AppInfo(info.loadLabel(pm).toString(), info.activityInfo.packageName)
        }
    }
}
