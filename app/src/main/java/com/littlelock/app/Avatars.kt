package com.littlelock.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import androidx.core.content.ContextCompat
import java.io.File

/**
 * The pictures a child's profile can wear: cartoon faces drawn by tools/gen_avatars.py, or a picture the parent imports
 * (cropped square and kept inside the app). A stored avatar value is "" (none), "a:<key>" or [IMAGE].
 */
object Avatars {

    /** key -> the name the parent sees. */
    val BUILT_IN = listOf("cat" to "Cat", "dog" to "Puppy", "bunny" to "Bunny", "bear" to "Bear", "panda" to "Panda", "fox" to "Fox",
        "penguin" to "Penguin", "owl" to "Owl", "frog" to "Frog", "monkey" to "Monkey", "lion" to "Lion", "pig" to "Piggy",
        "robot" to "Robot", "alien" to "Alien", "dino" to "Dino", "unicorn" to "Unicorn")

    const val IMAGE = "image"
    private const val PREFIX = "a:"
    private const val SIDE = 320

    fun value(key: String): String = PREFIX + key

    /** The built-in key inside a stored value, or null when it isn't one. */
    fun keyOf(value: String?): String? {
        if (value == null || !value.startsWith(PREFIX)) return null
        return value.removePrefix(PREFIX).takeIf { k -> BUILT_IN.any { it.first == k } }
    }

    fun resOf(key: String): Int = when (key) {
        "cat" -> R.drawable.av_cat
        "dog" -> R.drawable.av_dog
        "bunny" -> R.drawable.av_bunny
        "bear" -> R.drawable.av_bear
        "panda" -> R.drawable.av_panda
        "fox" -> R.drawable.av_fox
        "penguin" -> R.drawable.av_penguin
        "owl" -> R.drawable.av_owl
        "frog" -> R.drawable.av_frog
        "monkey" -> R.drawable.av_monkey
        "lion" -> R.drawable.av_lion
        "pig" -> R.drawable.av_pig
        "robot" -> R.drawable.av_robot
        "alien" -> R.drawable.av_alien
        "dino" -> R.drawable.av_dino
        "unicorn" -> R.drawable.av_unicorn
        else -> 0
    }

    fun file(context: Context, id: String): File = File(context.filesDir, "avatar_$id.jpg")

    /** The picture for [p], or null when it has none. */
    fun drawable(context: Context, p: Profile): Drawable? {
        keyOf(p.avatar)?.let { return ContextCompat.getDrawable(context, resOf(it)) }
        if (p.avatar == IMAGE) {
            val f = file(context, p.id)
            if (f.isFile) BitmapFactory.decodeFile(f.path)?.let { return BitmapDrawable(context.resources, it) }
        }
        return null
    }

    /** Puts [p]'s picture in [view] as a circle; the view is hidden when the profile has none. */
    fun show(view: ImageView, p: Profile, hideWhenNone: Boolean = true) {
        val d = drawable(view.context, p)
        view.setImageDrawable(d)
        view.scaleType = ImageView.ScaleType.CENTER_CROP
        view.outlineProvider = oval
        view.clipToOutline = true
        view.visibility = if (d == null && hideWhenNone) View.GONE else View.VISIBLE
    }

    private val oval = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }

    /** Saves the picture at [uri] as [id]'s avatar: cropped to a square in the middle and scaled down. */
    fun import(context: Context, id: String, uri: Uri): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SIDE && bounds.outHeight / (sample * 2) >= SIDE) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalStateException("unreadable")
        val square = Math.min(bmp.width, bmp.height)
        val cropped = Bitmap.createBitmap(bmp, (bmp.width - square) / 2, (bmp.height - square) / 2, square, square)
        val out = Bitmap.createScaledBitmap(cropped, SIDE, SIDE, true)
        file(context, id).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    } catch (e: Exception) {
        false
    }
}
