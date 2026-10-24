package com.littlelock.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64

/** One child on a shared tablet: a name and picture, their own apps and look, and (if the parent wants) their own PIN. */
data class Profile(
    val id: String,
    val name: String = "",
    /** "" (none), [Avatars.value] of a built-in picture, or [Avatars.IMAGE] (a picture the parent imported). */
    val avatar: String = "",
    val pinHash: String = "",
    val pinSalt: String = "",
    val accent: String = Accent.DEFAULT,
    val wallpaper: String = "",
    val tileSize: Int = Look.DEFAULT_TILE,
    val layoutMode: Int = Layouts.GRID,
    val align: Int = Layouts.MIDDLE,
    val gap: Int = Layouts.DEFAULT_GAP,
    /** The apps this child may use. */
    val allowed: Set<String> = emptySet(),
    /** The order they are shown in: package names, with [Profiles.ROW_BREAK] where a new row starts. Apps missing from it follow, by name. */
    val order: List<String> = emptyList()
) {
    val hasPin: Boolean get() = pinHash.isNotEmpty() && pinSalt.isNotEmpty()
}

/**
 * The children who share this tablet. With one profile (the usual case) nothing changes: it is just the child's home screen.
 * With more, a profile picker appears, each child gets their own apps, look and (optional) PIN, and the tablet goes back to the
 * default profile or the picker when a child leaves their profile or the tablet sits idle.
 *
 * Everything the app needs to know about "who is using it now" comes from here, so the home screen, the app blocker and the
 * lockdown all agree. The rules themselves are plain functions so they can be tested without a tablet.
 */
object Profiles {

    const val MAIN = "main"
    const val ROW_BREAK = "|"
    const val PICKER = "picker"
    const val MAX = 8

    /** Minutes of nothing in front before it goes back; 0 = never. */
    val IDLE_CHOICES = listOf(0, 10, 15, 30, 60, 120)
    const val DEFAULT_IDLE_MIN = 30

    private const val KEY_PROFILES = "profiles"
    private const val KEY_DEFAULT = "profile_default"
    private const val KEY_ACTIVE = "profile_active"
    private const val KEY_EXIT_PICKER = "profile_exit_picker"
    private const val KEY_IDLE_MIN = "profile_idle_min"
    private const val KEY_IDLE_SINCE = "profile_idle_since"

    // ------------------------------------------------------------------ the rules (no Android in them)

    /** What the home screen shows. */
    sealed class Screen {
        object Picker : Screen()
        data class Home(val profile: Profile) : Screen()
    }

    /** What to show, given the profiles, the parent's default and where the tablet was left. One profile: always its home screen. */
    fun resolve(profiles: List<Profile>, defaultId: String, active: String): Screen {
        if (profiles.size <= 1) return Screen.Home(profiles.firstOrNull() ?: Profile(MAIN))
        if (active == PICKER) return Screen.Picker
        profiles.firstOrNull { it.id == active }?.let { return Screen.Home(it) }
        profiles.firstOrNull { it.id == defaultId }?.let { return Screen.Home(it) }
        return Screen.Picker
    }

    /** Where leaving a profile (the Exit button, or the idle time) leads: [PICKER] or the default profile's id. */
    fun leaveTarget(profiles: List<Profile>, defaultId: String, exitToPicker: Boolean): String =
        if (exitToPicker || profiles.none { it.id == defaultId }) PICKER else defaultId

    class IdleStep(val idleSince: Long, val revert: Boolean)

    /**
     * The idle count: [busy] is "the screen is on and an app is in front"; anything else (the home screen, the tablet asleep)
     * counts as idle. Watching a show is busy, so it is never cut off. Time idle is [idleSince] (0 = not idle).
     * A count that already ran out goes back even if the tablet is busy again: it slept (so nothing ticked) and was just woken.
     */
    fun idleStep(idleSince: Long, busy: Boolean, now: Long, minutes: Int): IdleStep {
        if (idleSince in 1..now && minutes > 0 && now - idleSince >= minutes * 60_000L) return IdleStep(0L, true)
        if (busy) return IdleStep(0L, false)
        if (idleSince == 0L || idleSince > now) return IdleStep(now, false)
        return IdleStep(idleSince, false)
    }

