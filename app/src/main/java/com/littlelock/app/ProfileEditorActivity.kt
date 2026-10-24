package com.littlelock.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.littlelock.app.databinding.ActivityProfileEditorBinding

/**
 * Parent screen for one child on the tablet: their name and picture, their own PIN (if wanted), their apps and the order they
 * appear in, and how their home screen looks (colours, wallpaper, size, layout). Every change is saved at once, to that child
 * only. Only reachable through the parent PIN.
 */
class ProfileEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "profile_id"
    }

    private lateinit var binding: ActivityProfileEditorBinding
    private var id = ""
    private var loading = false

    private val pickWallpaper = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) saveWallpaper(uri)
    }

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) saveAvatar(uri)
    }

    private fun profile(): Profile = Profiles.get(this, id) ?: Profile(id)

    private fun edit(change: (Profile) -> Profile) = Profiles.update(this, id, change)

    override fun onCreate(savedInstanceState: Bundle?) {
        id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val start = Profiles.get(this, id)
        if (start == null) {
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        Accent.apply(this, start.accent)                 // the screen wears the colour of the child being edited
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        loading = true
        binding.etKidName.setText(start.name)
        loading = false
        binding.etKidName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!loading) edit { it.copy(name = s?.toString().orEmpty().trim().take(24)) }
            }
        })

        setupLook()
        setupButtons()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return
        if (!ParentSession.isUnlocked()) {
            startActivity(Intent(this, PinLockActivity::class.java)
                .putExtra(PinLockActivity.EXTRA_MODE, PinLockActivity.MODE_ENTER)
                .putExtra(PinLockActivity.EXTRA_NEXT, PinLockActivity.NEXT_SETTINGS))
            finish()
            return
        }
        ParentSession.unlock()
        Kiosk.disengage(this)
        showStatus()
    }

    /** The parts that change when the parent comes back from the PIN pad or the app lists. */
    private fun showStatus() {
        val p = profile()
        Avatars.show(binding.ivAvatarPreview, p, hideWhenNone = false)
        binding.tvPinStatus.setText(if (p.hasPin) R.string.profile_pin_has else R.string.profile_pin_none)
        binding.btnSetPin.setText(if (p.hasPin) R.string.profile_pin_change else R.string.profile_pin_set)
        binding.btnClearPin.visibility = if (p.hasPin) View.VISIBLE else View.GONE
        binding.tvAppsStatus.text = resources.getQuantityString(R.plurals.profile_apps_count, p.allowed.size, p.allowed.size)
        binding.btnDeleteProfile.visibility = if (Profiles.all(this).size > 1) View.VISIBLE else View.GONE
        binding.btnArrangeApps.isEnabled = p.allowed.size > 1
        markAvatars(p.avatar)
        refreshWallPreview()
    }

    private fun setupButtons() {
        binding.btnEditorDone.setOnClickListener { finish() }
        binding.btnAllowedApps.setOnClickListener {
            startActivity(Intent(this, AllowedAppsActivity::class.java).putExtra(AllowedAppsActivity.EXTRA_ID, id))
        }
        binding.btnArrangeApps.setOnClickListener {
            startActivity(Intent(this, ArrangeAppsActivity::class.java).putExtra(ArrangeAppsActivity.EXTRA_ID, id))
        }
        binding.btnSetPin.setOnClickListener {
            startActivity(Intent(this, PinLockActivity::class.java)
                .putExtra(PinLockActivity.EXTRA_MODE, PinLockActivity.MODE_SET)
                .putExtra(PinLockActivity.EXTRA_NEXT, PinLockActivity.NEXT_NONE)
                .putExtra(PinLockActivity.EXTRA_PROFILE, id))
        }
        binding.btnClearPin.setOnClickListener {
            Profiles.clearPin(this, id)
            showStatus()
        }
        binding.btnPickAvatar.setOnClickListener { pickAvatar.launch("image/*") }
        binding.btnClearAvatar.setOnClickListener {
            edit { it.copy(avatar = "") }
            Avatars.file(this, id).delete()
            showStatus()
        }
        binding.btnPickWallpaper.setOnClickListener { pickWallpaper.launch("image/*") }
        binding.btnClearWallpaper.setOnClickListener {
            edit { it.copy(wallpaper = "") }
            Profiles.wallpaperFile(this, id).delete()
            refreshWallPreview()
            Toast.makeText(this, R.string.settings_wallpaper_done, Toast.LENGTH_SHORT).show()
        }
        binding.btnDeleteProfile.setOnClickListener {
            val name = profile().name.ifEmpty { getString(R.string.profile_unnamed) }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.profile_delete_title, name))
                .setMessage(R.string.profile_delete_message)
                .setPositiveButton(R.string.profile_delete_yes) { _, _ ->
                    Profiles.delete(this, id)
                    finish()
                }
                .setNegativeButton(R.string.setup_cancel, null)
                .show()
        }
    }

    // ------------------------------------------------------------------ the look

    private fun setupLook() {
        val p = profile()
        loading = true
        radioChoices(binding.rgTile, Look.TILES.mapIndexed { i, t -> "${t.name}${if (i == Look.DEFAULT_TILE) " (default)" else ""}" }, p.tileSize) { i ->
            edit { it.copy(tileSize = i) }
        }
        radioChoices(binding.rgLayout, Layouts.NAMES, p.layoutMode) { i -> edit { it.copy(layoutMode = i) } }
        radioChoices(binding.rgAlign, Layouts.ALIGN_NAMES, p.align) { i -> edit { it.copy(align = i) } }
        radioChoices(binding.rgGap, listOf("None", "Normal (default)", "Roomy", "Wide", "Very wide", "Huge"), p.gap) { i -> edit { it.copy(gap = i) } }
        loading = false
        addAvatarPicker()
        addAccentPicker()
        addColorPicker()
        addPhotoPicker()
        addAnimalPicker()
    }

    /** Fill [group] with one radio button per name, checking [selected]; [onPick] gets the chosen index. */
    private fun radioChoices(group: RadioGroup, names: List<String>, selected: Int, onPick: (Int) -> Unit) {
        names.forEachIndexed { i, name ->
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = name
                tag = i
            }
            group.addView(rb)
            if (i == selected) group.check(rb.id)
        }
        group.setOnCheckedChangeListener { g, checkedId ->
            if (loading) return@setOnCheckedChangeListener
            val index = g.findViewById<RadioButton>(checkedId)?.tag as? Int ?: return@setOnCheckedChangeListener
            onPick(index)
        }
    }

    private fun circle(argb: Int, ringed: Boolean, size: Int, d: Float) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins((5 * d).toInt(), 0, (5 * d).toInt(), 0) }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(argb)
            setStroke(((if (ringed) 4 else 2) * d).toInt(), if (ringed) 0xFF000000.toInt() else 0x33000000)
        }
        isClickable = true
        isFocusable = true
    }

    /** The picture beside the name: tap a cartoon face; the one picked is full-size, the others a little faded. */
    private fun addAvatarPicker() {
        val d = resources.displayMetrics.density
        val size = (72 * d).toInt()
        Avatars.BUILT_IN.forEach { (key, name) ->
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins((4 * d).toInt(), 0, (4 * d).toInt(), 0) }
                setImageResource(Avatars.resOf(key))
                tag = Avatars.value(key)
                contentDescription = name
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    edit { it.copy(avatar = Avatars.value(key)) }
                    showStatus()
                }
            }
            binding.llAvatars.addView(iv)
        }
    }

    private fun markAvatars(value: String) {
        for (i in 0 until binding.llAvatars.childCount) {
            val v = binding.llAvatars.getChildAt(i)
            val mine = v.tag == value
            v.alpha = if (mine || value.isEmpty() || value == Avatars.IMAGE) 1f else 0.5f
            v.scaleX = if (mine) 1.12f else 1f
            v.scaleY = v.scaleX
        }
    }

    /** The accent colour of the top bar, buttons and headings: a row of swatches; the picked one has a ring. The screen redraws in it. */
    private fun addAccentPicker() {
        val d = resources.displayMetrics.density
        val size = (46 * d).toInt()
        val current = profile().accent
        Accent.ALL.forEach { choice ->
            val v = circle(choice.argb, choice.key == current, size, d).apply {
                contentDescription = choice.name
                setOnClickListener {
                    if (choice.key != profile().accent) {
                        edit { it.copy(accent = choice.key) }
                        recreate()
                    }
                }
            }
            binding.llAccent.addView(v)
        }
    }

    /** What the child's home screen will look like: the current wallpaper, shown right here as it is picked. */
    private fun refreshWallPreview() {
        val value = profile().wallpaper
        val iv = binding.ivWallPreview
        val color = Look.colorOf(value)
        when {
            color != null -> {
                iv.setImageDrawable(null)
                iv.setBackgroundColor(color)
            }
            else -> {
                val dm = resources.displayMetrics
                val bmp = Wallpapers.forValue(this, value, (dm.widthPixels * 0.6f).toInt(), (140 * dm.density * 1.6f).toInt(), id)
                iv.setBackgroundColor(getColor(R.color.littlelock_background))
                iv.setImageBitmap(bmp)
            }
        }
        markColors(value)
    }

    private fun markColors(value: String) {
        for (i in 0 until binding.llColors.childCount) {
            val v = binding.llColors.getChildAt(i)
            val mine = Look.colorValue(Look.COLORS[i].second)
            v.alpha = if (value == mine || value.isEmpty()) 1f else 0.55f
            v.scaleX = if (value == mine) 1.15f else 1f
            v.scaleY = v.scaleX
        }
    }

    private fun addColorPicker() {
        val d = resources.displayMetrics.density
        val size = (46 * d).toInt()
        Look.COLORS.forEach { (name, argb) ->
            val v = circle(argb, false, size, d).apply {
                contentDescription = name
                setOnClickListener {
                    edit { it.copy(wallpaper = Look.colorValue(argb)) }
                    refreshWallPreview()
                    Toast.makeText(this@ProfileEditorActivity, name, Toast.LENGTH_SHORT).show()
                }
            }
            binding.llColors.addView(v)
        }
        refreshWallPreview()
    }

    /** A row of built-in photos (public domain); tapping one makes it the wallpaper. */
    private fun addPhotoPicker() {
        val d = resources.displayMetrics.density
        val size = (92 * d).toInt()
        Look.PHOTOS.forEach { (key, name) ->
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins((4 * d).toInt(), 0, (4 * d).toInt(), 0) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(thumbnail(Wallpapers.photoRes(key), size))
                contentDescription = name
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    edit { it.copy(wallpaper = Look.photoValue(key)) }
                    refreshWallPreview()
                    Toast.makeText(this@ProfileEditorActivity, name, Toast.LENGTH_SHORT).show()
                }
            }
            binding.llPhotos.addView(iv)
        }
    }

    /** A small version of a built-in photo for the picker (the full-size JPEG is not decoded for a thumbnail). */
    private fun thumbnail(res: Int, px: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, res, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= px) sample *= 2
        return BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** A row of the cartoon animals to choose from; tapping one makes it the wallpaper. */
    private fun addAnimalPicker() {
        val d = resources.displayMetrics.density
        val size = (92 * d).toInt()
        Look.ANIMALS.forEach { (key, name) ->
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins((4 * d).toInt(), 0, (4 * d).toInt(), 0) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(Wallpapers.resOf(key))
                contentDescription = name
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    edit { it.copy(wallpaper = Look.animalValue(key)) }
                    refreshWallPreview()
                    Toast.makeText(this@ProfileEditorActivity, name, Toast.LENGTH_SHORT).show()
                }
            }
            binding.llAnimals.addView(iv)
        }
    }

    /** Copies the chosen picture into the app (scaled down to fit a screen) and uses it as this child's wallpaper. */
    private fun saveWallpaper(uri: Uri) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 2000 || bounds.outHeight / sample > 2000) sample *= 2
            val bmp = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: throw IllegalStateException("unreadable")
            Profiles.wallpaperFile(this, id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            edit { it.copy(wallpaper = Look.IMAGE) }
            refreshWallPreview()
            Toast.makeText(this, R.string.settings_wallpaper_done, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.settings_wallpaper_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun saveAvatar(uri: Uri) {
        if (Avatars.import(this, id, uri)) {
            edit { it.copy(avatar = Avatars.IMAGE) }
            showStatus()
        } else {
            Toast.makeText(this, R.string.settings_wallpaper_failed, Toast.LENGTH_LONG).show()
        }
    }
}
