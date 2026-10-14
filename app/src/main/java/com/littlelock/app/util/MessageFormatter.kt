package com.littlelock.app.util

/**
 * Utility functions for Discord webhook message formatting.
 * Extracted from DiscordWebhookService for testability.
 */
object MessageFormatter {

    /**
     * Format seconds into a human-readable duration string.
     * e.g., 120 -> "2 m", 3725 -> "1 h 2 m"
     */
    fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) {
            "$hours h $minutes m"
        } else {
            "$minutes m"
        }
    }
}
