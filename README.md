# GlycoMate 🩸

> A comprehensive diabetes management companion app for Android — built with Kotlin & Jetpack Compose.

![Android](https://img.shields.io/badge/Android-8%2B%20(API%2026)-green?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue?logo=kotlin)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.08-blue)
![Target](https://img.shields.io/badge/targetSdk-35%20(Android%2015)-brightgreen)

---

## What is GlycoMate?

GlycoMate is a feature-rich Android application designed to help people with **Type 1 diabetes** manage their condition day-to-day. It connects to Continuous Glucose Monitors (CGMs), logs meals and insulin, calculates bolus doses, and motivates users through gamification — all with a fully **Greek-language UI (Ελληνικά)**.

---

## Features

### 📡 CGM Integration
- **LibreLinkUp** — Real-time glucose from Abbott FreeStyle Libre sensors (EU, US, DE, FR, AP, AU regions)
- **Nightscout** — Self-hosted open-source CGM remote monitor
- **Dexcom Share** — Dexcom G-series sensors via Share API
- Background sync every **3 minutes** via WorkManager

### 📝 Manual Logging
- Glucose readings (mg/dL) with trend detection
- Insulin entries (Rapid / Long / Mixed, with brand)
- Meal entries with carb grams
- Bolus calculator (ICR + ISF based on current glucose)

### 🤖 AI Meal Recognition
- Photo-based meal analysis via **Groq API Key**
- Automatic carb estimation from a photo
- Retry logic with exponential backoff for API rate limits

### 📦 Barcode Scanning
- Scan product barcodes with **ML Kit**
- Automatic nutrition lookup via **OpenFoodFacts API**
- Instant carb info without manual entry

### 📊 Statistics & Charts
- **Time-In-Range (TIR)** percentage (daily & weekly)
- Glucose trend charts
- Historical log browser

### 😊 Mood & Energy Tracker
- Log mood (1–5 scale) and energy levels
- Correlation analysis between glucose levels and mood
- Visualized in charts over time

### 🎮 Gamification
- **XP system**: Earn points for every log (glucose: 10 XP, meal: 15 XP, insulin: 5 XP, perfect day: 50 XP)
- **10 levels**: Αρχάριος → Master (0–6000+ XP)
- **13 badges**: First log, streaks, perfect days, CGM connected, night owl, and more
- **Buddy mascot**: Reacts to your behavior (streaks, hypos, missed logs)
- **Streak counter**: Daily login & logging streaks

### 🚨 SOS Emergency Alerts
- One-tap emergency button
- Automatic SOS when glucose drops below threshold
- Sends **SMS with GPS location** to saved emergency contacts
- Manages up to N emergency contacts

### ⌚ Wear OS Integration
- Real-time glucose displayed on Android smartwatch
- Synced via Google Play Wearable API

### 🔔 Reminders & Notifications
- Customizable reminders for glucose checks, insulin, and meals
- 4 notification channels: Hypo alert 🚨, Hyper alert ↑, Live glucose status 📊, CGM sync 🔄
- Restarts automatically on device reboot (BootReceiver)

### 📄 PDF Export
- Generate PDF reports of glucose history for your doctor

### 🔒 Security
- Encrypted storage for CGM credentials (Android Security Crypto)
- No credentials stored in plain text

---

## Architecture

```
UI Layer  →  ViewModel  →  Repository  →  Room DB / CGM APIs / DataStore
(Compose)    (MVVM)        (Repository)   (Local / Remote / Prefs)
```

- **Pattern**: MVVM with Repository
- **Database**: Room 2.6.1 (glucose, insulin, meals, mood entries)
- **Preferences**: DataStore + EncryptedSharedPreferences
- **Background**: WorkManager (CGM sync, reminders) + Foreground Service

---

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose (BOM 2024.08.00) |
| Architecture | MVVM + Repository |
| Database | Room 2.6.1 |
| Networking | Retrofit2 2.11.0 + OkHttp3 4.12.0 |
| Background | WorkManager 2.9.1 |
| Camera | CameraX 1.3.4 |
| Barcode | ML Kit Barcode 17.3.0 |
| Location | Google Play Location 21.3.0 |
| Wearable | Google Play Wearable 18.2.0 |
| Security | Security Crypto 1.1.0-alpha06 |
| Images | Coil 2.7.0 |
| Design | Material3 1.2.1 |
| Navigation | Navigation Compose 2.7.7 |

---

## Screens

| Screen | Description |
|--------|-------------|
| **Dashboard** (Αρχική) | Latest glucose, TIR, quick-log FAB, AI scan shortcut |
| **Statistics** (Γραφήματα) | TIR charts, glucose trends, historical analytics |
| **Buddy** (Φίλος) | Mascot companion, XP/level/badges, streak info |
| **SOS** | Emergency contacts, manual & auto SOS trigger |
| **Settings** (Μενού) | Profile, targets, CGM setup, alerts, wearable |
| **AI Meal Scan** | Take photo → automatic carb estimation |
| **Barcode Scanner** | Scan product → nutrition info |
| **History** (Ιστορικό) | Complete log of all entries |
| **Mood Tracker** | Log mood/energy, view correlation with glucose |
| **Reminders** | Set up logging & medication reminders |
| **Onboarding** | First-launch setup wizard |

---

## CGM Sources

| Source | Type | Notes |
|--------|------|-------|
| LibreLinkUp | Abbott FreeStyle Libre | Regions: EU, US, DE, FR, AP, AU |
| Nightscout | Self-hosted | Custom URL + API secret |
| Dexcom Share | Dexcom G-series | Regional server support |

---

## Requirements

- **Android 8.0+** (API 26 / minSdk 26)
- **Permissions needed**:
  - `CAMERA` — barcode scanning & AI meal photo
  - `ACCESS_FINE_LOCATION` — SOS GPS location
  - `SEND_SMS` — SOS emergency messages
  - `POST_NOTIFICATIONS` — glucose alerts & reminders
  - `INTERNET` — CGM sync, Groq API, OpenFoodFacts

---

## Setup

```bash
# 1. Clone the repository
git clone https://github.com/vaggostrik/GlycoMate.git

# 2. Open in Android Studio
#    File → Open → select the GlycoMate folder

# 3. Wait for Gradle sync to complete

# 4. Connect a device or start an emulator

# 5. Press Run ▶
```

> **Note**: For AI Meal Scan, you need a [Groq API key](https://console.groq.com/keys). Enter it in Settings → AI Ανάλυση Γεύματος.

---

## Notifications

| Channel | Priority | Description |
|---------|----------|-------------|
| 🚨 Υπογλυκαιμία | HIGH | Low glucose alert (below target) |
| ↑ Υπεργλυκαιμία | HIGH | High glucose alert (above target) |
| 📊 Τρέχουσα γλυκόζη | LOW | Persistent status bar reading |
| 🔄 Συγχρονισμός CGM | MIN | Background sync status |

---

## Gamification Details

### XP Rewards
| Action | XP |
|--------|----|
| Glucose log | +10 |
| Meal log | +15 |
| Insulin log | +5 |
| Perfect day (100% TIR) | +50 |
| Good day (≥70% TIR) | +20 |
| Streak bonus | +5/day |

### Levels
| Level | Title (Greek) | XP Required |
|-------|---------------|-------------|
| 1 | Αρχάριος | 0 |
| 2 | Παρατηρητής | 200 |
| 3 | Σταθερός | 500 |
| 4–9 | ... | ... |
| 10 | Master | 6000 |

---

*GlycoMate — Ο σύντροφός σου στη διαχείριση του διαβήτη* 🩸
