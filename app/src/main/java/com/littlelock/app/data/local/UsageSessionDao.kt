package com.littlelock.app.data.local

import androidx.room.*

/**
 * DAO for usage sessions.
 */
@Dao
interface UsageSessionDao {

    @Query("SELECT * FROM usage_sessions WHERE startTimeMillis >= :since ORDER BY startTimeMillis DESC")
    suspend fun getSessionsSince(since: Long): List<UsageSessionEntity>

    @Query("SELECT * FROM usage_sessions ORDER BY startTimeMillis DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int = 50): List<UsageSessionEntity>

    @Query("DELETE FROM usage_sessions WHERE startTimeMillis < :before")
    suspend fun deleteOldSessions(before: Long)

    @Insert
    suspend fun insertSession(session: UsageSessionEntity): Long

    @Insert
    suspend fun insertSessions(sessions: List<UsageSessionEntity>)

    @Query("DELETE FROM usage_sessions")
    suspend fun deleteAll()
}
