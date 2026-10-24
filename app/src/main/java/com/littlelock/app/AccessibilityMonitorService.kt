package com.littlelock.app

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.PowerManager
import android.widget.FrameLayout
import android.widget.TextView
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.util.Log

/**
 * Accessibility service that keeps kid mode in force and reports usage.
 *  - In kid mode, an app the parent didn't allow that comes to the front is sent straight back to the LittleLock
 *    home screen (see [Enforcer]); keyboards and the system UI are never blocked, and nothing is blocked while the
 *    parent is signed in.
 *  - Counts app launches and sends the optional Discord notifications.
 */
class AccessibilityMonitorService : AccessibilityService() {

    private companion object {
        private const val TAG = "AccessibilityMonitor"
        // Parts of the system that are not "an app the child opened" (they never count as use)
        private val SYSTEM_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher3",
            "com.android.settings",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.google.android.gms",
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.apps.turbo",
            "com.google.android.setupwizard"
        )

        private fun isSystemPackage(packageName: String): Boolean = SYSTEM_PACKAGES.contains(packageName)
    }

    /** The app the child is using right now (null on the home screen, the time-up page, the notification shade...). */
    private var appInFront: String? = null
    private var imeCache: Set<String> = emptySet()
    private var imeCachedAt = 0L

    private var lastPackageName: String? = null
    private var lastForeground: String? = null
    private var cover: View? = null
    private var lastTick = 0L
    private var timeUpShown = false
    private var killedForLock = false
    private var polling = false
    private var lastPoll = 0L
    private val remoteWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val coverTimeout = Runnable { hideCover() }
    private val timeTick = object : Runnable {
        override fun run() {
            try {
                tickTime()
            } catch (e: Exception) {
                Log.w(TAG, "time tick: ${e.message}")
            }
            handler.postDelayed(this, 5000)
        }
    }
    private var lastBlocked: String? = null
    private var lastBlockedAt = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var shield: View? = null
    private val shieldTick = object : Runnable {
        override fun run() {
            updateShield()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // windows came or went: is the notification shade / quick settings one of them? (whatever the phone calls it)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (shieldWanted() && shadeWindowOpen()) closeShade()
            return
        }

        // Only care about window state changed events (app launches)
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString()
        lastForeground = packageName

        // LittleLock itself is in front again: the cover that hid the other app can go
        if (packageName == this.packageName) {
            if (cover != null) handler.postDelayed({ hideCover() }, 450)
            appInFront = null
            lastPackageName = null          // the next app opened from the home screen is a new opening, even the same one again
            return
        }

        // Time's up: nothing but the time-up page may be in front (the system's own windows and keyboards still work)
        if (timeLocked() && packageName != "com.android.systemui" && packageName != "android" && packageName !in keyboardPackages()) {
            showTimeUp()
            return
        }

        // the notification shade / quick settings opened anyway: close it again
        val said = (event.text?.joinToString(" ") ?: "") + " " + (event.contentDescription ?: "")
        if (ShadeGuard.isShade(packageName, className, said) && shieldWanted()) {
            closeShade()
            return
        }

        if (Enforcer.shouldSendHome(packageName, this.packageName, Preferences.getAllowedApps(this),
                keyboardPackages(), Preferences.isKidMode(this), ParentSession.isUnlocked())) {
            sendHome(packageName, event)
            return
        }

        // the system's own windows and keyboards are never "the app being used"
        if (isSystemPackage(packageName) || packageName in keyboardPackages()) {
            Log.d(TAG, "Filtering system package: $packageName")
            return
        }

        // an app popping up its own dialogs or screens is still the same use of it
        appInFront = packageName
        if (packageName == lastPackageName) return

        Log.d(TAG, "New app launch detected: $packageName ($className)")
        lastPackageName = packageName

        // Record the opening (with the time it stays in front, see tickTime) in the usage log - it does not depend on Discord or the internet
        if (Preferences.isKidMode(this) && !ParentSession.isUnlocked()) Preferences.recordUsage(this, packageName, 0L, 1)

        // Send Discord notification if enabled
        if (Preferences.isAppLaunchNotifEnabled(this)) {
            val webhookUrl = Preferences.getWebhookUrl(this)
            if (!webhookUrl.isNullOrEmpty()) {
                val appName = event.text?.firstOrNull()?.toString() ?: packageName
                DiscordWebhookService.sendAppLaunchNotification(
                    this,
                    webhookUrl,
                    appName,
                    packageName
                )
            }
        }
    }

    /** Back to the LittleLock home screen, and (at most once a minute per app) tell the parent. */
    private fun sendHome(packageName: String, event: AccessibilityEvent) {
        Log.i(TAG, "Not allowed in kid mode: $packageName - back to the home screen")
        showCover()                                     // hide the other app at once; spamming Home only meets the cover
        startActivity(Intent(this, LauncherActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        val now = SystemClock.elapsedRealtime()
        if (packageName == lastBlocked && now - lastBlockedAt < 60_000) return
        lastBlocked = packageName
        lastBlockedAt = now
        val webhookUrl = Preferences.getWebhookUrl(this)
        if (Preferences.isBlockedAppsNotifEnabled(this) && !webhookUrl.isNullOrEmpty()) {
            val appName = event.text?.firstOrNull()?.toString() ?: packageName
            DiscordWebhookService.sendBlockedAppNotification(this, webhookUrl, appName, packageName)
        }
    }

    /** Keyboards must keep working inside allowed apps. */
    private fun keyboardPackages(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (imeCachedAt != 0L && now - imeCachedAt < 60_000L) return imeCache   // asked of Android on every window change otherwise
        imeCache = try {
            getSystemService(InputMethodManager::class.java)?.enabledInputMethodList
                ?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
        imeCachedAt = now
        return imeCache
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        handler.removeCallbacks(shieldTick)
        handler.post(shieldTick)
        handler.removeCallbacks(timeTick)
        lastTick = 0L
        handler.post(timeTick)
        returnHomeAfterUpdate()
    }

    /** A self-update restarts LittleLock; bring the child's home screen back instead of leaving a blank screen. */
    private fun returnHomeAfterUpdate() {
        val code = try {
            packageManager.getPackageInfo(packageName, 0).longVersionCode
        } catch (e: Exception) {
            return
        }
        val seen = Preferences.getPrefs(this).getLong("seen_version_code", 0L)
        if (seen == code) return
        Preferences.getPrefs(this).edit().putLong("seen_version_code", code).apply()
        if (seen != 0L && Preferences.isKidMode(this)) {
            startActivity(Intent(this, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(shieldTick)
        handler.removeCallbacks(timeTick)
        hideCover()
        removeShield()
        remoteWorker.shutdown()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ screen time

    private fun timeLocked(): Boolean = Preferences.isKidMode(this) && !ParentSession.isUnlocked() && Preferences.isTimeLocked(this)

    private fun isScreenOn(): Boolean = getSystemService(PowerManager::class.java)?.isInteractive != false

    /** Every few seconds: count screen time, lock when time is up (and close the apps), and look for a command from the parent. */
    private fun tickTime() {
        val now = System.currentTimeMillis()
        val kid = Preferences.isKidMode(this)
        val parent = ParentSession.isUnlocked()
        if (lastTick != 0L && kid && !parent && isScreenOn()) {
            val delta = (now - lastTick).coerceIn(0L, 15_000L)
            Preferences.addUsedMs(this, delta, now)
            appInFront?.let { Preferences.recordUsage(this, it, delta, 0, now) }     // how long each app is in front
        }
        lastTick = now
        if (kid && !parent && Preferences.isTimeLocked(this, now)) {
            if (!timeUpShown || (lastForeground != null && lastForeground != packageName)) showTimeUp()
            else if (!killedForLock) scheduleClose()
        } else {
            // the lock ended by the clock (bedtime over, extension used, unlocked from afar): don't leave the time-up page sitting there
            if (timeUpShown && kid && isScreenOn()) {
                startActivity(Intent(this, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            timeUpShown = false
            killedForLock = false
        }
        sleepWhenIdle(now, kid && !parent)
        if (kid) sendUsageUpdate(now)
        DailySummaryScheduler.maintain(this, now)
        pollRemote(now)
    }

    private var sleptAt = 0L

    /** Every few hours (the parent chooses) a short usage note goes to the Discord channel - only when the tablet was used since the last one. */
    private fun sendUsageUpdate(now: Long) {
        val hours = Preferences.getUsageEvery(this)
        val url = Preferences.getWebhookUrl(this)
        if (hours <= 0 || url.isNullOrEmpty()) return
        val last = Preferences.getUsageLast(this)
        if (last == 0L) {                                      // first time: start counting from now
            Preferences.setUsageSent(this, now, Preferences.timeSettings(this, now).usedMs)
            return
        }
        if (now - last < hours * 3_600_000L) return
        val s = Preferences.timeSettings(this, now)
        if (s.usedMs == Preferences.getUsageLastUsed(this)) {  // nothing happened since the last note
            Preferences.setUsageSent(this, now, s.usedMs)
            return
        }
        Preferences.setUsageSent(this, now, s.usedMs)
        val left = if (s.limitOn) " ${TimeLimit.durationText((s.limitMinutes * 60_000L - s.usedMs).coerceAtLeast(0))} left." else ""
        val apps = Preferences.usageToday(this, now)
        val text = "Used today: ${TimeLimit.durationText(s.usedMs)}.$left Apps opened today: ${Preferences.getDailyLaunchCount(this)}." +
            if (apps.isEmpty()) "" else "\n$apps"
        Thread { DiscordWebhookService.sendNote(url, "📊 Usage update", text) }.start()
    }

    /** Nobody touching LittleLock for the chosen minutes: put the screen to sleep (once - it is woken by a touch or the power button). */
    private fun sleepWhenIdle(now: Long, kidMode: Boolean) {
        val on = isScreenOn()
        if (!on) {
            Idle.touch(now)                                    // a wake-up starts a fresh count
            return
        }
        if (!kidMode || now - sleptAt < 30_000L) return
        val inFront = lastForeground == null || lastForeground == packageName
        if (Idle.shouldSleep(Preferences.getIdleMinutes(this), Idle.lastTouch, now, inFront, on)) {
            sleptAt = now
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    /** The time-up page over everything (behind a plain cover so no app flashes through), then the apps are closed. */
    private fun showTimeUp() {
        if (!timeUpShown) {
            timeUpShown = true
            val url = Preferences.getWebhookUrl(this)
            if (Preferences.isBlockedAppsNotifEnabled(this) && !url.isNullOrEmpty()) {
                val state = TimeLimit.state(Preferences.timeSettings(this), System.currentTimeMillis())
                DiscordWebhookService.sendNote(url, "⏰ Time's up", when (state.why) {
                    TimeLimit.Why.FORCED -> "The tablet was locked."
                    TimeLimit.Why.BEDTIME -> "Bedtime lockdown started."
                    else -> "Today's screen time is used up."
                })
            }
        }
        showCover()
        startActivity(Intent(this, TimeUpActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        scheduleClose()
    }

    /** A moment later (the apps are in the background by then) close what the child was using. */
    private fun scheduleClose() {
        if (killedForLock) return
        killedForLock = true
        handler.postDelayed({
            val am = getSystemService(ActivityManager::class.java)
            Preferences.getAllowedApps(this).forEach { pkg ->
                try {
                    am?.killBackgroundProcesses(pkg)
                } catch (e: Exception) {
                    Log.w(TAG, "couldn't close $pkg: ${e.message}")
                }
            }
        }, 1500)
    }

    /** Looks in the parent's Discord channel for commands (if the remote is on), at most every 30 seconds. */
    private fun pollRemote(now: Long) {
        if (!Preferences.isRemoteOn(this) || polling || now - lastPoll < 30_000L) return
        val token = Preferences.getDiscordToken(this)
        val channel = Preferences.getDiscordChannel(this)
        val users = DiscordRemote.users(Preferences.getDiscordUsers(this))
        if (token.isBlank() || channel.isBlank()) return
        polling = true
        lastPoll = now
        val app = applicationContext
        remoteWorker.execute {
            try {
                val last = Preferences.getDiscordLast(app)
                val msgs = DiscordWebhookService.fetchMessages(token, channel, last.ifEmpty { null })
                if (msgs != null) {
                    if (last.isEmpty()) {
                        // the first look: remember where the channel stands and run nothing that is already there
                        Preferences.setDiscordLast(app, if (msgs.isEmpty()) "1" else DiscordRemote.newest(msgs))
                    } else {
                        val o = DiscordRemote.handle(msgs, users, last, Preferences.timeSettings(app), System.currentTimeMillis(), launches = Preferences.getDailyLaunchCount(app), apps = Preferences.usageToday(app, bold = false))
                        Preferences.saveTimeSettings(app, o.settings)
                        Preferences.setDiscordLast(app, o.lastId)
                        o.replies.forEach { DiscordWebhookService.postBotMessage(token, channel, it) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "remote: ${e.message}")
            } finally {
                polling = false
            }
        }
    }

    // ------------------------------------------------------------------ the cover

    /**
     * A plain, quiet full-screen cover (no words or colours to put anyone off), drawn at once when another app (or the phone's own home screen) comes up in kid mode,
     * so nothing underneath can be tapped while LittleLock is brought back. It takes every touch; it goes away when LittleLock is in
     * front again (or after a few seconds at most).
     */
    private fun showCover() {
        handler.removeCallbacks(coverTimeout)
        handler.postDelayed(coverTimeout, 2500)
        if (cover != null) return
        try {
            val wm = getSystemService(WindowManager::class.java) ?: return
            val v = FrameLayout(this).apply {
                setBackgroundColor(0xFF1E1E24.toInt())
                setOnTouchListener { _, _ -> true }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE
            )
            wm.addView(v, params)
            cover = v
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't put up the cover: ${e.message}")
        }
    }

    private fun hideCover() {
        handler.removeCallbacks(coverTimeout)
        val v = cover ?: return
        cover = null
        try {
            getSystemService(WindowManager::class.java)?.removeView(v)
        } catch (e: Exception) {
            // already gone
        }
    }

    private fun shieldWanted(): Boolean = ShadeGuard.shieldWanted(
        Preferences.isKidMode(this), ParentSession.isUnlocked(), Preferences.isBlockShade(this))

    /** Any system-UI window covering a good part of the screen is the shade (or quick settings) pulled down. */
    private fun shadeWindowOpen(): Boolean = try {
        val h = resources.displayMetrics.heightPixels
        windows.any { w ->
            val r = Rect()
            w.getBoundsInScreen(r)
            ShadeGuard.isShadeWindow(w.root?.packageName?.toString(), r.height(), h, w.type == AccessibilityWindowInfo.TYPE_SYSTEM)
        }
    } catch (e: Exception) {
        false
    }

    private fun closeShade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    /** Puts the shield over the status bar while it is wanted, and takes it away when it isn't. */
    private fun updateShield() {
        if (shieldWanted()) addShield() else removeShield()
    }

    /**
     * A transparent strip over the status bar that takes the touch, so pulling the shade down does nothing. It is an
     * accessibility overlay, which this service may draw without any extra permission.
     */
    private fun addShield() {
        if (shield != null) return
        try {
            val wm = getSystemService(WindowManager::class.java) ?: return
            val barId = resources.getIdentifier("status_bar_height", "dimen", "android")
            val bar = if (barId > 0) resources.getDimensionPixelSize(barId) else (28 * resources.displayMetrics.density).toInt()
            val view = View(this).apply { setOnTouchListener { _, _ -> true } }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                ShadeGuard.shieldHeightPx(bar),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP }
            wm.addView(view, params)
            shield = view
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't put up the status-bar shield: ${e.message}")
        }
    }

    private fun removeShield() {
        val view = shield ?: return
        shield = null
        try {
            getSystemService(WindowManager::class.java)?.removeView(view)
        } catch (e: Exception) {
            // already gone
        }
    }
}
