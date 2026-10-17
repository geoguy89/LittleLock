package com.littlelock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LookTest {

    @Test
    fun tiles_get_bigger_and_the_default_is_large() {
        val icons = Look.TILES.map { it.iconDp }
        assertEquals(icons.sorted(), icons)
        assertEquals("Large", Look.tile(Look.DEFAULT_TILE).name)
        assertTrue(Look.tile(Look.DEFAULT_TILE).iconDp > 64)
    }

    @Test
    fun an_unknown_tile_size_falls_back_into_range() {
        assertEquals(Look.TILES.first(), Look.tile(-5))
        assertEquals(Look.TILES.last(), Look.tile(99))
    }

    @Test
    fun a_wallpaper_colour_round_trips_and_other_values_are_not_colours() {
        val c = Look.COLORS[3].second
        assertEquals(c, Look.colorOf(Look.colorValue(c)))
        assertNull(Look.colorOf(Look.IMAGE))
        assertNull(Look.colorOf(""))
        assertNull(Look.colorOf(null))
        assertNull(Look.colorOf("color:zzz"))
    }

    @Test
    fun dark_wallpapers_are_told_from_light_ones() {
        assertTrue(Look.isDark(0xFF23224A.toInt()))
        assertFalse(Look.isDark(0xFFFFE9A8.toInt()))
    }

    @Test
    fun the_greeting_uses_the_kids_name_when_there_is_one() {
        assertEquals("Hi, Ava!", Look.greeting("  Ava ", "My Apps"))
        assertEquals("My Apps", Look.greeting("   ", "My Apps"))
        assertEquals("My Apps", Look.greeting(null, "My Apps"))
    }
}
