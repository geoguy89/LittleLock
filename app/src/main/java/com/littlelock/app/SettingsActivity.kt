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

    private var loadingLook = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Accent.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        loadSettings()
        setupLook()
        setupListeners()
        setupUpdates()
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
        showUsage()
        showProfiles()
    }

    // ------------------------------------------------------------------ what was used today

    private fun showUsage() {
        val data = Preferences.usageData(this)
        val day = Preferences.today(this)
        val list = UsageLog.itemised(data, day, { Preferences.appLabel(this, it) }, bold = false)
        binding.tvUsageToday.text = if (list.isEmpty()) getString(R.string.usage_none) else UsageLog.headline(data, day) + "\n\n" + list
    }

    // ------------------------------------------------------------------ updates from GitHub

    private val updater by lazy { AndroidUpdater(this) }
    private var release: Release? = null
    private var updating = false

    private fun setupUpdates() {
        binding.btnCheckUpdate.setOnClickListener { checkForUpdate() }
        binding.btnInstallUpdate.setOnClickListener { installUpdate() }
        checkForUpdate()
    }

    private fun checkForUpdate() {
        if (updating) return
        binding.tvUpdateStatus.setText(R.string.update_checking)
        binding.btnCheckUpdate.isEnabled = false
        updater.latest { r, why ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.btnCheckUpdate.isEnabled = true
                release = r
                val newer = r != null && ReleaseFeed.isNewer(r.tag, updater.installedVersion)
                binding.tvUpdateStatus.text = when {
                    r == null -> why ?: getString(R.string.update_check)
                    newer -> getString(R.string.update_available, r.version, updater.installedVersion)
                    else -> getString(R.string.update_current, updater.installedVersion)
                } + Preferences.getUpdateError(this).let { if (it.isEmpty()) "" else "\n" + getString(R.string.update_last_failed, it) }
                binding.tvUpdateNotes.text = if (newer) r!!.notes else ""
                binding.tvUpdateNotes.visibility = if (newer && r!!.notes.isNotEmpty()) View.VISIBLE else View.GONE
                binding.btnInstallUpdate.visibility = if (newer) View.VISIBLE else View.GONE
                if (newer) binding.btnInstallUpdate.text = getString(R.string.update_install, r!!.version)
            }
        }
    }

    private fun installUpdate() {
        val r = release ?: return
        if (updating) return
        if (!packageManager.canRequestPackageInstalls()) {
            // Android wants the parent to allow this app to install apps, once
            Toast.makeText(this, R.string.update_allow, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        updating = true
        Preferences.setUpdateError(this, "")
        binding.btnInstallUpdate.isEnabled = false
        binding.btnCheckUpdate.isEnabled = false
        binding.pbUpdate.progress = 0
        binding.pbUpdate.visibility = View.VISIBLE
        binding.tvUpdateStatus.text = getString(R.string.update_downloading, 0)
        var lastPct = -1
        updater.install(r, { f ->
            val pct = (f * 100).toInt()
            if (pct != lastPct) {
                lastPct = pct
                runOnUiThread {
                    binding.pbUpdate.progress = pct
                    binding.tvUpdateStatus.text = getString(R.string.update_downloading, pct)
                }
            }
        }) { error ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                updating = false
                binding.btnInstallUpdate.isEnabled = true
                binding.btnCheckUpdate.isEnabled = true
                binding.pbUpdate.visibility = View.GONE
                binding.tvUpdateStatus.text = error ?: getString(R.string.update_installing)
            }
        }
    }

    private fun loadSettings() {
        binding.etWebhookUrl.setText(Preferences.getWebhookUrl(this) ?: "")
        binding.cbNotifAppLaunch.isChecked = Preferences.isAppLaunchNotifEnabled(this)
        binding.cbNotifDailySummary.isChecked = Preferences.isDailySummaryEnabled(this)
        binding.cbNotifBlockedApps.isChecked = Preferences.isBlockedAppsNotifEnabled(this)
        binding.cbNotifWrongPin.isChecked = Preferences.isWrongPinNotifEnabled(this)
        binding.cbBlockShade.isChecked = Preferences.isBlockShade(this)
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

    /** The tablet-wide choices: how often usage is reported, when the screen sleeps. */
    private fun setupLook() {
        loadingLook = true
        val usageChoices = listOf(0, 1, 2, 4, 8)
        radioChoices(binding.rgUsage, usageChoices.map { if (it == 0) "Never" else if (it == 1) "1 hour" else "$it hours" }, usageChoices.indexOf(Preferences.getUsageEvery(this)).coerceAtLeast(0)) { Preferences.setUsageEvery(this, usageChoices[it]) }
        radioChoices(binding.rgIdle, Idle.CHOICES.map { Idle.name(it) }, Idle.CHOICES.indexOf(Preferences.getIdleMinutes(this)).coerceAtLeast(0)) { Preferences.setIdleMinutes(this, Idle.CHOICES[it]) }
        loadingLook = false
    }

    // ------------------------------------------------------------------ the children who use the tablet

    /** One row per child (picture, name, Edit), and - with more than one - what happens when a child leaves or the tablet sits idle. */
    private fun showProfiles() {
        val list = Profiles.all(this)
        val defaultId = Profiles.defaultId(this)
        val d = resources.displayMetrics.density
        binding.llProfiles.removeAllViews()
        list.forEach { p ->
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
            }
            val pic = android.widget.ImageView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams((52 * d).toInt(), (52 * d).toInt()).apply { marginEnd = (12 * d).toInt() }
                setBackgroundColor(getColor(R.color.littlelock_background))
                contentDescription = null
            }
            Avatars.show(pic, p, hideWhenNone = false)
            val notes = listOfNotNull(
                if (list.size > 1 && p.id == defaultId) getString(R.string.profile_is_default) else null,
                if (p.hasPin) getString(R.string.profile_has_pin) else null
            )
            val text = android.widget.TextView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = p.name.ifEmpty { getString(R.string.profile_unnamed) } + if (notes.isEmpty()) "" else "\n" + notes.joinToString(" - ")
                setTextColor(getColor(R.color.littlelock_text_primary))
                textSize = 16f
            }
            val edit = android.widget.Button(this).apply {
                setText(R.string.profile_edit)
                setOnClickListener {
                    startActivity(Intent(this@SettingsActivity, ProfileEditorActivity::class.java).putExtra(ProfileEditorActivity.EXTRA_ID, p.id))
                }
            }
            row.addView(pic)
            row.addView(text)
            row.addView(edit)
            binding.llProfiles.addView(row)
        }
        binding.btnAddProfile.visibility = if (list.size < Profiles.MAX) View.VISIBLE else View.GONE
        binding.llSharing.visibility = if (list.size > 1) View.VISIBLE else View.GONE
        if (list.size <= 1) return

        loadingLook = true
        val ids = listOf("") + list.map { it.id }
        val names = listOf(getString(R.string.profiles_no_default)) + list.map { it.name.ifEmpty { getString(R.string.profile_unnamed) } }
        radioChoices(binding.rgDefaultProfile, names, ids.indexOf(defaultId).coerceAtLeast(0)) {
            Profiles.setDefaultId(this, ids[it])
            showProfiles()
        }
        radioChoices(binding.rgExitTo, listOf(getString(R.string.profiles_exit_default), getString(R.string.profiles_exit_picker)),
            if (Profiles.exitToPicker(this) || defaultId.isEmpty()) 1 else 0) {
            if (it == 0 && Profiles.defaultId(this).isEmpty()) {
                Toast.makeText(this, R.string.profiles_pick_default_first, Toast.LENGTH_LONG).show()
                showProfiles()
            } else {
                Profiles.setExitToPicker(this, it == 1)
            }
        }
        radioChoices(binding.rgProfileIdle, Profiles.IDLE_CHOICES.map { Profiles.idleName(it) },
            Profiles.IDLE_CHOICES.indexOf(Profiles.idleMinutes(this)).coerceAtLeast(0)) { Profiles.setIdleMinutes(this, Profiles.IDLE_CHOICES[it]) }
        loadingLook = false
    }

    /** Fill [group] with one radio button per name, checking [selected]; [onPick] gets the chosen index. */
    private fun radioChoices(group: android.widget.RadioGroup, names: List<String>, selected: Int, onPick: (Int) -> Unit) {
        group.setOnCheckedChangeListener(null)
        group.removeAllViews()
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
            if (loadingLook) return@setOnCheckedChangeListener
            val index = g.findViewById<RadioButton>(checkedId)?.tag as? Int ?: return@setOnCheckedChangeListener
            onPick(index)
        }
    }

    /** The screen-time controls, from what is saved. */
    private fun loadTime() {
        showLimitScope()
        val t = timeNow()
        timeLoading = true
        binding.cbLimit.isChecked = t.limitOn
        binding.sbLimit.progress = ((t.limitMinutes - TimeLimit.MIN_LIMIT) / 5).coerceIn(0, binding.sbLimit.max)
        binding.tvLimitValue.text = getString(R.string.time_limit_value, t.limitMinutes)
        binding.cbBed.isChecked = t.bedOn
        binding.btnBedStart.text = getString(R.string.bed_start_button, TimeLimit.minuteOfDayText(t.bedStartMin))
        showBedtimeTimes(t)
        binding.btnSummaryTime.text = getString(R.string.summary_time_button, TimeLimit.minuteOfDayText(t.summaryMin))
        binding.btnDayStart.text = getString(R.string.day_start_button, TimeLimit.minuteOfDayText(t.dayStartMin))
        timeLoading = false
        showTimeStatus()
    }

    private var timeLoading = false

    /** Whose screen time the controls change: a child's own when limits are per child, otherwise the tablet's. */
    private var timeChild: String? = null

    private fun timeNow(now: Long = System.currentTimeMillis()): TimeLimit.Settings = Preferences.timeSettings(this, now, timeChild)

    /** Limits for the whole tablet or for each child, and (per child) whose limits are on show. */
    private fun showLimitScope() {
        val list = Profiles.all(this)
        binding.llLimitScope.visibility = if (list.size > 1) View.VISIBLE else View.GONE
        if (list.size <= 1) {
            timeChild = null
            return
        }
        val perChild = Profiles.limitsPerChild(this)
        if (!perChild) timeChild = null
        else if (list.none { it.id == timeChild }) timeChild = Profiles.timeTarget(this).id
        val wasLoading = loadingLook
        loadingLook = true
        radioChoices(binding.rgLimitScope, listOf(getString(R.string.limits_scope_device), getString(R.string.limits_scope_child)), if (perChild) 1 else 0) {
            Profiles.setLimitsPerChild(this, it == 1)
            showLimitScope()
            loadTime()
        }
        binding.tvLimitChildTitle.visibility = if (perChild) View.VISIBLE else View.GONE
        binding.rgLimitChild.visibility = if (perChild) View.VISIBLE else View.GONE
        if (perChild) {
            radioChoices(binding.rgLimitChild, list.map { it.name.ifEmpty { getString(R.string.profile_unnamed) } }, list.indexOfFirst { it.id == timeChild }.coerceAtLeast(0)) {
                timeChild = list[it].id
                loadTime()
            }
        }
        loadingLook = wasLoading
    }

    private fun showBedtimeTimes(t: TimeLimit.Settings) {
        binding.btnBedStart.text = getString(R.string.bed_start_button, TimeLimit.minuteOfDayText(t.bedStartMin))
        binding.btnBedEnd.text = getString(R.string.bed_end_button, TimeLimit.minuteOfDayText(t.bedEndMin))
        binding.tvBedValue.text = getString(R.string.bed_length_value, TimeLimit.durationText(t.bedMinutes * 60_000L))
    }

    /** A clock picker for one of the time-of-day settings; [change] makes the new settings from the picked minute. */
    private fun pickTime(titleRes: Int, current: Int, change: (TimeLimit.Settings, Int) -> TimeLimit.Settings, after: () -> Unit = {}) {
        TimePickerDialog(this, { _, h, m ->
            val minute = h * 60 + m
            val next = change(timeNow(), minute)
            if (next.bedOn && next.bedStartMin == next.bedEndMin) {
                Toast.makeText(this, R.string.bed_same_time, Toast.LENGTH_LONG).show()
            } else if (next.bedOn && next.bedMinutes < TimeLimit.MIN_BED) {
                Toast.makeText(this, R.string.bed_too_short, Toast.LENGTH_LONG).show()
            } else {
                saveTime { change(it, minute) }
                loadTime()
                after()
            }
        }, current / 60, current % 60, false).apply {
            setTitle(titleRes)
            show()
        }
    }

    private fun showTimeStatus() {
        val now = System.currentTimeMillis()
        val t = timeNow(now)
        val status = RemoteCommands.status(t, now)
        val who = timeChild?.let { Profiles.get(this, it) }
        binding.tvTimeStatus.text = if (who == null) status else getString(R.string.limits_status_child, who.name.ifEmpty { getString(R.string.profile_unnamed) }, status)
        binding.btnLockNow.setText(if (t.forceLock) R.string.time_unlock_now else R.string.time_lock_now)
    }

    private fun saveTime(change: (TimeLimit.Settings) -> TimeLimit.Settings) {
        Preferences.saveTimeSettings(this, change(timeNow()), forId = timeChild)
        showTimeStatus()
    }

    private fun showStatus() {
        val kidMode = Preferences.isKidMode(this)
        val allowed = Profiles.allowedEverywhere(this).size
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
        binding.btnAddProfile.setOnClickListener { addProfile() }
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
        binding.cbBed.setOnCheckedChangeListener { _, on -> if (!timeLoading) saveTime { it.copy(bedOn = on) } }
        binding.btnBedStart.setOnClickListener {
            pickTime(R.string.bed_switch, timeNow().bedStartMin, { t, m -> t.copy(bedStartMin = m) })
        }
        binding.btnBedEnd.setOnClickListener {
            pickTime(R.string.bed_end_button_title, timeNow().bedEndMin, { t, m -> t.copy(bedEndMin = m) })
        }
        binding.btnSummaryTime.setOnClickListener {
            pickTime(R.string.summary_time_title, timeNow().summaryMin, { t, m -> t.copy(summaryMin = m) }) {
                DailySummaryScheduler.scheduleDailySummary(this)
            }
        }
        binding.btnDayStart.setOnClickListener {
            pickTime(R.string.day_start_title, timeNow().dayStartMin, { t, m -> t.copy(dayStartMin = m) })
        }
        binding.btnLockNow.setOnClickListener { saveTime { it.copy(forceLock = !it.forceLock, extendedUntil = if (!it.forceLock) 0L else it.extendedUntil) } }
        binding.btnResetUsed.setOnClickListener {
            saveTime { it.copy(usedMs = 0L) }
            Toast.makeText(this, R.string.time_reset_done, Toast.LENGTH_SHORT).show()
        }
        binding.btnExtendTime.setOnClickListener {
            ExtendDialog.show(this) { until ->
                saveTime { it.copy(extendedUntil = until) }
                Toast.makeText(this, getString(R.string.time_extended, TimeLimit.clockText(until)), Toast.LENGTH_LONG).show()
            }
        }
        binding.btnRemoteTest.setOnClickListener { testRemote() }
        binding.btnBack.setOnClickListener { leave() }
    }

    /** A new child: an empty profile, opened at once so the parent can name it and choose their apps. */
    private fun addProfile() {
        val n = Profiles.all(this).size + 1
        val p = Profiles.add(this, getString(R.string.profile_new_name, n)) ?: return
        showProfiles()
        startActivity(Intent(this, ProfileEditorActivity::class.java).putExtra(ProfileEditorActivity.EXTRA_ID, p.id))
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

    /** Saves the Discord fields and posts a hello in the channel, so the parent knows it works. */
    private fun testRemote() {
        saveSettings()
        val token = Preferences.getDiscordToken(this)
        val channel = Preferences.getDiscordChannel(this)
        Thread {
            val problem = DiscordWebhookService.postBotMessageResult(token, channel, "✅ LittleLock is listening here. ${RemoteCommands.help()}")
                ?: DiscordWebhookService.readResult(token, channel)
            runOnUiThread {
                val shown = if (problem == null) getString(R.string.remote_test_ok) else problem
                android.app.AlertDialog.Builder(this).setMessage(shown).setPositiveButton(android.R.string.ok, null).show()
            }
        }.start()
    }

    private fun saveSettings() {
        val webhookUrl = binding.etWebhookUrl.text?.toString()?.trim()
        Preferences.setWebhookUrl(this, if (webhookUrl.isNullOrEmpty()) null else webhookUrl)
        Preferences.setAppLaunchNotifEnabled(this, binding.cbNotifAppLaunch.isChecked)
        Preferences.setDailySummaryEnabled(this, binding.cbNotifDailySummary.isChecked)
        Preferences.setBlockedAppsNotifEnabled(this, binding.cbNotifBlockedApps.isChecked)
        Preferences.setWrongPinNotifEnabled(this, binding.cbNotifWrongPin.isChecked)
        Preferences.setBlockShade(this, binding.cbBlockShade.isChecked)
        Preferences.setDiscordRemote(this, binding.etDiscordToken.text?.toString().orEmpty(), binding.etDiscordChannel.text?.toString().orEmpty(),
            binding.etDiscordUsers.text?.toString().orEmpty())
        Preferences.setRemoteOn(this, binding.cbRemote.isChecked)
    }
}
