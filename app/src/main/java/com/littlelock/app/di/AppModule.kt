package com.littlelock.app.di

import com.littlelock.app.data.local.AppDatabase
import com.littlelock.app.data.local.SettingsRepository
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

/**
 * Koin dependency injection module.
 */
object AppModule {

    val modules = module {
        // Repository
        single { SettingsRepository(androidApplication()) }

        // Database
        single { AppDatabase.getInstance(androidApplication()) }
        single { get<AppDatabase>().allowedAppDao() }
        single { get<AppDatabase>().usageSessionDao() }
    }
}
