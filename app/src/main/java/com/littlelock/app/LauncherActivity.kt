package com.littlelock.app

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.littlelock.app.databinding.ActivityLauncherBinding

/**
 * The child's home screen: a grid of the apps the parent allowed, nothing else - with the child's name and picture and the
 * parent's choice of tile size, colours and wallpaper for that child.
 * When several children share the tablet ([Profiles]) it can instead show "who is using the tablet?", and a child's own
 * profile has an Exit button and no way into parent settings.
 * The lock button at the top opens parent settings behind the parent PIN (first run: the parent sets the PIN).
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLauncherBinding
    private var visibleApps: List<AppInfo> = emptyList()
    private var themedAccent = ""

    private val childPin = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Accent.apply(this)
        themedAccent = Accent.current(this).key
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        binding.btnSettings.setOnClickListener { openParentArea() }
        binding.btnExit.setOnClickListener {
            if (Profiles.shouldRevert(this)) Profiles.leave(this) else Profiles.showPicker(this)
            refresh()
        }

        // this is the home screen: Back has nowhere to go
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        if (savedInstanceState == null) {
            DailySummaryScheduler.scheduleDailySummary(this)
            if (!Preferences.isPinSet(this)) openParentArea()
        }
    }

    override fun onUserInteraction() {
        Idle.touch()
    }

    override fun onResume() {
        super.onResume()
        Idle.touch()
        // coming back to the home screen ends the parent's unlocked window: the lock is back without a manual relock
        ParentSession.lock()
        if (Preferences.isKidMode(this) && !ParentSession.isUnlocked() && Preferences.isTimeLocked(this)) {
            // time is up: Home leads to the time-up page, not to the apps
            startActivity(Intent(this, TimeUpActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            return
        }
        // the tablet sat idle for the parent's time while this was away: back to the default profile (or the picker)
        if (Preferences.isKidMode(this)) Profiles.tickIdle(this, busy = false)
        refresh()
        syncKiosk()
    }

    /** With the device-owner lockdown set up: on while kid mode is on and the parent is out, lifted otherwise. */
    private fun syncKiosk() {
        if (!Kiosk.isOwner(this)) return
        if (Kiosk.wanted(Preferences.isKioskOn(this), true, Preferences.isKidMode(this), ParentSession.isUnlocked())) {
            Kiosk.engage(this)
        } else {
            Kiosk.disengage(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        refresh()
    }

    /** Shows whatever the tablet is meant to show now: a child's apps, or the choice of who is using it. */
    fun refresh() {
        if (Profiles.look(this).accent != themedAccent) {
            recreate()                                   // each child has their own colour, which the theme has to be built with
            return
        }
        when (val screen = Profiles.screen(this)) {
            is Profiles.Screen.Picker -> showPicker()
            is Profiles.Screen.Home -> showHome(screen.profile)
        }
    }

    /** The apps the child may see: their allowed list in kid mode, every app when kid mode is off. */
    private fun showHome(profile: Profile) {
        binding.pickerRoot.visibility = View.GONE
        binding.scrollApps.visibility = View.VISIBLE
        val all = AppFilter.launchableApps(packageManager)
        var segments: List<Int>? = null
        if (Preferences.isKidMode(this)) {
            val rows = Profiles.arrange(AppFilter.visibleApps(all, profile.allowed, packageName), profile.order)
            visibleApps = rows.flatten()
            if (Profiles.ROW_BREAK in profile.order && rows.size > 1) segments = rows.map { it.size }
        } else {
            visibleApps = AppFilter.choosableApps(all, packageName)
        }
        val lightText = applyLook(profile)
        binding.gridApps.tile = Look.tile(profile.tileSize)
        binding.gridApps.mode = profile.layoutMode
        binding.gridApps.align = profile.align
        binding.gridApps.gapDp = Layouts.gapDp(profile.gap)
        binding.gridApps.segments = segments
        binding.gridApps.setApps(visibleApps, lightText) { launch(it) }
        binding.tvEmpty.visibility = if (visibleApps.isEmpty()) View.VISIBLE else View.GONE
        binding.tvEmpty.setText(
            if (Preferences.isPinSet(this)) R.string.launcher_empty else R.string.launcher_setup
        )
        binding.tvEmpty.setTextColor(
            if (lightText) getColor(R.color.white) else getColor(R.color.littlelock_text_secondary)
        )
        binding.tvKidModeOff.visibility = if (Preferences.isKidMode(this)) View.GONE else View.VISIBLE

        // who this is: their picture beside their name; in someone else's profile there is Exit and no way into parent settings
        Avatars.show(binding.ivAvatar, profile)
        val shared = Profiles.isShared(this)
        val inChildProfile = shared && profile.id != Profiles.defaultId(this)
        binding.btnSettings.visibility = if (inChildProfile) View.GONE else View.VISIBLE
        binding.btnExit.visibility = if (shared) View.VISIBLE else View.GONE
        binding.btnExit.setText(if (Profiles.shouldRevert(this)) R.string.profile_exit else R.string.profile_switch)
    }

    /** "Who is using the tablet?": a picture and name for each child; a child with a PIN types it first. */
    private fun showPicker() {
        val profiles = Profiles.all(this)
        val look = Profiles.look(this)
        val lightText = applyLook(look, title = getString(R.string.launcher_title))
        binding.scrollApps.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        binding.tvKidModeOff.visibility = View.GONE
        binding.pickerRoot.visibility = View.VISIBLE
        binding.ivAvatar.visibility = View.GONE
        binding.btnExit.visibility = View.GONE
        binding.btnSettings.visibility = View.VISIBLE
        binding.tvPickerTitle.setTextColor(if (lightText) getColor(R.color.white) else getColor(R.color.littlelock_text_primary))
        binding.gridProfiles.setProfiles(profiles, lightText) { pick(it) }
    }

    private fun pick(p: Profile) {
        if (p.hasPin) {
            childPin.launch(Intent(this, PinLockActivity::class.java)
                .putExtra(PinLockActivity.EXTRA_MODE, PinLockActivity.MODE_ENTER)
                .putExtra(PinLockActivity.EXTRA_NEXT, PinLockActivity.NEXT_RESULT)
                .putExtra(PinLockActivity.EXTRA_PROFILE, p.id))
        } else {
            Profiles.signIn(this, p.id)
            refresh()
        }
    }

    /**
     * The child's name in the title and their wallpaper behind the grid.
     * Returns true when the app names should be drawn in white (a dark colour or a picture behind them).
     */
    private fun applyLook(profile: Profile, title: String? = null): Boolean {
        binding.tvAppName.text = title ?: Look.greeting(profile.name, getString(R.string.launcher_title))
        val wallpaper = profile.wallpaper
        val color = Look.colorOf(wallpaper)
        if (color != null) {
            binding.root.background = ColorDrawable(color)
            return Look.isDark(color)
        }
        val dm = resources.displayMetrics
        val bmp = Wallpapers.forValue(this, wallpaper, dm.widthPixels, dm.heightPixels, profile.id)
        if (bmp != null) {
            binding.root.background = BitmapDrawable(resources, bmp).apply { gravity = Gravity.FILL }
            return Wallpapers.isDark(bmp)
        }
        binding.root.setBackgroundColor(getColor(R.color.littlelock_background))
        return false
    }

    private fun launch(app: AppInfo) {
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            Toast.makeText(this, getString(R.string.launcher_cant_open, app.label), Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun openParentArea() {
        val intent = Intent(this, PinLockActivity::class.java)
            .putExtra(PinLockActivity.EXTRA_MODE, if (Preferences.isPinSet(this)) PinLockActivity.MODE_ENTER else PinLockActivity.MODE_SET)
            .putExtra(PinLockActivity.EXTRA_NEXT, PinLockActivity.NEXT_SETTINGS)
        startActivity(intent)
    }
}
