package com.littlelock.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Entity representing a usage session for a child app.
 */
@Entity(tableName = "usage_sessions")
data class UsageSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val durationSeconds: Long
)
