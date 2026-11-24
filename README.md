# Family TV

A clean, lightweight **Android TV** IPTV player built with **Jetpack Compose for TV** and **Media3 (ExoPlayer)**, designed from the ground up for living-room D-pad remote navigation.

> **Disclaimer:** Family TV is strictly a bring-your-own-playlist media player. It does not include, provide, or endorse any IPTV channels, streams, or subscriptions.

---

## Features

- **Xtream Codes & M3U Support:** Log in with Xtream Codes server credentials or load any direct M3U playlist URL, plus an offline demo playlist for testing.
- **Live TV, Movies & Series:** Separate sections with instant local startup caching, category browsing, search, and pinned favorites (hold `OK` on any item).
- **Live EPG & TV Remote Controls:** Now/Next program guide info, quick channel up/down switching, aspect ratio cycling, and audio/subtitle track selection with adjustable subtitle sizes.
- **Continue Watching & Auto-Next:** Saves playback progress for movies and series and automatically queues the next episode.
- **Parental Lock:** 4-digit PIN protection for locked categories with automatic detection for adult groups.
- **Bilingual Interface:** Switch between **English** and **Türkçe** anytime in Settings.
- **Built-In GitHub Updater:** Check for new releases and install APK updates directly from **Settings → App Updates**.

---

## Download & Installation (Android TV / Google TV)

1. Go to the [Latest Release](https://github.com/Efe5407/FamilyTV/releases/latest) page and download `app-release.apk`.
2. Send the APK to your Android TV / Google TV device (via *Send Files to TV*, a USB drive, or ADB: `adb install app-release.apk`).
3. Open the file on your TV and enable **Install unknown apps** when prompted.

---

## Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/Efe5407/FamilyTV.git
   ```
2. Open the project in **Android Studio** (Ladybug or newer) with JDK 17+.
3. Run or build a signed APK via **Build → Generate Signed App Bundle / APK...**.

---

## Tech Stack

- **Kotlin** & **Jetpack Compose for TV** (`androidx.tv.material3`)
- **AndroidX Media3** (ExoPlayer)
- **OkHttp** & `org.json` for Xtream API, M3U parsing, and GitHub Releases updates
