# Smart Silent Zone 🛡️
> Context-Aware Native Android App & Live Web Simulation for College Presentation  
> **GitHub Repository:** [https://github.com/yasar-arafath-maker/Smart-Silence-Zone](https://github.com/yasar-arafath-maker/Smart-Silence-Zone)

---

## 🚀 Overview
**Smart Silent Zone** is an intelligent ringer-mode automation system designed for university campuses. It contextually manages phone audio profiles (Silent, Vibrate, Normal) using multi-sensor fusion, Wi-Fi BSSID indoor floor localization, geofences, and emergency break-glass mechanisms.

The project contains two complementary implementations:
1. **Native Android App (`app/`)**: Written in Kotlin, Jetpack Compose, Room DB with SQLCipher encryption, and Dagger Hilt. Hardened for Android 14/15 background restrictions.
2. **Live Presentation Simulation (`web-demo/index.html`)**: A single-page interactive simulation in Chrome (`http://localhost:8080`) featuring cascading State ➔ District ➔ College selectors, real browser GPS distance calculation, and emergency overrides.

---

## ⚡ Supercharged Production Architecture (Android 14/15 Hardened)

### 1. Foreground Service Type Compliance (Android 14/15 Hardening)
- Declared in `AndroidManifest.xml` with exact system-approved types:
  ```xml
  <service
      android:name=".service.SmartZoneForegroundService"
      android:foregroundServiceType="location|connectedDevice"
      android:exported="false" />
  ```
- Complies strictly with Android 14 (API 34) and Android 15 (API 35) foreground service policies:
  - `location`: Covers geofencing and connected Wi-Fi AP association.
  - `connectedDevice`: Covers paired Wear OS / BLE smartwatch haptic mirroring.
- Safely manages `PowerManager.WakeLock` with a 10-second timeout during state transitions to guarantee zero battery drain.

### 2. Wi-Fi Scan Throttling Workaround (0ms Room Matching)
- **Problem**: Android restricts Wi-Fi scans to 4 times every 2 minutes per foreground app, delaying room-level detection in corridors.
- **Production Solution**: `WifiFloorDetector` completely avoids triggering active Wi-Fi scans (`startScan()`). Instead, it relies on *active association state* via `ConnectivityManager.NetworkCallback` with `FLAG_INCLUDE_LOCATION_INFO` and instant `WifiManager.connectionInfo` (BSSID + RSSI signal strength).
- Connected campus BSSIDs are matched against Room in **0 milliseconds** with **zero scan throttle violations**.

### 3. Room Database Encryption at Rest (SQLCipher 256-bit AES)
- Location logs, private SMS templates, and whitelisted VIP parent phone numbers are protected against forensic extraction.
- Integrated 256-bit AES encryption via SQLCipher open helper factory in `AppModule.kt`.

---

## 🛠️ Core Modules (Engineering Implementation)

### 1. Wi-Fi Floor Detection (`sensor/WifiFloorDetector.kt`)
- **Technical Reality**: `WifiInfo.getBSSID()` only returns the MAC address of the connected router. Routers do not know what floor or room they are on. Therefore, the app maintains a local Room table (`WifiApEntity`: `bssid`, `zoneName`, `floor`, `silentPolicy`) created via an **Admin Survey Mode**.
- **API Handling**:
  - Android 12+ (API 31+): Uses `ConnectivityManager.NetworkCallback` with `NetworkCapabilities.FLAG_INCLUDE_LOCATION_INFO`.
  - Android 8.1 - 11: Falls back to `WifiManager.connectionInfo`.
  - Requires `ACCESS_FINE_LOCATION` and `NEARBY_WIFI_DEVICES` (Android 13+).
- **Admin Survey Mode**: Users can stand inside a classroom/lab and tap **"Save This AP to Survey Table"** to register it.

### 2. Break-Glass Auto-SMS & Emergency Override (`sensor/BreakGlassSmsManager.kt`)
- **VIP Contact Whitelist**: Stored locally in Room (`ContactEntity`). Parents/guardians are tagged as VIPs.
- **Missed Call Auto-SMS**: `PhoneCallReceiver` listens to `PHONE_STATE`. When a VIP call is unanswered and the phone is in a silent zone, an auto-reply is sent via `SmsManager`:
  > *"I'm in CSE 3rd Floor Lab. My phone is silenced until 11:30 AM. For an emergency, reply EMERGENCY."*
- **Emergency Override**: `SmsEmergencyReceiver` inspects incoming SMS from VIPs. If the keyword is **`EMERGENCY`**, it overrides Silent/DND mode and triggers a loud siren using `MediaPlayer` with `AudioAttributes.USAGE_ALARM` at maximum volume until dismissed.
- **Rate-Limiting**: Enforces a 30-minute cooldown per contact to prevent SMS loop storms.
- **Google Play Notice**: `SEND_SMS` / `RECEIVE_SMS` are restricted permissions. Permitted for sideloaded college project presentations; production deployment requires Google Play declaration.

### 3. Flip-to-Shhh Sensor Fusion (`sensor/FlipToShhhDetector.kt`)
- **Sensor Fusion**: Fuses 3 hardware sensors:
  - **Proximity Sensor**: `< 1.0 cm` (Screen covered / facing desk)
  - **Ambient Light Sensor**: `< 5.0 lux` (Dark environment)
  - **Accelerometer**: Z-axis `< -9.0 m/s²` (Phone lying flat face-down)
- **Debounced Hold**: Requires conditions to hold steady for ~1 second before applying DND/Silent.
- **Auto-Restore**: Restores the previous ringer profile immediately upon picking up the phone (screen face up).

### 4. Smartwatch Haptic Notification (`notifications/WatchNotificationManager.kt`)
- Posts high-priority notifications on the `SILENT_ZONE_EVENTS` channel.
- Uses `NotificationCompat.WearableExtender` with distinct vibration patterns (`0, 300, 150, 300`) to mirror alerts with haptic buzz to connected Wear OS and BLE smart bands.

### 5. Campus Silence Heatmap (`sensor/AmbientNoiseClassifier.kt`)
- **Acoustic Decibel Sampling**: Reads ambient sound pressure level (SPL) in short intervals.
- **Privacy Guarantee**: **Zero audio is recorded, saved, or uploaded.** Audio buffers are converted immediately to RMS decibel values in memory and discarded.
- **Zone Halos**:
  - 🟢 Green: `< 40 dB` (Quiet Study Zone)
  - 🟡 Amber: `40 - 60 dB` (Classroom Lecture)
  - 🔴 Red: `> 60 dB` (Canteen / High Noise)

---

## 📋 Manifest Permissions & Justifications

| Permission | Reason & Usage |
| :--- | :--- |
| `ACCESS_FINE_LOCATION` | Required by Android to read connected Wi-Fi BSSID and evaluate geofences. |
| `ACCESS_BACKGROUND_LOCATION` | Required to detect entering and leaving geofences when app is closed. |
| `NEARBY_WIFI_DEVICES` | Required on Android 13+ (API 33) to query Wi-Fi access points. |
| `ACCESS_NOTIFICATION_POLICY` | Required to programmatically toggle Do Not Disturb (DND) mode. |
| `POST_NOTIFICATIONS` | Required on Android 13+ to post persistent service and zone notifications. |
| `SEND_SMS` | Required to send the Break-Glass auto-reply SMS to parents/VIPs. |
| `RECEIVE_SMS` | Required to detect the `EMERGENCY` override keyword from whitelisted VIPs. |
| `READ_PHONE_STATE` | Required to detect incoming and missed calls from VIP contacts. |
| `READ_CONTACTS` | Required to select parents/emergency contacts into the VIP whitelist. |
| `RECORD_AUDIO` | Required to sample ambient sound pressure level (dB) without storing audio. |
| `VIBRATE` | Required for haptic feedback and smartwatch vibration triggers. |
| `FOREGROUND_SERVICE` | Keeps sensor listeners and zone evaluation alive in the background. |

---

## 🖥️ How to Run

### 1. Live Web Simulation Demo (Chrome)
1. The demo is running locally at:
   ```
   http://localhost:8080
   ```
   *(Or open `d:/Downloads/APP/web-demo/index.html` in Chrome)*
2. Features to test in the demo:
   - **Wi-Fi Floor Detection**: Change the AP dropdown to see the 0.5s transition and ringer change. Click **+ Add New AP** to dynamically add a classroom to the Room survey table.
   - **VIP Call & Auto-SMS**: Click **📞 Call** on Dad's card, end call to see the auto-SMS bubble.
   - **Emergency Override**: In Dad's reply box, leave `EMERGENCY` and click **Send SMS** to hear the siren and see the red flashing alert.
   - **Flip-to-Shhh**: Click **⚡ 1-Click Face-Down** to simulate sensor fusion.
   - **Watch Sync**: Observe the watch mockup vibrating on zone transitions.
   - **Heatmap**: Toggle **🎙️ Use My Mic** to see real decibels dynamically color the halos.

### 2. Native Android App (Android Studio)
1. Open the project root folder in **Android Studio Hedgehog / Iguana / Ladybug**.
2. Ensure Android SDK 34/35 is installed.
3. Gradle will sync dependencies (Dagger Hilt, Room, Compose).
4. Run on an Android device or emulator with API 26+.
