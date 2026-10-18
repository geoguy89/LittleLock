package com.littlelock.app

import android.app.Activity
import android.content.Context

/**
 * The colour the parent picked for the top bar, buttons and headings. The picked colour is applied as a theme overlay
 * (Accent.<Name> in themes.xml) before an activity draws, so every screen follows it.
 */
object Accent {

    class Choice(val key: String, val name: String, val argb: Int, val styleRes: Int)

    const val DEFAULT = "purple"

    val ALL = listOf(
        Choice("purple", "Purple", 0xFF6C63FF.toInt(), R.style.Accent_Purple),
        Choice("blue", "Blue", 0xFF2F80ED.toInt(), R.style.Accent_Blue),
        Choice("teal", "Teal", 0xFF00A896.toInt(), R.style.Accent_Teal),
        Choice("green", "Green", 0xFF3BAA4A.toInt(), R.style.Accent_Green),
        Choice("orange", "Orange", 0xFFF2842B.toInt(), R.style.Accent_Orange),
        Choice("red", "Red", 0xFFE5484D.toInt(), R.style.Accent_Red),
        Choice("pink", "Pink", 0xFFE4559B.toInt(), R.style.Accent_Pink),
        Choice("gray", "Gray", 0xFF5F6B7A.toInt(), R.style.Accent_Gray)
    )

    fun choice(key: String?): Choice = ALL.firstOrNull { it.key == key } ?: ALL.first()

    fun current(context: Context): Choice = choice(Preferences.getAccent(context))

    /** Call in onCreate before the layout is inflated. */
    fun apply(activity: Activity) {
        activity.theme.applyStyle(current(activity).styleRes, true)
    }

    /** The colour with [alpha] (0..255), e.g. for the translucent bar on the home screen. */
    fun withAlpha(argb: Int, alpha: Int): Int = (argb and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
