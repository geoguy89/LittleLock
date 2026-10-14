package com.littlelock.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a usage session for a child app.
 */
@Entity(tableName = "usage_sessions")
data class UsageSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationSeconds: Long
)
