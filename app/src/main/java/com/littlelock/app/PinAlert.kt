package com.littlelock.app

import android.content.Context
import java.util.TimeZone

/** Tells the parent in Discord when someone types a wrong PIN - and says whether it happened during bedtime or a lock. */
object PinAlert {

    /** The words of the alert: [why] is what the tablet was doing, [wrongInRow] which wrong try in a row this was. */
    fun text(why: TimeLimit.Why, wrongInRow: Int, lockedOut: Boolean, now: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val during = when (why) {
            TimeLimit.Why.BEDTIME -> "during the bedtime lockdown"
            TimeLimit.Why.FORCED -> "while the tablet was locked"
            TimeLimit.Why.LIMIT -> "after today's time ran out"
            TimeLimit.Why.OPEN -> "while the tablet was open"
        }
        val tries = if (lockedOut) "That was wrong try ${Preferences.MAX_ATTEMPTS} in a row - the PIN pad is locked for ${Preferences.LOCKOUT_MS / 1000} seconds."
                    else "Wrong try $wrongInRow in a row."
        return "A wrong PIN was typed $during, at ${TimeLimit.clockText(now, tz)}. $tries"
    }

    /** Sends the alert (if the parent has a webhook and left this alert on). [wrongInRow] comes from [Preferences.recordFailedPin]. */
    fun send(context: Context, wrongInRow: Int) {
        val url = Preferences.getWebhookUrl(context)
        if (url.isNullOrEmpty() || !Preferences.isWrongPinNotifEnabled(context)) return
        val now = System.currentTimeMillis()
        val why = TimeLimit.state(Preferences.timeSettings(context, now), now).why
        val body = text(why, wrongInRow, wrongInRow >= Preferences.MAX_ATTEMPTS, now)
        Thread { DiscordWebhookService.sendNote(url, "🔑 Wrong PIN", body) }.start()
    }
}
