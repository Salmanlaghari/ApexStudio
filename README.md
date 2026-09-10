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

## License

MIT — see `LICENSE` for the full text.