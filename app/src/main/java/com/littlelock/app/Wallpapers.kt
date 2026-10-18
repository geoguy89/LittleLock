package com.littlelock.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import java.io.File
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
        "monkey" -> R.drawable.wp_monkey
        else -> 0
    }

    /** Key -> the built-in photo (a JPEG in drawable-nodpi). */
    fun photoRes(key: String): Int = when (key) {
        "kitten" -> R.drawable.ph_kitten
        "labpup" -> R.drawable.ph_labpup
        "puppy" -> R.drawable.ph_puppy
        "ducklings" -> R.drawable.ph_ducklings
        "chicks" -> R.drawable.ph_chicks
        "rabbit" -> R.drawable.ph_rabbit
        "panda" -> R.drawable.ph_panda
        "pandas" -> R.drawable.ph_pandas
        "elephant" -> R.drawable.ph_elephant
        "giraffe" -> R.drawable.ph_giraffe
        "penguin" -> R.drawable.ph_penguin
        "lioncub" -> R.drawable.ph_lioncub
        "goat" -> R.drawable.ph_goat
        "lamb" -> R.drawable.ph_lamb
        "hedgehog" -> R.drawable.ph_hedgehog
        "fawn" -> R.drawable.ph_fawn
        "butterflies" -> R.drawable.ph_butterflies
        "rainbow" -> R.drawable.ph_rainbow
        "monkeylook" -> R.drawable.ph_monkeylook
        "monkeyface" -> R.drawable.ph_monkeyface
        "monkeytrio" -> R.drawable.ph_monkeytrio
        "babymonkey" -> R.drawable.ph_babymonkey
        "monkeyhug" -> R.drawable.ph_monkeyhug
        "babyorang" -> R.drawable.ph_babyorang
        "orangutans" -> R.drawable.ph_orangutans
        "capuchin" -> R.drawable.ph_capuchin
        "monkeybrow" -> R.drawable.ph_monkeybrow
        else -> 0
    }

    private var cached: Triple<String, Int, Bitmap>? = null

    /** [source] scaled to cover a [width] x [height] screen and cropped to the middle - never stretched. */
    fun cover(source: Bitmap, width: Int, height: Int): Bitmap {
        if (source.width <= 0 || source.height <= 0 || width <= 0 || height <= 0) return source
        val scale = max(width / source.width.toFloat(), height / source.height.toFloat())
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.translate((width - source.width * scale) / 2f, (height - source.height * scale) / 2f)
        canvas.scale(scale, scale)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /**
     * The picture for a stored wallpaper value - an animal, a built-in photo, or the parent's own picture - fitted to a
     * [width] x [height] screen; null for a colour, no wallpaper, or something that can't be read.
     */
    fun forValue(context: Context, value: String, width: Int, height: Int): Bitmap? {
        Look.animalOf(value)?.let { return render(context, it, width, height) }
        Look.photoOf(value)?.let { key ->
            val res = photoRes(key)
            if (res == 0) return null
            val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
            val bmp = BitmapFactory.decodeResource(context.resources, res, opts) ?: return null
            return cover(bmp, width, height)
        }
        if (value == Look.IMAGE) {
            val file = File(context.filesDir, LauncherActivity.WALLPAPER_FILE)
            val bmp = if (file.isFile) BitmapFactory.decodeFile(file.path) else null
            return bmp?.let { cover(it, width, height) }
        }
        return null
    }

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
