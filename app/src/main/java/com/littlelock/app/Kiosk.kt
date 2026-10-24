package com.littlelock.app

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.inputmethod.InputMethodManager

/**
 * Kiosk lockdown - the real way to lock a tablet down. Accessibility can send the child back when another app shows up,
 * but only Android's own "lock task" mode actually removes the ways out: no notification pane or quick settings, no Home
 * or Recents buttons, and only the apps the parent allowed can start. It needs LittleLock to be the device owner, which is
 * set once from a computer (no accounts on the tablet):
 *
 *     adb shell dpm set-device-owner com.littlelock.app/.DeviceAdminReceiver
 *
 * With that done LittleLock also becomes the permanent home app (no "choose a launcher" step), and the parent PIN is the
 * only way out: opening parent settings lifts the lockdown, leaving them puts it back. Parent settings can also give the
 * device-owner role up again.
 */
object Kiosk {

    /** The one command the parent runs from a computer. */
    fun adbCommand(ownPackage: String): String = "adb shell dpm set-device-owner $ownPackage/.DeviceAdminReceiver"

    /** Packages lock-task mode may run: the allowed apps, LittleLock itself, and the keyboards. */
    fun lockTaskPackages(allowed: Set<String>, own: String, keyboards: Set<String>): Array<String> =
        (allowed + own + keyboards).filter { it.isNotBlank() }.toSortedSet().toTypedArray()

    /** Should the lockdown be on right now? */
    fun wanted(kioskOn: Boolean, isOwner: Boolean, kidMode: Boolean, parentUnlocked: Boolean): Boolean =
        kioskOn && isOwner && kidMode && !parentUnlocked

    private fun dpm(ctx: Context): DevicePolicyManager? = ctx.getSystemService(DevicePolicyManager::class.java)

    private fun admin(ctx: Context) = ComponentName(ctx, DeviceAdminReceiver::class.java)

    fun isOwner(ctx: Context): Boolean = try {
        dpm(ctx)?.isDeviceOwnerApp(ctx.packageName) == true
    } catch (e: Exception) {
        false
    }

    private fun keyboards(ctx: Context): Set<String> = try {
        ctx.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
    } catch (e: Exception) {
        emptySet()
    }

    /** Lock the tablet down (idempotent): the allowed apps only, no notification pane, LittleLock as the permanent home. */
    fun engage(activity: Activity) {
        if (!isOwner(activity)) return
        val d = dpm(activity) ?: return
        val a = admin(activity)
        try {
            d.setLockTaskPackages(a, lockTaskPackages(Profiles.allowedEverywhere(activity), activity.packageName, keyboards(activity)))
            d.setLockTaskFeatures(a, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS)   // the power button's menu (power off / restart) still works
            d.setStatusBarDisabled(a, true)
            val home = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            d.addPersistentPreferredActivity(a, home, ComponentName(activity, LauncherActivity::class.java))
            val am = activity.getSystemService(ActivityManager::class.java)
            if (am?.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) activity.startLockTask()
        } catch (e: Exception) {
            android.util.Log.w("Kiosk", "couldn't lock down: ${e.message}")
        }
    }

    /** Lift the lockdown (the parent is in, or kid mode is off). */
    fun disengage(activity: Activity) {
        if (!isOwner(activity)) return
        val d = dpm(activity) ?: return
        try {
            val am = activity.getSystemService(ActivityManager::class.java)
            if (am?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) activity.stopLockTask()
            d.setStatusBarDisabled(admin(activity), false)
        } catch (e: Exception) {
            android.util.Log.w("Kiosk", "couldn't lift the lockdown: ${e.message}")
        }
    }

    /** Give the device-owner role up (and the permanent home with it): the tablet is an ordinary tablet again. */
    fun release(activity: Activity): Boolean {
        if (!isOwner(activity)) return false
        val d = dpm(activity) ?: return false
        return try {
            disengage(activity)
            d.clearPackagePersistentPreferredActivities(admin(activity), activity.packageName)
            d.setLockTaskPackages(admin(activity), emptyArray())
            @Suppress("DEPRECATION")
            d.clearDeviceOwnerApp(activity.packageName)
            true
        } catch (e: Exception) {
            false
        }
    }
}