    /** Should an idle tablet change screens? Only a signed-in profile that is not already the place it would go back to. */
    fun shouldRevert(profiles: List<Profile>, defaultId: String, active: String, exitToPicker: Boolean): Boolean {
        val now = resolve(profiles, defaultId, active) as? Screen.Home ?: return false
        if (profiles.size <= 1) return false
        val target = leaveTarget(profiles, defaultId, exitToPicker)
        return now.profile.id != target
    }

    /** The apps of [p] in the order and rows to show: (app, starts a new row). Apps the order doesn't mention follow, by name. */
    fun arrange(apps: List<AppInfo>, order: List<String>): List<List<AppInfo>> {
        val byPkg = apps.associateBy { it.packageName }
        val used = HashSet<String>()
        val rows = ArrayList<MutableList<AppInfo>>()
        var row = ArrayList<AppInfo>()
        for (token in order) {
            if (token == ROW_BREAK) {
                if (row.isNotEmpty()) rows.add(row)
                row = ArrayList()
            } else {
                val app = byPkg[token]
                if (app != null && used.add(token)) row.add(app)
            }
        }
        val rest = apps.filter { it.packageName !in used }.sortedBy { it.label.lowercase() }
        if (rows.isEmpty() && row.isEmpty()) return if (rest.isEmpty()) emptyList() else listOf(rest)
        row.addAll(rest)
        if (row.isNotEmpty()) rows.add(row)
        return rows
    }

    /** A PIN for a child must not be the parent's, and must not be any other child's. */
    enum class PinCheck { OK, SAME_AS_PARENT, SAME_AS_OTHER }

    // ------------------------------------------------------------------ reading and saving

    private var cache: Pair<String, List<Profile>>? = null

    private fun prefs(context: Context) = Preferences.getPrefs(context)

    @Synchronized
    fun all(context: Context): List<Profile> {
        val p = prefs(context)
        val raw = p.getString(KEY_PROFILES, null)
        if (raw == null) {
            val first = migrated(context)
            store(context, listOf(first))
            return listOf(first)
        }
        cache?.let { if (it.first == raw) return it.second }
        val list = parse(raw).ifEmpty { listOf(Profile(MAIN)) }
        cache = raw to list
        return list
    }

    fun get(context: Context, id: String): Profile? = all(context).firstOrNull { it.id == id }

    /** True when more than one child shares the tablet - the picker, Exit and the default-profile choices only matter then. */
    fun isShared(context: Context): Boolean = all(context).size > 1

    @Synchronized
    private fun store(context: Context, list: List<Profile>) {
        val raw = serialize(list)
        prefs(context).edit().putString(KEY_PROFILES, raw).apply()
        cache = raw to list
    }

    @Synchronized
    fun save(context: Context, profile: Profile) {
        val list = all(context)
        store(context, if (list.any { it.id == profile.id }) list.map { if (it.id == profile.id) profile else it } else list + profile)
    }

    fun update(context: Context, id: String, change: (Profile) -> Profile) {
        val p = get(context, id) ?: return
        save(context, change(p))
    }

    /** A new, empty profile (no apps yet), with the next free id. */
    fun add(context: Context, name: String): Profile? {
        val list = all(context)
        if (list.size >= MAX) return null
        var n = list.size + 1
        while (list.any { it.id == "p$n" }) n++
        val p = Profile("p$n", name.trim().take(24))
        save(context, p)
        return p
    }

    /** Removes a profile and the pictures kept for it. The last profile can't be removed. */
    @Synchronized
    fun delete(context: Context, id: String): Boolean {
        val list = all(context)
        if (list.size <= 1 || list.none { it.id == id }) return false
        store(context, list.filter { it.id != id })
        wallpaperFile(context, id).delete()
        Avatars.file(context, id).delete()
        val e = prefs(context).edit()
        if (prefs(context).getString(KEY_DEFAULT, "") == id) e.putString(KEY_DEFAULT, "")
        if (prefs(context).getString(KEY_ACTIVE, "") == id) e.putString(KEY_ACTIVE, "")
        e.apply()
        return true
    }

    /** The picture file of a profile's own wallpaper (the first profile keeps the name earlier versions used). */
    fun wallpaperFile(context: Context, id: String): File =
        File(context.filesDir, if (id == MAIN) "wallpaper.jpg" else "wallpaper_$id.jpg")

