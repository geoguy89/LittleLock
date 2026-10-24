package com.littlelock.app

import android.content.Context
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

/** One child on the "who is using the tablet?" screen: a big round picture over their name (and a small lock when they have a PIN). */
class ProfileCard(context: Context) : LinearLayout(context) {

    private val picture = ImageView(context).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    private val label = TextView(context).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val d = resources.displayMetrics.density
        addView(picture, LayoutParams((PICTURE_DP * d).toInt(), (PICTURE_DP * d).toInt()))
        addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * d).toInt() })
        val tv = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) setBackgroundResource(tv.resourceId)
        isClickable = true
        isFocusable = true
    }

    fun bind(p: Profile, lightText: Boolean) {
        val shown = p.copy(avatar = if (Avatars.drawable(context, p) == null) Avatars.value("cat") else p.avatar)
        Avatars.show(picture, shown, hideWhenNone = false)
        val name = p.name.ifEmpty { context.getString(R.string.profile_unnamed) }
        label.text = if (p.hasPin) "$name  🔒" else name
        contentDescription = name
        if (lightText) {
            label.setTextColor(Color.WHITE)
            label.setShadowLayer(4f, 0f, 1f, Color.BLACK)
        } else {
            label.setTextColor(context.getColor(R.color.littlelock_text_primary))
            label.setShadowLayer(0f, 0f, 0f, 0)
        }
    }

    companion object {
        const val PICTURE_DP = 120
    }
}

/** The profile cards, centred across the screen however many there are (see [Layouts]). */
class ProfileGrid @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {

    private var result: Layouts.Result? = null
    private val density get() = resources.displayMetrics.density

    fun setProfiles(profiles: List<Profile>, lightText: Boolean, onPick: (Profile) -> Unit) {
        removeAllViews()
        profiles.forEach { p ->
            addView(ProfileCard(context).also {
                it.bind(p, lightText)
                it.setOnClickListener { _ -> onPick(p) }
            })
        }
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val availW = max(0, w - paddingLeft - paddingRight)
        val cellW = (CELL_W_DP * density).toInt()
        val cellH = (CELL_H_DP * density).toInt()
        val r = Layouts.arrange(childCount, availW, 0, cellW, cellH, (16 * density).toInt(), 1f, Layouts.TOP)
        result = r
        for (i in 0 until childCount) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(w, r.contentH + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val res = result ?: return
        for (i in 0 until minOf(childCount, res.cells.size)) {
            val cell = res.cells[i]
            val x = paddingLeft + cell.x
            val y = paddingTop + cell.y
            getChildAt(i).layout(x, y, x + res.cellW, y + res.cellH)
        }
    }

    fun cardAt(index: Int): View? = getChildAt(index)

    private companion object {
        const val CELL_W_DP = 170
        const val CELL_H_DP = 190
    }
}
