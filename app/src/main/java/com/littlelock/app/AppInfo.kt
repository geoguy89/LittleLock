package com.littlelock.app

/**
 * Data class representing an installed application.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val iconResId: Int
)
