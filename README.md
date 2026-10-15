# LittleLock

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

### Setting the PIN

1. Open LittleLock → **Settings**
2. Enter a 4-digit PIN (digits 0–9)
3. Confirm the PIN
4. The PIN is required to change any settings — kids cannot bypass it

### Configuring Allowed Apps

1. Open LittleLock → **Settings**
2. Toggle on the apps you want your child to have access to
3. All other apps will be blocked when the child tries to launch them

### Accessibility Setup

1. When first launching LittleLock, you'll be prompted to enable accessibility
2. Go to **Settings → Accessibility → LittleLock** and enable it
3. This allows LittleLock to detect when the child tries to disable it

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

### Build with Signing (using project keystore)

The project includes a debug keystore (`littlelock-release.keystore`) for development builds. For production, generate your own keystore:

```bash
./gradlew assembleRelease -PkeystoreFile=/path/to/your/keystore.jks \
  -PkeystorePassword=your_password \
  -PkeyAlias=your_alias \
  -PkeyPassword=your_password
```

### Run Tests

```bash
./gradlew test
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
