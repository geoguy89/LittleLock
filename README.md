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
- **Daily Summary Reports** — Sends a daily digest of activity to Discord
- **No External Servers** — All data stays on-device; only Discord webhooks are called (outbound HTTP)
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
2. Parent settings open. Tap **Choose allowed apps** and tick the apps your child may use. Only those show on the
   home screen - nothing else, not Settings or the Play Store (they're marked if you do tick them).
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
