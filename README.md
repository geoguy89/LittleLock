# LittleLock
<!-- Updated for v1.0.0 release -->

**Kids parental control launcher with Discord webhook notifications.**

LittleLock is an Android launcher that replaces the default home screen on a child's device. It locks the device into a curated set of allowed apps and sends real-time notifications to a Discord channel whenever the child tries to open a blocked app or attempts to exit the launcher.

---

## Features

- **App Locking** — Replaces the system launcher so only whitelisted apps can be opened
- **Discord Webhook Notifications** — Instant alerts to a Discord channel when blocked apps are launched or the launcher is exited
- **PIN Protection** — A 4-digit PIN locks the settings screen, preventing kids from changing the configuration
- **Accessibility Monitoring** — Detects when accessibility services are disabled (e.g. by a child trying to bypass the lock)
- **Boot Receiver** — Automatically re-applies the lock after device reboot
- **Device Admin** — Uses Android Device Admin API to prevent uninstallation
- **Daily Summary Reports** — Sends a daily digest to Discord listing every app used, for how long and how often (kept on the tablet, so a day offline is still complete)
- **Bedtime Lockdown** — Locks every night between a start time and a morning wake-up time you choose (e.g. 8 PM to 7 AM); a parent can extend playtime from the lock screen with the PIN
- **Wrong-PIN Alerts** — Discord note when a wrong PIN is typed, saying if it happened during bedtime
- **In-app Updates** — Parent Settings → App updates checks this repo's latest GitHub release and installs it
- **No External Servers** — All data stays on-device; the only outbound calls are your Discord webhook and GitHub (for update checks)
- **No Ads** — Zero advertising, zero tracking
- **No Paywall** — Completely free and open-source

---

## Installation

### Building from Source

1. **Prerequisites**
   - Android Studio (latest stable)
   - JDK 17 or later
   - Android SDK with API level 34 (Android 14)

2. **Clone and Open**
   ```bash
   git clone https://github.com/geoguy89/littlelock.git
   cd littlelock
   ```
   Open the project in Android Studio.

3. **Build the Release APK**
   ```bash
   ./gradlew assembleRelease
   ```
   The signed release APK will be at:
   ```
   app/build/outputs/apk/release/LittleLock-release.apk
   ```

### Installing the APK

1. **Enable Unknown Sources** on the child's device:
   - Settings → Security → Allow installation of apps from unknown sources

2. **Transfer the APK** to the device (USB, email, cloud storage, etc.)

3. **Install** by tapping the APK file

4. **Grant Required Permissions**:
   - **Device Admin** — Prevents uninstallation
   - **Accessibility** — Monitors for bypass attempts
   - **Draw over other apps** — Shows the lock overlay
   - **Boot completed** — Re-applies lock after reboot

5. **Set as Default Launcher**:
   - Settings → Apps → Default apps → Home app → Select LittleLock

---

## Usage

### Setting Up Discord Webhook

1. In your Discord server, create (or select) a channel for LittleLock notifications
2. Right-click the channel → **Edit Channel** → **Integrations** → **Webhooks**
3. Click **New Webhook**, give it a name (e.g., "LittleLock"), and copy the **Webhook URL**
4. Open LittleLock → **Settings** → paste the webhook URL
5. Toggle notifications on for the events you want to track

### First run: PIN, allowed apps, home screen

1. Install LittleLock and open it. It asks a parent to **set a 4-digit PIN** (typed twice).
2. Parent settings open. Under **Children on this tablet** tap **Edit**, then **Choose allowed apps**, and tick the apps
   your child may use. Only those show on the home screen - nothing else, not Settings or the Play Store (they're marked
   if you do tick them). The same screen sets their name, picture, colours and wallpaper.
3. Tap **Make LittleLock the home screen** and pick LittleLock, so the Home button always comes back here.
4. Tap **Turn on blocking (Accessibility)** and switch LittleLock on: then any app that isn't allowed (opened from a
   notification, for example) goes straight back to the home screen.
5. Tap **Done - lock again**.

### Getting back into parent settings

Tap the **lock** at the top of the home screen and enter the PIN. Five wrong PINs in a row lock the pad for 30
seconds. Parent settings lock again when you leave them (or after 10 minutes away from them).

### Leaving kid mode

In parent settings tap **Turn kid mode off**: nothing is blocked any more, every app is shown, and Android asks which
home app to use. **Turn kid mode on** brings it back.

---

## Privacy