    /** The first profile, carried over from the single-child settings of earlier versions. */
    private fun migrated(context: Context): Profile {
        val p = prefs(context)
        return Profile(
            id = MAIN,
            name = p.getString("kid_name", "") ?: "",
            accent = Accent.choice(p.getString("accent", Accent.DEFAULT)).key,
            wallpaper = p.getString("wallpaper", "") ?: "",
            tileSize = p.getInt("tile_size", Look.DEFAULT_TILE).coerceIn(0, Look.TILES.lastIndex),
            layoutMode = if (p.getInt("layout_mode", Layouts.GRID) == Layouts.AUTO) Layouts.AUTO else Layouts.GRID,
            align = p.getInt("layout_align", Layouts.MIDDLE).coerceIn(Layouts.TOP, Layouts.BOTTOM),
            gap = p.getInt("layout_gap", Layouts.DEFAULT_GAP).coerceIn(0, Layouts.GAP_DP.lastIndex),
            allowed = p.getStringSet("allowed_apps", emptySet())?.toSet() ?: emptySet()
        )
    }

    // ------------------------------------------------------------------ the parent's choices for sharing

    /** The profile the tablet goes back to ("" = none: the picker). */
    fun defaultId(context: Context): String {
        val id = prefs(context).getString(KEY_DEFAULT, "") ?: ""
        return if (all(context).any { it.id == id }) id else ""
    }

