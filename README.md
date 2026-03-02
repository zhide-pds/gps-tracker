# 📡 GPS Asset Tracker — Android App

A lightweight Android app that turns your phone into a GPS asset tracker. It publishes your location, battery level, and device ID to any MQTT broker on a configurable schedule or when significant movement is detected.

---

## ✨ Features

| Feature | Detail |
|---|---|
| **MQTT Publishing** | Configurable broker host, port, topic, username/password, TLS |
| **Interval Mode** | Publish every N seconds (default: 30s) |
| **Movement Mode** | Publish when device moves more than M metres (default: 50m) |
| **Background Tracking** | Runs as a foreground service — survives screen off & app closed |
| **Start on Boot** | Optionally auto-start after device reboot |
| **Battery Reporting** | Battery % included in every payload |

### MQTT Payload (JSON)
```json
{
  "deviceId": "TRACKER-A3F9-2E81",
  "lat": 51.5074,
  "lon": -0.1278,
  "battery": 87,
  "timestamp": 1709380861,
  "trigger": "interval"
}
```
`trigger` is either `"interval"`, `"movement"`, or `"manual"`.

---

## 🚀 Getting Your APK (No Android Studio Needed!)

Follow these steps — it takes about 5 minutes:

### Step 1 — Create a free GitHub account
Go to [github.com](https://github.com) and sign up if you don't have an account.

### Step 2 — Create a new repository
1. Click the **+** button (top right) → **New repository**
2. Name it `gps-tracker`
3. Set it to **Public** (required for free Actions minutes)
4. Click **Create repository**

### Step 3 — Upload the project files
1. On your new repo page, click **uploading an existing file**
2. Drag and drop all the files from this project (keeping the folder structure)
   - Or use GitHub Desktop app for easier uploading
3. Click **Commit changes**

### Step 4 — Watch it build automatically
1. Click the **Actions** tab in your repository
2. You'll see a workflow called **"Build APK"** running (yellow dot = in progress)
3. Wait ~3–5 minutes for it to finish (green checkmark = done ✅)

### Step 5 — Download your APK
1. Click the completed workflow run
2. Scroll down to **Artifacts**
3. Click **GPS-Tracker-APK** to download a `.zip` file
4. Unzip it — inside is your `app-release.apk`

### Step 6 — Install on your Android phone
1. Transfer the APK to your phone (email it to yourself, or use a USB cable)
2. On your phone: **Settings → Security → Allow unknown sources** (or "Install unknown apps")
3. Open the APK file and tap **Install**
4. Open **GPS Tracker** and tap **START**

---

## ⚙️ Configuration

Tap the **⋮ menu → Settings** in the app to configure:

- **Broker Host** — your MQTT broker address (e.g. `broker.hivemq.com` for a free public test broker)
- **Port** — 1883 (plain) or 8883 (TLS)
- **Topic** — the MQTT topic to publish to (e.g. `tracker/devices`)
- **Username / Password** — leave blank if not required
- **TLS** — toggle on for encrypted connections
- **Interval** — how often to publish (seconds)
- **Movement Threshold** — minimum distance change to trigger a publish (metres)
- **Start on Boot** — auto-start tracking after reboot

---

## 🆓 Free MQTT Test Brokers

If you don't have your own broker, these are free for testing:

| Broker | Host | Port |
|---|---|---|
| HiveMQ Public | `broker.hivemq.com` | 1883 |
| EMQX Public | `broker.emqx.io` | 1883 |
| Mosquitto Test | `test.mosquitto.org` | 1883 |

⚠️ These are **public** — anyone can see your messages. Use your own broker for real deployments.

---

## 🔒 Android Permissions

The app requests:

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS coordinates |
| `ACCESS_BACKGROUND_LOCATION` | Tracking when app is closed |
| `FOREGROUND_SERVICE` | Keeps service alive |
| `INTERNET` | MQTT connection |
| `RECEIVE_BOOT_COMPLETED` | Start on boot (optional) |

---

## 📁 Project Structure

```
gps-tracker/
├── .github/workflows/
│   └── build-apk.yml          ← GitHub Actions (auto-builds your APK)
├── app/
│   └── src/main/
│       ├── java/com/gpstracker/
│       │   ├── mqtt/MqttManager.kt      ← MQTT connection & publishing
│       │   ├── service/TrackerService.kt← Background foreground service
│       │   ├── service/BootReceiver.kt  ← Start-on-boot handler
│       │   ├── ui/MainActivity.kt       ← Dashboard screen
│       │   ├── ui/SettingsActivity.kt   ← Settings screen
│       │   └── utils/                   ← Helpers (prefs, battery)
│       └── res/
│           ├── layout/                  ← XML layouts
│           ├── xml/preferences.xml      ← Settings definitions
│           └── values/                  ← Strings, colors, themes
└── build.gradle
```

---

## 🔁 Making Future Updates

1. Edit any file in GitHub directly (click the file → pencil icon)
2. Commit the change
3. Actions will automatically rebuild the APK
4. Download the new APK from the Actions tab

---

## 🧯 Troubleshooting

**App stops tracking after a while?**
- Go to phone Settings → Apps → GPS Tracker → Battery → set to "Unrestricted"
- Some manufacturers (Xiaomi, Samsung, Huawei) aggressively kill background apps

**Can't connect to MQTT broker?**
- Check the broker host and port in Settings
- Try the free HiveMQ broker first to confirm your setup works
- If using TLS, make sure port is 8883

**Location not updating?**
- Make sure you granted "Allow all the time" for location (not just "While using")
