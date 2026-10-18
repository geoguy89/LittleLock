package com.littlelock.app

/**
 * Screen timeout inside LittleLock. When LittleLock is showing (the apps, or the time-up page) and nobody touches the screen for
 * the minutes the parent chose, the service puts the screen to sleep - so a child can't leave it on all night.
 */
object Idle {

    /** Choices in Settings: minutes, 0 = never. */
    val CHOICES = listOf(1, 2, 5, 10, 0)
    const val DEFAULT_MINUTES = 2

    @Volatile
    var lastTouch = System.currentTimeMillis()
        private set

    fun touch(now: Long = System.currentTimeMillis()) {
        lastTouch = now
    }

    fun name(minutes: Int): String = if (minutes == 0) "Never" else if (minutes == 1) "1 minute" else "$minutes minutes"

    /** True when the screen should be put to sleep now. */
    fun shouldSleep(minutes: Int, lastTouch: Long, now: Long, litelockInFront: Boolean, screenOn: Boolean): Boolean =
        minutes > 0 && screenOn && litelockInFront && now - lastTouch >= minutes * 60_000L
}
