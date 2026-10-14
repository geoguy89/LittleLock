package com.littlelock.app.data.local

import androidx.room.*
import java.time.LocalDateTime

/**
 * DAO for usage sessions.
 */
@Dao
interface UsageSessionDao {

    @Query("SELECT * FROM usage_sessions WHERE startTime >= :since ORDER BY startTime DESC")
    suspend fun getSessionsSince(since: LocalDateTime): List<UsageSessionEntity>

    @Query("SELECT * FROM usage_sessions ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int = 50): List<UsageSessionEntity>

    @Query("DELETE FROM usage_sessions WHERE startTime < :before")
    suspend fun deleteOldSessions(before: LocalDateTime)

    @Insert
    suspend fun insertSession(session: UsageSessionEntity): Long

    @Insert
    suspend fun insertSessions(sessions: List<UsageSessionEntity>)

    @Query("DELETE FROM usage_sessions")
    suspend fun deleteAll()
}
