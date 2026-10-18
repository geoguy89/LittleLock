package com.littlelock.app

import java.util.TimeZone

/**
 * The optional Discord remote: the parent posts a command in a private channel (from their phone, anywhere) and the tablet obeys
 * it. Only messages from the users (id or name) the parent listed are acted on; bots, strangers, ordinary chat and anything older than a few
 * minutes are ignored. This file only decides what a batch of messages means - fetching and replying live in
 * [DiscordWebhookService].
 */
object DiscordRemote {

    /** A message as fetched from the channel. [id] is Discord's numeric message id (later = bigger). */
    class Msg(val id: String, val authorId: String, val isBot: Boolean, val content: String, val timestampMs: Long, val authorNames: Set<String> = emptySet())

    class Outcome(val settings: TimeLimit.Settings, val replies: List<String>, val lastId: String)

    /** A command older than this is stale (the tablet was off, or a message was left lying around) and is not run. */
    const val MAX_AGE_MS = 10 * 60_000L

    /** The newest message id: where to start from, so commands already in the channel are never replayed. */
    fun newest(messages: List<Msg>, current: String = ""): String =
        (messages.map { it.id } + listOfNotNull(current.takeIf { it.isNotEmpty() })).maxByOrNull { it.toLongOrNull() ?: 0L } ?: current

    /** Who may give commands: numeric Discord user ids, or user names (without the @, any capitals). */
    fun users(text: String): Set<String> =
        text.split(',', ' ', ';', '\n').map { it.trim().removePrefix("@") }.filter { it.isNotEmpty() }.map { if (it.all(Char::isDigit)) it else it.lowercase() }.toSet()

    /** Runs the commands in [messages] (oldest first) for the [allowed] users, returning the new settings and what to answer. */
    fun handle(
        messages: List<Msg>,
        allowed: Set<String>,
        lastId: String,
        settings: TimeLimit.Settings,
        now: Long,
        tz: TimeZone = TimeZone.getDefault()
    ): Outcome {
        var s = settings
        val replies = ArrayList<String>()
        val last = lastId.toLongOrNull() ?: 0L
        for (m in messages.sortedBy { it.id.toLongOrNull() ?: 0L }) {
            if ((m.id.toLongOrNull() ?: 0L) <= last) continue
            if (m.isBot || (m.authorId !in allowed && m.authorNames.none { it in allowed })) continue
            if (now - m.timestampMs > MAX_AGE_MS) continue
            val cmd = RemoteCommands.parse(m.content) ?: continue
            val r = RemoteCommands.apply(s, cmd, now, tz)
            s = r.settings
            replies.add(r.reply)
        }
        return Outcome(s, replies, newest(messages, lastId))
    }
}
