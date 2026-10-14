package com.littlelock.app.data.local

import androidx.room.*

/**
 * DAO for allowed apps.
 */
@Dao
interface AllowedAppDao {

    @Query("SELECT * FROM allowed_apps WHERE isAllowed = 1")
    suspend fun getAllowedApps(): List<AllowedAppEntity>

    @Query("SELECT * FROM allowed_apps")
    suspend fun getAllApps(): List<AllowedAppEntity>

    @Query("SELECT * FROM allowed_apps WHERE packageName = :packageName")
    suspend fun getAppByPackageName(packageName: String): AllowedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: AllowedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<AllowedAppEntity>)

    @Query("DELETE FROM allowed_apps WHERE packageName = :packageName")
    suspend fun deleteApp(packageName: String)

    @Query("UPDATE allowed_apps SET isAllowed = :allowed WHERE packageName = :packageName")
    suspend fun setAllowed(packageName: String, allowed: Boolean)

    @Query("DELETE FROM allowed_apps")
    suspend fun deleteAll()
}
