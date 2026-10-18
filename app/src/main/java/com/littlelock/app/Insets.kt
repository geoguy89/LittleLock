package com.littlelock.app

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Android 15 draws apps edge to edge: content runs under the status bar, the navigation bar and a camera cut-out, so a
 * button near an edge can be covered or unreachable. A screen the parent must always be able to use (the PIN pad, the
 * lock button, settings) pads itself by the system bars so everything sits inside the clickable area.
 */
fun View.applySystemBarInsets(extraTopDp: Int = 0, extraBottomDp: Int = 0) {
    val base = intArrayOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
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