- **No external servers** — LittleLock does not connect to any of its own servers
- **No ads** — Zero advertising of any kind
- **No paywall** — Completely free, forever
- **No tracking** — No analytics, no telemetry, no data collection
- **Discord only** — The only outbound network call is to your own Discord webhook URL
- **Open source** — Full source code available for audit

---

## Build Instructions

### Quick Build

```bash
./gradlew assembleRelease
```

### Release signing

Every release must be signed with the **same** key, or a new APK won't install over the old one. The key never goes
in the repo: the build reads it from environment variables or Gradle properties (for example in
`~/.gradle/gradle.properties`):

| Name | What |
|------|------|
| `LITTLELOCK_KEYSTORE_PATH` | path to the keystore file |
| `LITTLELOCK_STORE_PASSWORD` | the keystore password |
| `LITTLELOCK_KEY_ALIAS` | the key's alias (default `littlelock`) |
| `LITTLELOCK_KEY_PASSWORD` | the key's password (default: the keystore password) |

Without them a release build is debug-signed (the build says so) - fine for testing only.

On GitHub Actions the keystore comes from repository secrets: `LITTLELOCK_KEYSTORE_BASE64` (the keystore file,
base64-encoded) plus `LITTLELOCK_STORE_PASSWORD`, `LITTLELOCK_KEY_ALIAS` and `LITTLELOCK_KEY_PASSWORD`.

### Run Tests

```bash
./gradlew testDebugUnitTest      # or, on Windows, the full health check: tools\check.ps1
```

### Static Analysis

```bash
./gradlew detekt
./gradlew ktlintCheck
```

---

## License

This project is provided as-is for personal, non-commercial use.

---

## Acknowledgments

Built with:
- **Kotlin** — Primary language
- **Android SDK** — Platform APIs
- **Discord Webhooks** — Notification delivery

## Lockdown (kiosk) mode - the strong lock, for tablets

LittleLock can send a child back from other apps and close the notification pane by itself (the Accessibility service), but
on a tablet - a Kindle Fire in particular - the only complete lockdown is Android's own *lock task* mode. It removes the
notification pane and quick settings, the Home and Recents buttons, and lets only the apps you allowed start. It needs LittleLock
to be the **device owner**, which is set once from a computer:

1. On the tablet turn on USB debugging (Settings > Device options > tap the serial number seven times > Developer options). On some
   tablets remove added accounts first.
2. Connect it to a computer and run: `adb shell dpm set-device-owner com.littlelock.app/.DeviceAdminReceiver`
3. Open LittleLock > parent settings > *Lock the tablet down*. LittleLock is now the permanent home app (no "choose a launcher" step).

The parent PIN lifts the lockdown while you are in parent settings and puts it back when you leave. *Give up device owner* in the same
card returns the tablet to normal. Without this step LittleLock still works, with the weaker accessibility-based lock.

## Wallpapers

Pick a colour, a photo, or one of the cartoon animals in parent settings. The animals are drawn by `tools/gen_wallpapers.py`
(run it to regenerate `res/drawable/wp_*.xml`; `--preview DIR` writes PNGs to look at).

## Screen time, "Time's up", and the Discord remote

In parent settings, **Screen time** sets a daily limit (5 minutes to 4 hours), lets you **Lock now**, and **Extend time…** until a clock time you
choose. When time is up the apps the child was using are closed and a full-screen colourful *Time's up* page covers everything - Home leads
there, not to the apps. On that page the **Parent** button asks for the PIN and then *what time the tablet should lock again*; until then it is
open and at that time the page comes back.

**Remote control (Discord)** is optional and uses *your own* Discord bot, so nothing goes through anyone else's server: create a bot at
discord.com/developers, turn on its *Message Content Intent*, add it to a private channel where it can read and post, then paste the bot token,
the channel id and your own Discord user id into parent settings. Post commands in that channel from your phone: `status`, `lock`, `unlock`,
`extend 30`, `extend 1h`, `extend until 8:30pm`, `limit 90`, `limit off`, `reset`. Only the user ids you list are obeyed; the tablet checks the
channel about every 30 seconds and answers in the channel.

## Layout and photos

