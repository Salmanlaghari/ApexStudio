# ApexStudio

> High-end & powerful on-device video editor for Android.

A premium mobile video-editing experience built with **Jetpack Compose**
and **Media3 Transformer**. Trims, layers and exports clips without
leaving the device — no cloud round-trips, no watermarks.

---

## Tech stack

| Layer | Choice |
| --- | --- |
| UI | Jetpack Compose (Material 3) + custom cinematic theme |
| Media | AndroidX Media3 (ExoPlayer, Transformer, Effect) |
| Color | GPUImage 3D-LUT engine for CapCut-style filters |
| Persistence | DataStore + kotlinx-serialization JSON codec |
| Build | AGP 8.7 · Kotlin 2.1 · Gradle 8.x |

---

## Project structure

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

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

`compileSdk = 36`, `minSdk = 26`, `targetSdk = 36`.

---

## Royalty-free music library

The editor's music sheet has three tabs:

| Tab | Source | Needs |
| --- | --- | --- |
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
cached locally for 24h; previews/downloads are per-track and user-initiated.

### Why Jamendo, not the Pixabay music API

Pixabay's documented public API (<https://pixabay.com/api/docs/>) only
exposes **image and video** endpoints — there is no documented music/audio
endpoint. Building on an undocumented endpoint would be unverifiable and
could break silently, so the library uses Jamendo instead (documented,
free key, whole catalogue Creative-Commons licensed).

### Legal notes

- **Hard line:** no PagalWorld, no Bollywood, no copyrighted commercial
  songs from any source. Only Creative-Commons / openly-licensed tracks.
- Every Jamendo track shows its CC licence badge (e.g. `CC BY-SA`).
  **Attribution is required by the licence** — credit artist + track when
  you publish a video that uses one (YouTube description, etc.).

---

## License

MIT — see `LICENSE` for the full text.