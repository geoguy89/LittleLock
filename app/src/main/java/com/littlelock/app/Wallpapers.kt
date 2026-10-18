package com.littlelock.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import kotlin.math.max

/**
 * The cartoon-animal wallpapers (drawn by tools/gen_wallpapers.py as vector drawables) and how a wallpaper becomes a picture
 * the size of the screen. A wallpaper is scaled to cover the screen and centred, so a tall phone and a wide tablet both
 * show the animal in the middle.
 */
object Wallpapers {

    /** Key -> the drawable. The keys are the ones [Look.ANIMALS] lists. */
    fun resOf(key: String): Int = when (key) {
        "cat" -> R.drawable.wp_cat
        "dog" -> R.drawable.wp_dog
        "bunny" -> R.drawable.wp_bunny
        "bear" -> R.drawable.wp_bear
        "panda" -> R.drawable.wp_panda
        "fox" -> R.drawable.wp_fox
        "penguin" -> R.drawable.wp_penguin
        "owl" -> R.drawable.wp_owl
        "elephant" -> R.drawable.wp_elephant
        "frog" -> R.drawable.wp_frog
        else -> 0
    }

    private var cached: Triple<String, Int, Bitmap>? = null

    /** The animal wallpaper [key] drawn to cover a [width] x [height] screen, or null when there is no such animal. */
    fun render(context: Context, key: String, width: Int, height: Int): Bitmap? {
        val res = resOf(key)
        if (res == 0 || width <= 0 || height <= 0) return null
        cached?.let { (k, sizeKey, bmp) -> if (k == key && sizeKey == width * 31 + height) return bmp }
        val drawable = ContextCompat.getDrawable(context, res)?.mutate() ?: return null
        val side = 600
        val scale = max(width / side.toFloat(), height / side.toFloat())
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.translate((width - side * scale) / 2f, (height - side * scale) / 2f)
        canvas.scale(scale, scale)
        drawable.setBounds(0, 0, side, side)
        drawable.draw(canvas)
        cached = Triple(key, width * 31 + height, bmp)
        return bmp
    }

    /** Is this picture mostly dark? Sampled, not scanned: then the app names are drawn in white, otherwise in dark text. */
    fun isDark(bitmap: Bitmap): Boolean {
        var sum = 0.0
        var n = 0
        val stepX = max(1, bitmap.width / 24)
        val stepY = max(1, bitmap.height / 24)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val p = bitmap.getPixel(x, y)
                sum += luminance((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
                n++
                x += stepX
            }
            y += stepY
        }
        return n > 0 && sum / n < 140.0
    }

    fun luminance(r: Int, g: Int, b: Int): Double = 0.299 * r + 0.587 * g + 0.114 * b
}
