package com.littlelock.app

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Android 15 draws apps edge to edge: content runs under the status bar, the navigation bar and a camera cut-out, so a
 * button near an edge can be covered or unreachable. A screen the parent must always be able to use (the PIN pad, the
 * lock button, settings) pads itself by the system bars so everything sits inside the clickable area.
 */
fun View.applySystemBarInsets(extraTopDp: Int = 0, extraBottomDp: Int = 0, accentBars: Boolean = true) {
    val base = intArrayOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
    val accent = if (accentBars) AccentBars(Accent.current(context).argb) else null
    if (accent != null) {
        overlay.add(accent)
        addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> accent.setBounds(0, 0, v.width, v.height); accent.invalidateSelf() }
    }
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        accent?.set(bars.top, bars.bottom)
        val d = v.resources.displayMetrics.density
        v.setPadding(
            base[0] + bars.left,
            base[1] + bars.top + (extraTopDp * d).toInt(),
            base[2] + bars.right,
            base[3] + bars.bottom + (extraBottomDp * d).toInt()
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/** The accent colour behind the status bar (top) and the navigation buttons (bottom), drawn over the padded-in strips. */
class AccentBars(private val color: Int) : android.graphics.drawable.Drawable() {
    private var top = 0
    private var bottom = 0
    private val paint = android.graphics.Paint().apply { this.color = this@AccentBars.color }

    fun set(topPx: Int, bottomPx: Int) {
        top = topPx
        bottom = bottomPx
        invalidateSelf()
    }

    override fun draw(canvas: android.graphics.Canvas) {
        val b = bounds
        if (top > 0) canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), (b.top + top).toFloat(), paint)
        if (bottom > 0) canvas.drawRect(b.left.toFloat(), (b.bottom - bottom).toFloat(), b.right.toFloat(), b.bottom.toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
