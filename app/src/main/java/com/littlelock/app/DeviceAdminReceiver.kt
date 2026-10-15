package com.littlelock.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device admin receiver for enhanced device control.
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        const val ACTION_DEVICE_ADMIN_ENABLE = "android.app.action.DEVICE_ADMIN_ENABLE"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}
