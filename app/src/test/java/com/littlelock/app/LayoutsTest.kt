package com.littlelock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutsTest {

    @Test
    fun one_app_sits_in_the_middle_of_the_screen() {
        val r = Layouts.arrange(1, 1000, 1600, 200, 240, 10)
        assertEquals(Layouts.Cell(400, 680), r.cells.single())
    }

    @Test
    fun the_last_shorter_row_is_centred_and_rows_are_kept_even() {
        val r = Layouts.arrange(7, 1000, 1600, 200, 240, 10)         // 4 fit across -> 2 rows of 4 and 3
        assertEquals(4, r.cols)
        assertEquals(2, r.rows)
        val second = r.cells.drop(4)
        assertEquals(3, second.size)
        assertEquals(190, second.first().x)                           // (1000 - (3*200 + 2*10)) / 2
        assertEquals(second.first().y, second.last().y)
        assertEquals(7, r.cells.size)
    }

    @Test
    fun a_wide_screen_does_not_leave_a_stray_app_on_its_own_row() {
        val r = Layouts.arrange(7, 1000, 1600, 200, 240, 10)
        assertTrue(r.cells.groupBy { it.y }.values.none { it.size == 1 })
    }

    @Test
    fun the_whole_block_is_centred_down_the_screen_too() {
        val r = Layouts.arrange(8, 1000, 1600, 200, 240, 10)
        val top = r.cells.minOf { it.y }
        val bottom = r.cells.maxOf { it.y } + 240
        assertEquals(1600 - bottom, top)
    }

    @Test
    fun more_apps_than_fit_start_at_the_top_so_the_screen_can_scroll() {
        val r = Layouts.arrange(60, 1000, 800, 200, 240, 10)
        assertEquals(0, r.cells.minOf { it.y })
        assertTrue(r.contentH > 800)
    }

    @Test
    fun no_apps_gives_nothing() {
        assertTrue(Layouts.arrange(0, 1000, 1600, 200, 240, 10).cells.isEmpty())
    }

    @Test
    fun auto_fit_makes_few_apps_big_and_many_apps_smaller_and_never_too_big_for_the_screen() {
        val few = Layouts.autoFit(2, 1000, 1600, 200, 240, 10)
        val many = Layouts.autoFit(12, 1000, 1600, 200, 240, 10)
        assertTrue(few.scale > many.scale)
        for (r in listOf(few, many)) {
            assertTrue(r.contentW <= 1000)
            assertTrue(r.contentH <= 1600)
        }
        assertEquals(12, many.cells.size)
    }

    @Test
    fun auto_fit_falls_back_to_the_picked_size_when_nothing_fits() {
        val r = Layouts.autoFit(80, 1000, 800, 200, 240, 10)
        assertEquals(1f, r.scale, 0.001f)
    }

    @Test
    fun the_mode_picks_the_function() {
        assertEquals(1f, Layouts.forMode(Layouts.GRID, 2, 1000, 1600, 200, 240, 10).scale, 0.001f)
        assertTrue(Layouts.forMode(Layouts.AUTO, 2, 1000, 1600, 200, 240, 10).scale > 1f)
    }
}
