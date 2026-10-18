package com.littlelock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class DiscordRemoteTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val now = 1_800_000_000_000L
    private val parent = "111"
    private fun msg(id: Long, text: String, author: String = parent, bot: Boolean = false, age: Long = 5_000) =
        DiscordRemote.Msg(id.toString(), author, bot, text, now - age)

    private val locked = TimeLimit.Settings(limitOn = true, limitMinutes = 30, usedMs = 40 * 60_000L)

    @Test
    fun a_parents_extend_opens_the_tablet_and_is_answered() {
        val o = DiscordRemote.handle(listOf(msg(10, "extend 30")), setOf(parent), "5", locked, now, utc)
        assertFalse(TimeLimit.state(o.settings, now).locked)
        assertEquals(1, o.replies.size)
        assertEquals("10", o.lastId)
    }

    @Test
    fun a_stranger_a_bot_or_ordinary_chat_does_nothing() {
        val o = DiscordRemote.handle(listOf(msg(10, "extend 30", author = "999"), msg(11, "extend 30", bot = true), msg(12, "dinner at 6")), setOf(parent), "5", locked, now, utc)
        assertTrue(TimeLimit.state(o.settings, now).locked)
        assertTrue(o.replies.isEmpty())
        assertEquals("12", o.lastId)
    }

    @Test
    fun old_commands_are_not_replayed() {
        val o = DiscordRemote.handle(listOf(msg(10, "extend 30", age = 3_600_000)), setOf(parent), "5", locked, now, utc)
        assertTrue(TimeLimit.state(o.settings, now).locked)
        val seen = DiscordRemote.handle(listOf(msg(10, "extend 30")), setOf(parent), "10", locked, now, utc)
        assertTrue(TimeLimit.state(seen.settings, now).locked)
    }

    @Test
    fun several_commands_run_in_order() {
        val o = DiscordRemote.handle(listOf(msg(12, "lock"), msg(11, "extend 1h")), setOf(parent), "5", locked, now, utc)
        assertTrue(TimeLimit.state(o.settings, now).locked)            // the lock came last
        assertEquals(2, o.replies.size)
    }

    @Test
    fun nobody_is_allowed_until_the_parent_lists_an_id() {
        val o = DiscordRemote.handle(listOf(msg(10, "unlock")), emptySet(), "5", locked.copy(forceLock = true), now, utc)
        assertTrue(TimeLimit.state(o.settings, now).locked)
    }

    @Test
    fun user_ids_are_read_from_a_pasted_list_and_the_newest_message_is_the_baseline() {
        assertEquals(setOf("123", "456"), DiscordRemote.users("123, 456;abc  "))
        assertEquals("30", DiscordRemote.newest(listOf(msg(20, "x"), msg(30, "y"))))
        assertEquals("40", DiscordRemote.newest(listOf(msg(20, "x")), "40"))
    }
}
