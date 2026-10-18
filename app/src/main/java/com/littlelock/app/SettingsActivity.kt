package com.littlelock.app

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.app.TimePickerDialog
import android.content.ClipData
import android.widget.SeekBar
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.littlelock.app.databinding.ActivitySettingsBinding
import java.io.File

/**
 * Parent settings. Only reachable through the parent PIN: opened without an unlocked [ParentSession] it sends the
 * parent to the PIN pad instead. Leaving it (Done / Back) locks again.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val deviceAdminResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Toast.makeText(this, R.string.device_admin_updated, Toast.LENGTH_SHORT).show()
        showStatus()
    }

    private val accessibilityResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        showStatus()
    }

    /** Android's own "make this your home app" question; if the phone ignores it we walk the parent to the setting. */
    private val homeRoleResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        showStatus()
        if (!isHomeApp()) explainHomeSettings()
    }

    private val pickPicture = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) saveWallpaper(uri)
    }

    private var loadingLook = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        loadSettings()
        setupLook()
        setupListeners()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
    }

    override fun onResume() {
        super.onResume()
        if (!ParentSession.isUnlocked()) {
            startActivity(Intent(this, PinLockActivity::class.java)
                .putExtra(PinLockActivity.EXTRA_MODE, PinLockActivity.MODE_ENTER)
                .putExtra(PinLockActivity.EXTRA_NEXT, PinLockActivity.NEXT_SETTINGS))
            finish()
            return
        }
        ParentSession.unlock() // still here: keep the parent signed in
        Kiosk.disengage(this)  // the parent is in: lift the lockdown so Android's own settings can be opened
        showStatus()
    }

    private fun loadSettings() {
        binding.etWebhookUrl.setText(Preferences.getWebhookUrl(this) ?: "")
        binding.cbNotifAppLaunch.isChecked = Preferences.isAppLaunchNotifEnabled(this)
        binding.cbNotifDailySummary.isChecked = Preferences.isDailySummaryEnabled(this)
        binding.cbNotifBlockedApps.isChecked = Preferences.isBlockedAppsNotifEnabled(this)
        binding.cbBlockShade.isChecked = Preferences.isBlockShade(this)
        binding.etKidName.setText(Preferences.getKidName(this))
        binding.etDiscordToken.setText(Preferences.getDiscordToken(this))
        binding.etDiscordChannel.setText(Preferences.getDiscordChannel(this))
        binding.etDiscordUsers.setText(Preferences.getDiscordUsers(this))
        binding.cbRemote.isChecked = Preferences.isRemoteOn(this)
        loadTime()

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
        binding.tvVersion.text = getString(R.string.settings_version, version)
    }

    /** Tile-size choices, wallpaper colours. */
    private fun setupLook() {
        loadingLook = true
        Look.TILES.forEachIndexed { i, tile ->
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = "${tile.name}${if (i == Look.DEFAULT_TILE) " (default)" else ""}"
                tag = i
            }
            binding.rgTile.addView(rb)
            if (i == Preferences.getTileSize(this)) binding.rgTile.check(rb.id)
        }
        binding.rgTile.setOnCheckedChangeListener { group, checkedId ->
            if (loadingLook) return@setOnCheckedChangeListener
            val index = group.findViewById<RadioButton>(checkedId)?.tag as? Int ?: return@setOnCheckedChangeListener
            Preferences.setTileSize(this, index)
        }
        Layouts.NAMES.forEachIndexed { i, name ->
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = name
                tag = i
            }
            binding.rgLayout.addView(rb)
            if (i == Preferences.getLayoutMode(this)) binding.rgLayout.check(rb.id)
        }
        binding.rgLayout.setOnCheckedChangeListener { group, checkedId ->
            if (loadingLook) return@setOnCheckedChangeListener
            val mode = group.findViewById<RadioButton>(checkedId)?.tag as? Int ?: return@setOnCheckedChangeListener
            Preferences.setLayoutMode(this, mode)
        }
        addColorPicker()
        addPhotoPicker()
        addAnimalPicker()
        loadingLook = false
    }

    /** What the child's home screen will look like: the current wallpaper, shown right here as it is picked. */
    private fun refreshWallPreview() {
        val value = Preferences.getWallpaper(this)
        val iv = binding.ivWallPreview
        val color = Look.colorOf(value)
        when {
            color != null -> {
                iv.setImageDrawable(null)
                iv.setBackgroundColor(color)
            }
            else -> {
                val dm = resources.displayMetrics
                val bmp = Wallpapers.forValue(this, value, (dm.widthPixels * 0.6f).toInt(), (140 * dm.density * 1.6f).toInt())
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

    /** Colour swatches: tap one and the wallpaper changes, with a preview right above. */
    private fun addColorPicker() {
        val d = resources.displayMetrics.density
        val size = (46 * d).toInt()
        Look.COLORS.forEach { (name, argb) ->
            val v = View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply { setMargins((5 * d).toInt(), 0, (5 * d).toInt(), 0) }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(argb)
                    setStroke((2 * d).toInt(), 0x33000000)
                }
                contentDescription = name
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    Preferences.setWallpaper(this@SettingsActivity, Look.colorValue(argb))
                    refreshWallPreview()
                    Toast.makeText(this@SettingsActivity, name, Toast.LENGTH_SHORT).show()
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
            val iv = android.widget.ImageView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply { setMargins((4 * d).toInt(), 0, (4 * d).toInt(), 0) }
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                setImageBitmap(thumbnail(Wallpapers.photoRes(key), (size * 1.0f).toInt()))
                contentDescription = name
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    Preferences.setWallpaper(this@SettingsActivity, Look.photoValue(key))
                    refreshWallPreview()
                    Toast.makeText(this@SettingsActivity, name, Toast.LENGTH_SHORT).show()
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
            val iv = android.widget.ImageView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply { setMargins((4 * d).toInt(), 0, (4 * d).toInt(), 0) }
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                setImageResource(Wallpapers.resOf(key))
                contentDescription = name
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    Preferences.setWallpaper(this@SettingsActivity, Look.animalValue(key))
                    refreshWallPreview()
                    Toast.makeText(this@SettingsActivity, name, Toast.LENGTH_SHORT).show()
                }
            }
            binding.llAnimals.addView(iv)
        }
    }

    /** The screen-time controls, from what is saved. */
    private fun loadTime() {
        val t = Preferences.timeSettings(this)
        timeLoading = true
        binding.cbLimit.isChecked = t.limitOn
        binding.sbLimit.progress = ((t.limitMinutes - TimeLimit.MIN_LIMIT) / 5).coerceIn(0, binding.sbLimit.max)
        binding.tvLimitValue.text = getString(R.string.time_limit_value, t.limitMinutes)
        timeLoading = false
        showTimeStatus()
    }

    private var timeLoading = false

    private fun showTimeStatus() {
        val now = System.currentTimeMillis()
        val t = Preferences.timeSettings(this, now)
        binding.tvTimeStatus.text = RemoteCommands.status(t, now)
        binding.btnLockNow.setText(if (t.forceLock) R.string.time_unlock_now else R.string.time_lock_now)
    }

    private fun saveTime(change: (TimeLimit.Settings) -> TimeLimit.Settings) {
        Preferences.saveTimeSettings(this, change(Preferences.timeSettings(this)))
        showTimeStatus()
    }

    private fun showStatus() {
        val kidMode = Preferences.isKidMode(this)
        val allowed = Preferences.getAllowedApps(this).size
        binding.tvKidModeStatus.text = if (kidMode) {
            resources.getQuantityString(R.plurals.settings_kid_mode_on_status, allowed, allowed)
        } else {
            getString(R.string.settings_kid_mode_off_status)
        }
        binding.btnKidMode.setText(if (kidMode) R.string.settings_kid_mode_off else R.string.settings_kid_mode_on)
        binding.tvAccessibilityStatus.setText(
            if (isBlockingServiceOn()) R.string.settings_accessibility_on else R.string.settings_accessibility_explain
        )
        binding.tvSetupHome.setText(if (isHomeApp()) R.string.setup_home_yes else R.string.setup_home_no)
        binding.tvSetupAccess.setText(if (isBlockingServiceOn()) R.string.setup_access_yes else R.string.setup_access_no)
        binding.tvSetupAdmin.setText(if (isAdminOn()) R.string.setup_admin_yes else R.string.setup_admin_no)
        val owner = Kiosk.isOwner(this)
        binding.tvKioskStatus.setText(if (owner) R.string.kiosk_on_status else R.string.kiosk_off_status)
        binding.llKioskHelp.visibility = if (owner) View.GONE else View.VISIBLE
        binding.llKioskOwner.visibility = if (owner) View.VISIBLE else View.GONE
        binding.tvKioskCommand.text = Kiosk.adbCommand(packageName)
        binding.cbKiosk.isChecked = Preferences.isKioskOn(this)
    }

    private fun setupListeners() {
        binding.btnAllowedApps.setOnClickListener {
            startActivity(Intent(this, AllowedAppsActivity::class.java))
        }
        binding.btnSetHome.setOnClickListener { requestHome() }
        binding.btnKidMode.setOnClickListener {
            val turnOn = !Preferences.isKidMode(this)
            Preferences.setKidMode(this, turnOn)
            if (turnOn) {
                Toast.makeText(this, R.string.settings_kid_mode_now_on, Toast.LENGTH_SHORT).show()
            } else {
                // leaving kid mode: let the parent pick their usual home app
                Toast.makeText(this, R.string.settings_kid_mode_now_off, Toast.LENGTH_LONG).show()
                openHomeSettings()
            }
            showStatus()
        }
        binding.btnChangePin.setOnClickListener {
            startActivity(Intent(this, PinLockActivity::class.java)
                .putExtra(PinLockActivity.EXTRA_MODE, PinLockActivity.MODE_SET)
                .putExtra(PinLockActivity.EXTRA_NEXT, PinLockActivity.NEXT_NONE))
        }
        binding.btnSaveWebhook.setOnClickListener {
            saveSettings()
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }
        binding.btnEnableDeviceAdmin.setOnClickListener {
            deviceAdminResult.launch(Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLE))
        }
        binding.btnEnableAccessibility.setOnClickListener {
            accessibilityResult.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnPickWallpaper.setOnClickListener { pickPicture.launch("image/*") }
        binding.btnClearWallpaper.setOnClickListener {
            Preferences.setWallpaper(this, "")
            File(filesDir, LauncherActivity.WALLPAPER_FILE).delete()
            refreshWallPreview()
            Toast.makeText(this, R.string.settings_wallpaper_done, Toast.LENGTH_SHORT).show()
        }
        binding.btnCopyAdb.setOnClickListener {
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("adb", Kiosk.adbCommand(packageName)))
            Toast.makeText(this, R.string.kiosk_copied, Toast.LENGTH_LONG).show()
        }
        binding.cbKiosk.setOnCheckedChangeListener { _, on -> Preferences.setKioskOn(this, on) }
        binding.btnReleaseKiosk.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.kiosk_release_title)
                .setMessage(R.string.kiosk_release_message)
                .setPositiveButton(R.string.kiosk_release) { _, _ ->
                    val ok = Kiosk.release(this)
                    Toast.makeText(this, if (ok) R.string.kiosk_released else R.string.kiosk_release_failed, Toast.LENGTH_LONG).show()
                    showStatus()
                }
                .setNegativeButton(R.string.setup_cancel, null)
                .show()
        }
        binding.cbLimit.setOnCheckedChangeListener { _, on -> if (!timeLoading) saveTime { it.copy(limitOn = on) } }
        binding.sbLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val minutes = TimeLimit.MIN_LIMIT + progress * 5
                binding.tvLimitValue.text = getString(R.string.time_limit_value, minutes)
                if (fromUser && !timeLoading) saveTime { it.copy(limitMinutes = minutes) }
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        binding.btnLockNow.setOnClickListener { saveTime { it.copy(forceLock = !it.forceLock, extendedUntil = if (!it.forceLock) 0L else it.extendedUntil) } }
        binding.btnResetUsed.setOnClickListener {
            saveTime { it.copy(usedMs = 0L) }
            Toast.makeText(this, R.string.time_reset_done, Toast.LENGTH_SHORT).show()
        }
        binding.btnExtendTime.setOnClickListener {
            val soon = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, 30) }
            TimePickerDialog(this, { _, h, m ->
                val until = TimeLimit.nextOccurrence(System.currentTimeMillis(), h, m)
                saveTime { it.copy(extendedUntil = until) }
                Toast.makeText(this, getString(R.string.time_extended, TimeLimit.clockText(until)), Toast.LENGTH_LONG).show()
            }, soon.get(java.util.Calendar.HOUR_OF_DAY), soon.get(java.util.Calendar.MINUTE), false).apply {
                setTitle(R.string.time_extend_until_title)
                show()
            }
        }
        binding.btnRemoteTest.setOnClickListener { testRemote() }
        binding.btnBack.setOnClickListener { leave() }
    }

    private fun leave() {
        saveSettings()
        ParentSession.lock()
        finish()
    }

    /** Is LittleLock the phone's current home app? */
    private fun isHomeApp(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == packageName
    }

    /** Asks Android to make LittleLock the home app (the system shows its own "set as default" question). */
    private fun requestHome() {
        if (isHomeApp()) {
            Toast.makeText(this, R.string.setup_home_yes, Toast.LENGTH_SHORT).show()
            return
        }
        val roles = getSystemService(RoleManager::class.java)
        try {
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                homeRoleResult.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        } catch (e: Exception) {
            // fall through to the settings page
        }
        explainHomeSettings()
    }

    /** The phone didn't switch by itself: say what to tap, then open the home-app setting (or the chooser). */
    private fun explainHomeSettings() {
        AlertDialog.Builder(this)
            .setTitle(R.string.setup_home_help_title)
            .setMessage(R.string.setup_home_help)
            .setPositiveButton(R.string.setup_open_settings) { _, _ -> openHomeSettings() }
            .setNegativeButton(R.string.setup_cancel, null)
            .show()
    }

    private fun openHomeSettings() {
        val attempts = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            // with no default home app set, Android asks which one to use and offers "Always"
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in attempts) {
            try {
                startActivity(intent)
                return
            } catch (e: Exception) {
                // try the next one
            }
        }
    }

    private fun isBlockingServiceOn(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, AccessibilityMonitorService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun isAdminOn(): Boolean = try {
        getSystemService(DevicePolicyManager::class.java)
            ?.isAdminActive(ComponentName(this, DeviceAdminReceiver::class.java)) == true
    } catch (e: Exception) {
        false
    }

    /** Copies the chosen picture into the app (scaled down to fit a phone screen) and uses it as the wallpaper. */
    private fun saveWallpaper(uri: Uri) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 2000 || bounds.outHeight / sample > 2000) sample *= 2
            val bmp = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: throw IllegalStateException("unreadable")
            File(filesDir, LauncherActivity.WALLPAPER_FILE).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            Preferences.setWallpaper(this, Look.IMAGE)
            refreshWallPreview()
            Toast.makeText(this, R.string.settings_wallpaper_done, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.settings_wallpaper_failed, Toast.LENGTH_LONG).show()
        }
    }

    /** Saves the Discord fields and posts a hello in the channel, so the parent knows it works. */
    private fun testRemote() {
        saveSettings()
        val token = Preferences.getDiscordToken(this)
        val channel = Preferences.getDiscordChannel(this)
        Thread {
            val ok = DiscordWebhookService.postBotMessage(token, channel, "✅ LittleLock is listening here. ${RemoteCommands.help()}")
            runOnUiThread { Toast.makeText(this, if (ok) R.string.remote_test_ok else R.string.remote_test_failed, Toast.LENGTH_LONG).show() }
        }.start()
    }

    private fun saveSettings() {
        val webhookUrl = binding.etWebhookUrl.text?.toString()?.trim()
        Preferences.setWebhookUrl(this, if (webhookUrl.isNullOrEmpty()) null else webhookUrl)
        Preferences.setAppLaunchNotifEnabled(this, binding.cbNotifAppLaunch.isChecked)
        Preferences.setDailySummaryEnabled(this, binding.cbNotifDailySummary.isChecked)
        Preferences.setBlockedAppsNotifEnabled(this, binding.cbNotifBlockedApps.isChecked)
        Preferences.setBlockShade(this, binding.cbBlockShade.isChecked)
        Preferences.setKidName(this, binding.etKidName.text?.toString().orEmpty())
        Preferences.setDiscordRemote(this, binding.etDiscordToken.text?.toString().orEmpty(), binding.etDiscordChannel.text?.toString().orEmpty(),
            binding.etDiscordUsers.text?.toString().orEmpty())
        Preferences.setRemoteOn(this, binding.cbRemote.isChecked)
    }
}