    fun setDefaultId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_DEFAULT, id).apply()
    }

    /** True: leaving a profile (or idling) shows the picker; false: it shows the default profile. */
    fun exitToPicker(context: Context): Boolean = prefs(context).getBoolean(KEY_EXIT_PICKER, false)

    fun setExitToPicker(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_EXIT_PICKER, on).apply()
    }

    fun idleMinutes(context: Context): Int = prefs(context).getInt(KEY_IDLE_MIN, DEFAULT_IDLE_MIN)

    fun setIdleMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_IDLE_MIN, if (minutes in IDLE_CHOICES) minutes else DEFAULT_IDLE_MIN).apply()
    }

    fun idleName(minutes: Int): String = if (minutes == 0) "Never" else if (minutes >= 60 && minutes % 60 == 0) (if (minutes == 60) "1 hour" else "${minutes / 60} hours") else "$minutes minutes"

    // ------------------------------------------------------------------ who is using it now

    /** What the home screen shows right now. */
    fun screen(context: Context): Screen =
        resolve(all(context), defaultId(context), prefs(context).getString(KEY_ACTIVE, "") ?: "")

    /** The signed-in profile, or null on the picker. */
    fun current(context: Context): Profile? = (screen(context) as? Screen.Home)?.profile

    /** The profile whose look the screens wear: the signed-in one; on the picker the default's (or the first). */
    fun look(context: Context): Profile =
        current(context) ?: all(context).let { l -> l.firstOrNull { it.id == defaultId(context) } ?: l.first() }

    /** The apps allowed right now: the signed-in child's. On the picker, none (nothing should be open there). */
    fun allowedNow(context: Context): Set<String> = current(context)?.allowed ?: emptySet()

    /** Every app any profile may use - what the lockdown has to let through (it is the same list for everyone). */
    fun allowedEverywhere(context: Context): Set<String> = all(context).flatMap { it.allowed }.toSet()

    fun signIn(context: Context, id: String) {
        prefs(context).edit().putString(KEY_ACTIVE, id).putLong(KEY_IDLE_SINCE, 0L).apply()
    }

    fun showPicker(context: Context) {
        prefs(context).edit().putString(KEY_ACTIVE, PICKER).putLong(KEY_IDLE_SINCE, 0L).apply()
    }

    /** The child leaves their profile: the default profile or the picker, as the parent chose. */
    fun leave(context: Context) {
        val target = leaveTarget(all(context), defaultId(context), exitToPicker(context))
        prefs(context).edit().putString(KEY_ACTIVE, target).putLong(KEY_IDLE_SINCE, 0L).apply()
    }

    /** The screen went off: the idle count starts now (if it isn't running already). */
    fun markIdle(context: Context, now: Long = System.currentTimeMillis()) {
        if (!isShared(context)) return
        val p = prefs(context)
        if (p.getLong(KEY_IDLE_SINCE, 0L) == 0L) p.edit().putLong(KEY_IDLE_SINCE, now).apply()
    }

    fun shouldRevert(context: Context): Boolean =
        shouldRevert(all(context), defaultId(context), prefs(context).getString(KEY_ACTIVE, "") ?: "", exitToPicker(context))

    /** One tick of the idle count (see [idleStep]); true when the tablet just went back and the home screen should come up. */
    fun tickIdle(context: Context, busy: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        if (!isShared(context)) return false
        val p = prefs(context)
        val step = idleStep(p.getLong(KEY_IDLE_SINCE, 0L), busy, now, idleMinutes(context))
        if (step.idleSince != p.getLong(KEY_IDLE_SINCE, 0L)) p.edit().putLong(KEY_IDLE_SINCE, step.idleSince).apply()
        if (!step.revert || !shouldRevert(context)) return false
        leave(context)
        return true
    }

    // ------------------------------------------------------------------ PINs (a child's is never the parent's)

    fun checkPin(context: Context, pin: String, forId: String?): PinCheck {
        if (Preferences.isPinSet(context) && Preferences.verifyPin(context, pin)) return PinCheck.SAME_AS_PARENT
        if (all(context).any { it.id != forId && it.hasPin && matches(it, pin) }) return PinCheck.SAME_AS_OTHER
        return PinCheck.OK
    }

    /** True when [pin] belongs to a child profile - the parent may not choose it for themselves. */
    fun isChildPin(context: Context, pin: String): Boolean = all(context).any { it.hasPin && matches(it, pin) }

    fun setPin(context: Context, id: String, pin: String): PinCheck {
        require(PinHasher.isValidPin(pin)) { "PIN must be ${PinHasher.PIN_LENGTH} digits" }
        val check = checkPin(context, pin, id)
        if (check != PinCheck.OK) return check
        val salt = PinHasher.newSalt()
        update(context, id) { it.copy(pinSalt = b64(salt), pinHash = b64(PinHasher.hash(pin, salt))) }
        return PinCheck.OK
    }

    fun clearPin(context: Context, id: String) {
        update(context, id) { it.copy(pinHash = "", pinSalt = "") }
    }

    fun verifyPin(context: Context, id: String, pin: String): Boolean {
        val p = get(context, id) ?: return false
        return !p.hasPin || matches(p, pin)
    }

    private fun matches(p: Profile, pin: String): Boolean = try {
        PinHasher.verify(pin, Base64.getDecoder().decode(p.pinSalt), Base64.getDecoder().decode(p.pinHash))
    } catch (e: Exception) {
        false
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    // ------------------------------------------------------------------ stored form

    fun serialize(list: List<Profile>): String {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject()
                .put("id", p.id).put("name", p.name).put("avatar", p.avatar)
                .put("pinHash", p.pinHash).put("pinSalt", p.pinSalt)
                .put("accent", p.accent).put("wallpaper", p.wallpaper)
                .put("tile", p.tileSize).put("layout", p.layoutMode).put("align", p.align).put("gap", p.gap)
                .put("allowed", JSONArray(p.allowed.sorted()))
                .put("order", JSONArray(p.order)))
        }
        return arr.toString()
    }

    fun parse(raw: String): List<Profile> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isEmpty()) return@mapNotNull null
            Profile(
                id = id,
                name = o.optString("name"),
                avatar = o.optString("avatar"),
                pinHash = o.optString("pinHash"),
                pinSalt = o.optString("pinSalt"),
                accent = Accent.choice(o.optString("accent", Accent.DEFAULT)).key,
                wallpaper = o.optString("wallpaper"),
                tileSize = o.optInt("tile", Look.DEFAULT_TILE).coerceIn(0, Look.TILES.lastIndex),
                layoutMode = if (o.optInt("layout", Layouts.GRID) == Layouts.AUTO) Layouts.AUTO else Layouts.GRID,
                align = o.optInt("align", Layouts.MIDDLE).coerceIn(Layouts.TOP, Layouts.BOTTOM),
                gap = o.optInt("gap", Layouts.DEFAULT_GAP).coerceIn(0, Layouts.GAP_DP.lastIndex),
                allowed = strings(o.optJSONArray("allowed")).toSet(),
                order = strings(o.optJSONArray("order"))
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
}
