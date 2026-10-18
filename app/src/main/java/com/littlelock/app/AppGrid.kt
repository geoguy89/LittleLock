package com.littlelock.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One app on the home screen: its icon over its name, sized by the grid ([Layouts]). */
class AppTile(context: Context) : LinearLayout(context) {

    private val icon = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val label = TextView(context).apply {
        gravity = Gravity.CENTER
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private var lastIcon = -1
    private var lastText = -1f

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        addView(icon, LayoutParams(0, 0))
        addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = 4 })
        val tv = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) setBackgroundResource(tv.resourceId)
        isClickable = true
        isFocusable = true
    }

    fun bind(app: AppInfo, lightText: Boolean) {
        try {
            icon.setImageDrawable(context.packageManager.getApplicationIcon(app.packageName))
        } catch (e: PackageManager.NameNotFoundException) {
            icon.setImageResource(R.drawable.ic_app_icon_1)
        }
        label.text = app.label
        contentDescription = app.label
        if (lightText) {
            label.setTextColor(Color.WHITE)
            label.setShadowLayer(4f, 0f, 1f, Color.BLACK)
        } else {
            label.setTextColor(context.getColor(R.color.littlelock_text_primary))
            label.setShadowLayer(0f, 0f, 0f, 0)
        }
    }

    /** Fit the icon and the name to a [cellW] x [cellH] cell; [scale] is how much bigger than the picked size the cell is. */
    fun size(cellW: Int, cellH: Int, scale: Float, tile: Look.Tile) {
        val d = resources.displayMetrics.density
        val iconPx = max(1, min((tile.iconDp * scale * d).toInt(), min((cellH * 0.62f).toInt(), cellW - (8 * d).toInt())))
        val sp = min(30f, tile.textSp * sqrt(scale))
        if (iconPx != lastIcon) {
            icon.layoutParams = LayoutParams(iconPx, iconPx)
            lastIcon = iconPx
        }
        if (sp != lastText) {
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            lastText = sp
        }
    }
}

/**
 * The child's grid of allowed apps: the tiles sit together in the middle of the area - across and down - however many there
 * are, at the size the parent picked or (in [Layouts.AUTO]) the biggest that lets them all fit. More apps than fit: it starts at
 * the top and the page scrolls.
 */
class AppGrid @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {

    var mode: Int = Layouts.GRID
    var align: Int = Layouts.MIDDLE
    var gapDp: Int = Layouts.gapDp(Layouts.DEFAULT_GAP)
    var tile: Look.Tile = Look.tile(Look.DEFAULT_TILE)
    private var result: Layouts.Result? = null

    private val density get() = resources.displayMetrics.density

    fun setApps(apps: List<AppInfo>, lightText: Boolean, onClick: (AppInfo) -> Unit) {
        removeAllViews()
        apps.forEach { app ->
            addView(AppTile(context).also {
                it.bind(app, lightText)
                it.setOnClickListener { onClick(app) }
            })
        }
        requestLayout()
    }

    private fun baseW() = (tile.cellDp * density).toInt()

    /** A cell tall enough for the icon, a two-line name and some air. */
    private fun baseH() = ((tile.iconDp + tile.textSp * 2.7f + 22f) * density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        val availW = max(0, w - paddingLeft - paddingRight)
        val availH = if (hMode == MeasureSpec.UNSPECIFIED) resources.displayMetrics.heightPixels / 2 else max(0, hSize - paddingTop - paddingBottom)
        val gap = (gapDp * density).toInt()
        val r = Layouts.forMode(mode, childCount, availW, availH, baseW(), baseH(), gap, align)
        result = r
        for (i in 0 until childCount) {
            val c = getChildAt(i) as AppTile
            c.size(r.cellW, r.cellH, r.scale, tile)
            c.measure(MeasureSpec.makeMeasureSpec(r.cellW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(r.cellH, MeasureSpec.EXACTLY))
        }
        val wanted = r.contentH + paddingTop + paddingBottom
        val h = when (hMode) {
            MeasureSpec.EXACTLY -> hSize
            MeasureSpec.AT_MOST -> min(hSize, max(wanted, 0))
            else -> wanted
        }
        setMeasuredDimension(w, max(h, wanted.takeIf { hMode == MeasureSpec.UNSPECIFIED } ?: 0))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val res = result ?: return
        for (i in 0 until min(childCount, res.cells.size)) {
            val cell = res.cells[i]
            val x = paddingLeft + cell.x
            val y = paddingTop + cell.y
            getChildAt(i).layout(x, y, x + res.cellW, y + res.cellH)
        }
    }

    override fun shouldDelayChildPressedState(): Boolean = false

    /** For tests and accessibility: the tile at [index]. */
    fun tileAt(index: Int): View? = getChildAt(index)
}
