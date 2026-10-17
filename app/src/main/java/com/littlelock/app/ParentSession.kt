package com.littlelock.app

/**
 * The parent's unlocked window after entering the PIN. While it lasts the parent can use parent settings and any
 * app (for example Android's own settings, to turn on the accessibility service); it ends when they leave parent
 * settings, or after [WINDOW_MS] without them coming back to it.
 */
object ParentSession {

    const val WINDOW_MS = 10 * 60 * 1000L

    @Volatile
    private var unlockedUntil = 0L

    fun unlock(now: Long = System.currentTimeMillis()) {
        unlockedUntil = now + WINDOW_MS
    }

    fun lock() {
        unlockedUntil = 0L
    }

    fun isUnlocked(now: Long = System.currentTimeMillis()): Boolean = now < unlockedUntil
}
