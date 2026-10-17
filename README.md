<div align="center">

# 🎬 ApexStudio

### High-end & Powerful on-device video editor for Android

**No cloud round-trips · No watermarks · 100% On-Device**

[![Build](https://img.shields.io/badge/Build-Passing-brightgreen?style=for-the-badge)](https://github.com/Salmanlaghari/ApexStudio/actions)
[![Kotlin](https://img.shields.io/badge/Kotlin-91.6%25-7F52FF?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-API%2026%2B-3DDC84?style=for-the-badge&logo=android)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-Proprietary-red?style=for-the-badge)](LICENSE)

</div>

---

## ✨ Features Showcase

| Feature | Description |
|---------|-------------|
| ✂️ **Trim & Cut** | Precise frame-accurate trimming with waveform |
| 🎬 **Multi-layer Timeline** | Video, Audio, Text, Overlay — unlimited layers |
| 🎨 **20+ Professional LUTs** | CapCut-style cinematic colour grades |
| ✨ **Keyframe Animation** | Position, Scale, Rotation, Opacity, Volume |
| 🎭 **Mask Tool — 10 Modes** | Circle, Rectangle, Heart, Star, Triangle, Ellipse, Horizontal, Mirror, Draw/Pen, Blur-faces |
| 🖼️ **Overlay Clips** | Image/Video overlays — position, scale, rotation, opacity, trim |
| 😍 **134 Animated 3D Stickers** | 6 packs + new animated pack (hearts, stars, fire, confetti) |
| 🎵 **Music Library** | 500k+ royalty-free tracks via Jamendo + offline + import |
| 📱 **Camera Kit** | AR Lenses & Filters (Snap Camera Kit) |
| ⚡ **Hybrid Engine** | Media3 (fast) + FFmpeg (heavy) + GStreamer (real-time) |

---

## 🎥 See It In Action

<div align="center">

### 🎬 Timeline Editing
<img src="https://images.unsplash.com/photo-1574717024653-61fd2cf4d44d?w=800&q=80" alt="Video Editing Timeline" width="800"/>

*Multi-layer professional timeline with drag & drop*

### 🎨 LUT Filters & Colour Grading
<img src="https://images.unsplash.com/photo-1536240478700-b869070f9279?w=800&q=80" alt="Colour Grading" width="800"/>

*20+ cinematic colour grades, real-time preview*

### 🎭 Mask Effects
<img src="https://images.unsplash.com/photo-1626814026160-2237a95fc5a0?w=800&q=80" alt="Mask Effects" width="800"/>

*10 mask modes with feather, invert, keyframe animation*

### 😍 3D Stickers & Overlays
<img src="https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=800&q=80" alt="Stickers" width="800"/>

*134 animated stickers, Lottie-powered + Overlay Clips*

</div>

---

## 🛠️ Tech Stack

| Layer | Choice |
|-------|--------|
| 🎨 **UI** | Jetpack Compose (Material 3) + custom cinematic theme |
| 🎬 **Media** | AndroidX Media3 (ExoPlayer, Transformer, Effect) |
| 🌈 **Colour** | GPUImage 3D-LUT engine for CapCut-style filters |
| 💾 **Persistence** | DataStore + kotlinx-serialization JSON codec |
| 🔧 **Build** | AGP 8.7 · Kotlin 2.1 · Gradle 8.x |
| ⚡ **Engine** | Hybrid: Media3 + FFmpeg + GStreamer |

---

## 📁 Project Structure

```
app/src/main/java/com/apexstudio/app/
├── ApexApp.kt                  # Application + crash diagnostics
├── MainActivity.kt             # Edge-to-edge Compose host
├── data/                       # Media, picker, LUT, crash log
├── domain/                     # Models (Project, MediaClip, etc.)
├── presentation/
│   ├── state/                  # UI state holders
│   └── viewmodel/              # EditorViewModel + factory
└── ui/
    ├── ApexRoot.kt             # Tab navigation root
    ├── components/             # BottomNav, TopBar, Glass, Waveform, Buttons
    ├── screens/
    │   ├── editor/             # EditorScreen + side panels
    │   ├── audio/              # Audio studio
    │   ├── colortools/         # Color / LUT studio
    │   ├── export/             # Export settings + progress
    │   ├── home/               # Project browser
    │   ├── settings/           # App settings
    │   └── diagnostics/        # Crash diagnostics screen
    └── theme/                  # Color, Shape, Type, Theme
```

---

## 🔨 Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

`compileSdk = 36`, `minSdk = 26`, `targetSdk = 36`.

---

## 🎵 Royalty-Free Music Library

The editor's music sheet has three tabs:

| Tab | Source | Needs |
|-----|--------|-------|
| **Discover** | Jamendo API v3 — 500k+ real Creative-Commons tracks, search + genre tags + streaming preview + download to timeline | Free `JAMENDO_CLIENT_ID` |
| **Offline** | Built-in tracks synthesized on-device | Nothing |
| **Import** | Audio picker (MP3/M4A/WAV/OGG from device storage) | Nothing |

Added tracks play in the live preview (`AudioPlaybackManager`) and are mixed
into the exported video (`MusicExportMixer`: software PCM mix → AAC → muxed
with the video track copied untouched).

### Getting a free Jamendo key

1. Create a free account at <https://developer.jamendo.com/>
2. Register an app to get a **client ID**
3. Provide it as the `JAMENDO_CLIENT_ID` env var **or** in `local.properties`
   (same key name) — Gradle puts it in `BuildConfig.JAMENDO_CLIENT_ID`.
   Never commit the key.
4. Rebuild — the Discover tab lights up automatically. Without a key the tab
   shows setup steps; Offline + Import keep working.

API terms: <https://devportal.jamendo.com/api_terms_of_use>. Responses are
cached locally for 24h; previews/downloads are cached on-device.

---

<div align="center">

### Made with ❤️ by TEAM PK AI

**ApexStudio © 2026 — Professional Video Editing, On Your Phone.**

⭐ **Star this repo if you like it!** ⭐

</div>
