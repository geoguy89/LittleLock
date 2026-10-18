package com.littlelock.app

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Where the app tiles go on the child's home screen. However many apps the parent chose, they sit together in the middle of the
 * screen across (and, by default, down; the parent can also pick top or bottom) - with the last, shorter row centred too, and the rows kept even (7 apps on a 4-wide screen are 4 and
 * 3, not 4, 2 and 1). Plain numbers in, positions out, so it can be tested without a phone.
 */
object Layouts {

    /** Tiles at the size the parent picked, centred. */
    const val GRID = 0

    /** The biggest tiles that still let every app fit on the screen at once, centred (few apps: big; many apps: smaller). */
    const val AUTO = 1

    val NAMES = listOf("Centered, at the size I picked", "Fill the screen (biggest that fits)")

    /** Where the block of apps sits down the screen. */
    const val TOP = 0
    const val MIDDLE = 1
    const val BOTTOM = 2

    val ALIGN_NAMES = listOf("Top", "Middle", "Bottom")

    /** Space between tiles: the picked step (0..4) in dp. */
    val GAP_DP = listOf(0, 6, 10, 18, 28)
    const val DEFAULT_GAP = 2

    fun gapDp(step: Int): Int = GAP_DP[step.coerceIn(0, GAP_DP.size - 1)]

    data class Cell(val x: Int, val y: Int)

    class Result(
        val cols: Int,
        val rows: Int,
        val cellW: Int,
        val cellH: Int,
        val cells: List<Cell>,
        val contentW: Int,
        val contentH: Int,
        val scale: Float
    )

    /** Positions for [count] tiles of [cellW] x [cellH] with [gap] between them, centred in [availW] x [availH]. */
    fun arrange(count: Int, availW: Int, availH: Int, cellW: Int, cellH: Int, gap: Int, scale: Float = 1f, align: Int = MIDDLE): Result {
        if (count <= 0 || cellW <= 0 || cellH <= 0) return Result(0, 0, cellW, cellH, emptyList(), 0, 0, scale)
        val maxCols = max(1, (availW + gap) / (cellW + gap))
        val rows = ceil(count / maxCols.toDouble()).toInt()
        val cols = ceil(count / rows.toDouble()).toInt()                    // even rows: the fewest columns that still use [rows] rows
        val contentH = rows * cellH + (rows - 1) * gap
        val contentW = cols * cellW + (cols - 1) * gap
        val spare = availH - contentH
        val top = if (spare <= 0) 0 else when (align) {                      // taller than the screen: start at the top and scroll
            TOP -> 0
            BOTTOM -> spare
            else -> spare / 2
        }
        val cells = ArrayList<Cell>(count)
        var i = 0
        for (r in 0 until rows) {
            val inRow = min(cols, count - i)
            val rowW = inRow * cellW + (inRow - 1) * gap
            val left = max(0, (availW - rowW) / 2)
            for (c in 0 until inRow) {
                cells.add(Cell(left + c * (cellW + gap), top + r * (cellH + gap)))
                i++
            }
        }
        return Result(cols, rows, cellW, cellH, cells, contentW, contentH, scale)
    }

    /** The largest tiles (up to [maxScale] times the base size) for which all [count] apps fit in [availW] x [availH]; else the base size. */
    fun autoFit(count: Int, availW: Int, availH: Int, baseW: Int, baseH: Int, gap: Int, maxScale: Float = 2.4f, align: Int = MIDDLE): Result {
        var s = maxScale
        while (s > 1.0f) {
            val r = arrange(count, availW, availH, (baseW * s).toInt(), (baseH * s).toInt(), gap, s, align)
            if (r.contentH <= availH && r.contentW <= availW) return r
            s -= 0.1f
        }
        return arrange(count, availW, availH, baseW, baseH, gap, 1f, align)
    }

    fun forMode(mode: Int, count: Int, availW: Int, availH: Int, baseW: Int, baseH: Int, gap: Int, align: Int = MIDDLE): Result =
        if (mode == AUTO) autoFit(count, availW, availH, baseW, baseH, gap, align = align)
        else arrange(count, availW, availH, baseW, baseH, gap, 1f, align)
}
