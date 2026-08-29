# ApexStudio
<<<<<<< ours

A modern, high-performance Android video editing application built with Kotlin, Jetpack Compose, and Media3/ExoPlayer.

## Features
- Premium dark mode with glassmorphism + neon blue/purple accents
- Multi-track timeline (video, overlay, audio, SFX) with pinch-to-zoom, scrub, trim, and drag gestures
- RGB curves, color wheels (Shadows / Midtones / Highlights), and LUT presets
- Real-time audio mixer, beat sync, EQ visualizer, AI voice enhancement
- 8K/4K/1080p export with bitrate, quality, and frame-rate controls
- ExoPlayer / Media3 + MediaTransformer ready for hardware encoding

## Tech Stack
- **UI:** Jetpack Compose, Material 3, custom Canvas drawing
- **Media:** androidx.media3 (ExoPlayer, Transformer, Effect)
- **State:** ViewModel + StateFlow + Clean Architecture (Domain / Data / Presentation)
- **Navigation:** androidx.navigation:navigation-compose
- **Coil:** thumbnail loading
- **DataStore:** user prefs

## Setup Instructions (Android Studio)

1. **Install Android Studio** Ladybug | 2024.2.1 or newer.
2. **Install JDK 17** (File → Project Structure → SDK Location → Gradle JDK = 17).
3. Open Android Studio → **File → Open** → select the `ApexStudio/` folder.
4. Android Studio will detect the Gradle wrapper. If the wrapper JAR is missing, run from the project root:
   ```bash
   gradle wrapper --gradle-version 8.7
   ```
5. Let Gradle sync (`build.gradle.kts` already declares all required dependencies).
6. Run on a device or emulator (API 26+).
7. For first run, grant **Photos/Videos** and **Audio** permissions when prompted.

## Project Structure

```
ApexStudio/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/...
        └── java/com/apexstudio/app/
            ├── ApexApp.kt            (Application)
            ├── MainActivity.kt
            ├── ui/
            │   ├── ApexRoot.kt       (Nav host)
            │   ├── theme/            (Color, Type, Shape, Theme)
            │   ├── components/       (GlassCard, Buttons, Waveform, BottomNav, TopBar)
            │   └── screens/
            │       ├── home/
            │       ├── editor/       (Player, Tool bar, Multi-track Timeline)
            │       ├── colortools/   (Color wheels, RGB curves, LUTs)
            │       ├── audio/        (Audio studio, mixer, EQ, AI enhance)
            │       └── export/       (FX, transitions, export settings)
            ├── domain/model/         (Project, MediaClip, AudioTrack, ToolItem, LutPreset, ExportSettings)
            ├── data/repository/      (MediaRepository – mock)
            ├── presentation/
            │   ├── state/            (EditorState, ExportState, ColorToolState, AudioStudioState)
            │   └── viewmodel/        (EditorViewModel)
            └── util/                 (TimeFormat, WaveformGenerator, Fps)
```

## Architecture

```
┌──────────────────────────────────────────────────────┐
│                       UI (Compose)                  │
│  Screens ◀──▶ Components ◀──▶ Theme                 │
└────────────────────────┬─────────────────────────────┘
                         │ collectAsStateWithLifecycle
┌────────────────────────▼─────────────────────────────┐
│                Presentation                          │
│  ViewModel (StateFlow) — Undo/Redo stack             │
└────────────────────────┬─────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────┐
│                  Domain (Models)                     │
└────────────────────────┬─────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────┐
│                Data (Repository)                     │
│  Mock MediaRepository → future MediaStoreDataSource  │
└──────────────────────────────────────────────────────┘
```

## Integrating Real ExoPlayer

`EditorViewModel` already exposes a `Project` and clip URIs. To replace the mock preview with ExoPlayer:

```kotlin
val exoPlayer = ExoPlayer.Builder(context).build().apply {
    setMediaItem(MediaItem.fromUri(clip.uri))
    prepare()
}

AndroidView(
    factory = { ctx ->
        PlayerView(ctx).apply { player = exoPlayer }
    },
    modifier = Modifier.fillMaxSize()
)
```

Bind play/pause / seek to `vm.state.collectAsStateWithLifecycle()`.

## Export with MediaTransformer

`startExport()` in `EditorViewModel` is wired to a progress simulation. Replace with `MediaTransformer` for real hardware exports:

```kotlin
val transformer = MediaTransformer.Builder(context).build()
val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
    .setEffects(...)
    .build()

transformer.start(editedMediaItem, outputPath)
transformer.addListener(object : Transformer.Listener {
    override fun onCompleted(composition: Composition) { vm.setExportProgress(1f) }
    override fun onError(...) { ... }
})
```

## Notes

- Mock data lives in `data/repository/MediaRepository.kt` — replace with `MediaStore` queries.
- `WaveformGenerator` produces deterministic synthetic audio for visuals.
- All gesture controls (pinch-to-zoom, trim handles, scrub, color-wheel drag) work out of the box.
- Theme is dark-only by design; tokens are in `ui/theme/Color.kt`.
=======
High-end &amp; Powerful
>>>>>>> theirs