*Layout* centres the apps on the screen however many you chose: at the size you picked, or *Fill the screen* for the biggest tiles that still fit.
Wallpapers: ten cartoon animals (drawn in code, `tools/gen_wallpapers.py`), eighteen built-in CC0 / public-domain photos (credits in
`app/src/main/assets/PHOTO_CREDITS.txt`), colours, or a picture of your own. While another app (or the tablet's own home screen) comes up in kid
mode, an opaque rainbow cover hides it at once, so pressing Home repeatedly never lets a tap through.

## 1.6.0

- **Bedtime lockdown** (on by default): every night everything is off from 8:00 PM for 6 hours. Start time and length are adjustable in Settings > Screen time, or from Discord (`bedtime 9pm 8h`, `bedtime off`). A parent can still extend with the PIN or `extend 30`.
- **Accent colour**: pick the colour of the top bar, buttons and headings (Settings > Make it theirs).
- **Layout**: adjustable spacing between apps, and the block of apps can sit at the top, middle or bottom.
- **Monkey wallpapers**: nine CC0 monkey/orangutan photos and a cartoon monkey (credits in `assets/PHOTO_CREDITS.txt`).
- The rainbow "Hold on" cover is gone; the cover is now a plain dark screen with no words.

## 1.6.1

- Coming back to the LittleLock home screen locks the parent session again automatically (no manual relock).

## 1.6.2

- Much wider app spacing steps (up to Huge); default stays 10 dp.
- Wallpaper photos are shown less blown-up: a soft copy fills what the picture does not reach; they keep their native resolution.
- The accent colour now also fills the strip behind the navigation buttons at the bottom, on the home screen, the time-up/bedtime page and the PIN pad.
- Screen timeout inside LittleLock: the screen goes to sleep after 1/2/5/10 minutes without a touch (default 2; or never). The time-up page no longer keeps the screen on.

## 1.6.3

- Discord: the test message now says exactly what is wrong (wrong token / bot not in the channel / wrong channel id), and checks that the bot can read the channel.
- Discord remote: you can list yourself by user name as well as numeric id.
- Usage update posted to the Discord channel every 1/2/4/8 hours (default 2), only when the tablet was used.

## 1.6.4

- The accent colour now also colours the system navigation bar itself (status bar and navigation buttons), not only the strip drawn by the app.

## 1.6.5

- Bedtime ends on the clock, always: an extension only opens the tablet for its own time and never moves the morning end of the lockdown (now covered by a test).
- If the bedtime / time-up page is still showing when the lock ends (the tablet slept through it), LittleLock now brings the apps back on its own within seconds, and again the moment the screen wakes.

## 1.6.6

- Step-by-step Discord bot instructions in Settings (Developer Portal > OAuth2 > URL Generator, tick "bot", View Channels / Send Messages / Read Message History).
- The Discord webhook, the notification choices and the remote-control bot now sit together in Settings.
- New Discord command `usage` (also `stats`): today's used time, time left, apps opened and the lock state, whenever you ask.

## 1.9.1

- The lock screen (daily limit, bedtime, locked) now offers **Open parent settings** or **Give more time** after the parent PIN,
  so settings never need an extension first.
- An update that fails to install now says why in Settings > Updates (instead of a toast that disappears).

## 1.9.0 - several children on one tablet (profiles)

- **Profiles**: Settings > Children on this tablet > **Add a child**. Each child has their own name, picture, apps, colours,
  wallpaper, app size/layout and (optionally) their own 4-digit PIN. With one child nothing changes; with more, the home screen
  asks **Who's using the tablet?** (a picture and name for each child).
- **A child's PIN is never the parent's** (and never another child's). A profile with no PIN opens with a tap. A child inside
  their own profile has an **Exit** button and **no way into parent settings** - the parent button only shows on the default
  profile and on the picker.
- **Default profile**: the one the tablet goes back to. **Exit** (and the idle return) lead either to the default profile or to the
  picker - the parent chooses. The default profile can be left without a PIN.
- **Idle return**: when nothing has been in use for 30 minutes (home screen, or the screen asleep - choices 10 min to 2 hours, or
  never) the tablet goes back by itself. Watching a show or playing a game is "in use" and is never cut off.
- **Profile pictures**: sixteen cartoon faces (drawn by `tools/gen_avatars.py`) or the parent's own picture, shown beside the name.
- **Order and rows**: Edit > Order and rows moves the apps with arrows and starts a new row where you tick "New row".
- The app blocker follows the child in front: only their apps open; the lockdown (kiosk) lets through everyone's.
- The old single-child settings are carried over as the first profile.
- **Parent override of a PIN**: the parent can set, change or clear any child's PIN in Edit, and the parent's own PIN also opens any
  child's profile (so a forgotten child PIN never locks them out).
- **Screen-time limits**: with several children, Settings > Screen time asks whether limits apply to **the whole tablet** or to
  **each child separately**. Per child, each has their own daily limit, bedtime, lock and extension, and time is counted for the child
  signed in (the picker is never locked; a child's lock shows when they open their profile). The daily-summary time and when the
  day starts stay one setting. Discord commands change the child using the tablet (or the default profile) and say whose.
