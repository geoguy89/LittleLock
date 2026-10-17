package com.littlelock.app

/**
 * Decides whether an app that just came to the front must be sent back to the LittleLock home screen.
 */
object Enforcer {

    /** Parts of the system the child always needs (status bar, dialogs) - never blocked. */
    val ALWAYS_ALLOWED = setOf("android", "com.android.systemui")

    fun shouldSendHome(
        packageName: String?,
        ownPackage: String,
        allowed: Set<String>,
        exempt: Set<String>,
        kidMode: Boolean,
        parentUnlocked: Boolean
    ): Boolean {
        if (!kidMode || parentUnlocked || packageName.isNullOrEmpty()) return false
        if (packageName == ownPackage || packageName in ALWAYS_ALLOWED) return false
        return packageName !in allowed && packageName !in exempt
    }
}
