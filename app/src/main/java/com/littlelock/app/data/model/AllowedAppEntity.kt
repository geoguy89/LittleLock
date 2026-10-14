package com.littlelock.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an allowed app in the child launcher.
 */
@Entity(tableName = "allowed_apps")
data class AllowedAppEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val iconResId: Int = 0,
    val isAllowed: Boolean = true,
    val dailyTimeLimitMinutes: Int = 0 // 0 = unlimited
)
