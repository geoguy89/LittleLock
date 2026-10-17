package com.littlelock.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf

/** Installs pretend apps (with a launcher icon) into Robolectric's package manager. */
object TestApps {

    val context: Context get() = ApplicationProvider.getApplicationContext()

    fun install(packageName: String, label: String) {
        val app = ApplicationInfo().apply {
            this.packageName = packageName
            nonLocalizedLabel = label
        }
        val pm = shadowOf(context.packageManager)
        pm.installPackage(PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = app
        })
        val component = ComponentName(packageName, "$packageName.MainActivity")
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        )
    }

    /** A parent who has set up: PIN 1234, kid mode on, these apps allowed. */
    fun parentSetUp(vararg allowed: String) {
        Preferences.setPin(context, "1234")
        Preferences.setKidMode(context, true)
        Preferences.setAllowedApps(context, allowed.toSet())
        ParentSession.lock()
    }
}
