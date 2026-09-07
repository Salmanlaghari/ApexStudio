# Pro-Grade Video Compositing Engine — Android-Native Spec

**Project:** Apex Studio (Android, Kotlin, Jetpack Compose, Media3 1.5.0, OpenGL ES 2.0/3.0 via `BaseGlShaderProgram`)
**Status of prior work:** PR #80 wired `LutFilterGlEffect` + `FxGlEffect` + new `AdjustmentsGlEffect` onto the preview `ExoPlayer` via `setVideoEffects(...)`. Compile passes. Visual on-device confirmation is the missing piece.

This spec replaces the previous "WebGL / `<canvas>` / `requestAnimationFrame` / Web Audio" framing with the equivalent Android-native stack. Every item from the original 10-point list is preserved as a section so the original intent stays traceable.

---

## Fix #1 — Player & Preview Canvas
**Original:** Replace placeholder card with HTML5 `<canvas>`/WebGL.
**Native equivalent:** The Android preview surface is **`ExoPlayer` + `PlayerView` (SurfaceView) + the Media3 `setVideoEffects(List<Effect>)` chain**. SurfaceView is a separate hardware overlay composited by SurfaceFlinger; it cannot be replaced by a Compose canvas, but it can host the decoded video + a real OpenGL ES effect chain.

**Status:** Wired. `EditorScreen.kt` now calls `player.setVideoEffects([AdjustmentsGlEffect, LutFilterGlEffect, FxGlEffect])` in a `LaunchedEffect` placed *before* the `prepare()` `LaunchedEffect` (satisfies Media3's "must be called once before `prepare`" contract). Source order matters; this is enforced by source placement.

**Remaining:** On-device visual confirmation (Brightness -91/100, each filter chip, each FX chip).

---

## Fix #2 — Real-time render loop synced to timeline timecode
**Original:** `requestAnimationFrame` synced to playhead.
**Native equivalent:** Two complementary loops already exist:
- `LaunchedEffect(exoPlayer) { while (isActive) { … player.currentPosition … delay(33) } }` in `EditorScreen.kt` — polls position 30×/s, writes to `vm.setPlayerPosition`.
- `LaunchedEffect(exoPlayer, state.isPlaying)` — play/pause driver.

**What's missing:** A 60 fps Compose recomposition driver when the GL effects themselves don't already invalidate (e.g. when a Keyframe or animated Text is active and needs per-frame redraw). Implementation: add a `Choreographer.postFrameCallback` in a `DisposableEffect` that emits a tick to a `MutableState<Long>` which all animated layers (Keyframes, AnimatedText) read from. Falls under Fix #6.

**Status:** Frame-rate loop for position polling: ✅ exists. 60 fps driver for animated layers: ❌ missing (Fix #6).

---

## Fix #3 — Filmstrip thumbnail generation
**Original:** `OffscreenCanvas` / HTML5 Canvas thumbnails.
**Native equivalent:** `MediaMetadataRetriever.getFrameAtTime(timeUs, OPTION_CLOSEST)` on `Dispatchers.Default`, decoded into a `Bitmap`, optionally processed through the same LUT/FX color matrix used by `LutBitmapCache` for thumbnails. Cached `LruCache<clipId, List<Bitmap>>` (one per second of clip duration). Rendered into `TimelineTrackArea` via `AndroidView { ImageView }` rows instead of the current placeholder gradient boxes.

**Status:** Backbone exists (`LutBitmapCache` for LUT thumbnails; `MediaAnalyzer` for audio waveform). The per-second filmstrip *does not* yet run in the background. The placeholder gradient boxes in `TimelineTrackArea` are exactly the "dummy UI" the spec calls out — they need to be replaced with real `Bitmap`s.

**Plan:**
1. New `TimelineFilmstripEngine` class that lazily generates `List<Bitmap>` for a clip at 1 fps, in chunks, on `Dispatchers.Default`. LRU cache, max 50 entries.
2. `EditorViewModel.refreshTimelineFilmstrip(clipId)` called when a clip is added or the preview scrolls.
3. `TimelineTrackArea` row 1 (video filmstrip) draws `Image` composables for each cached bitmap, falling back to a flat `Color(0xFF1E1B2E)` box (clearly marked as "loading" with a single thin progress line, not a faked render).

**Fallback (Honesty rule, Fix #9/#10):** if the user's device can't decode at 1 fps (low-RAM Android Go devices), reduce to 1 frame every 2 s and log a `Log.w` once per clip. Never fake a fully-rendered strip.

---

## Fix #4 — Live FX pipeline
**Original:** WebGL shaders / CSS Canvas Filters.
**Native equivalent:** Existing `LutFilterGlEffect` (3D LUT strip), `FxGlEffect` (56 fragment shaders: Vignette, Film Grain, VHS, Glitch, Pixelate, Chromatic, Scanlines, Soft Blur, Bloom, Shake, Strobe, Prism, Zoom Blur, Halation, Lens Flare, Light Leak, Anamorphic, Bokeh Overlay, Sparkle, Sun Beam, Glow Diffuse, Bad TV, Pixel Sort, Datamosh, RGB Jitter, CRT Phosphor, Interlaced, Digital Drop, Sepia Grain, Dust Scratches, Film Damage, Bleach Bypass, Technicolor Strip, Cross Process, Solarize, Radial Blur, Tilt Shift, Motion Streak, Ghosting, Spin Blur, Camera Wobble, Whip Pan FX, Halftone, Sketch Lines, Posterize, Edge Neon, Thermal Vision, Night Vision, Oil Paint, Emboss Relief, Invert FX, Kaleidoscope), and the new `AdjustmentsGlEffect` from PR #80. All real GLSL ES 2.0 fragment shaders via `androidx.media3.effect.BaseGlShaderProgram`.

**Status:** Wired on the preview ExoPlayer (PR #80). Adjustments and FX chained correctly. Filter selection works through the manifest lookup. **Visual confirmation on a device is the open item.**

**Plan:** Per-PR visual sign-off checklist (Fix #10) — see bottom.

---

## Fix #5 — Animated Text overlays
**Original:** Fade, Typewriter, Bounce, Slide, Neon Glow animated text with custom fonts / colors / stroke / shadow.
**Native equivalent:**
- **Data model:** `TextOverlay` already exists (Models.kt:65) with `x, y, sizeScale, colorArgb, bgArgb, strokeColorArgb, shadowColorArgb, fontFamily, isItalic, isBold, startMs, endMs, presetId`. `TextPanel` UI for editing it exists.
- **Export:** `TextOverlayGlEffect` already bakes text into the exported MP4 via a custom GL program.
- **Preview engine (the missing piece):** a new `AnimatedTextEngine` in Compose. Each `TextOverlay` carries an `animationId: String?` (e.g. `"fade"`, `"typewriter"`, `"bounce"`, `"slide"`, `"neon_glow"`) and an `animationDurationMs: Long`. The preview reads these in a `LaunchedEffect(state.playerPositionMs, overlayId)` keyed on the per-frame playhead, computes an animation progress fraction, and applies Compose `graphicsLayer` transforms (alpha / translation / scale) + draw modifiers (Neon Glow via `Modifier.drawWithCache` + BlurMaskFilter) to a `Text` composable positioned at `overlay.x * containerW, overlay.y * containerH`.

**Plan:**
1. Extend `TextOverlay` with `animationId: String?` and `animationDurationMs: Long = 400L`.
2. `AnimatedTextEngine` class: pure function `progress(timeMs: Long, overlay: TextOverlay): AnimationState` (alpha, translation, scale).
3. `EditorScreen` preview overlay layer: render active `TextOverlay`s as `Text` composables with computed transforms.
4. `TextPanel` UI: dropdown for animation style + duration slider.
5. Export: extend `TextOverlayGlEffect` to apply the same animation curves during bake, so preview ↔ export match.

**Fallback:** if a particular animation uses a `BlurMaskFilter` and the device disables hardware layer compositing for views, fall back to a layered `Text` shadow (4 stacked translucent copies offset by ±1dp). Log a one-time `Log.i` for diagnostic value. Never silently drop the animation.

---

## Fix #6 — Transform Keyframes (Position / Scale / Rotation / Opacity over time)
**Original:** Animate V1/V2 layers and text layers over time.
**Native equivalent:**
- **Data model + interpolation:** `KeyframeTrack.interpolateAt(timeMs)` and `AnimatedTransform(translateX, translateY, scale, rotationDeg, opacity)` (Models.kt:255) — already returns the interpolated transform for any time. Easing curves (`LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, HOLD`) implemented.
- **Export:** `KeyframeAnimationEffect` in `data/animation/` already drives the GL transform on the export pipeline.
- **Preview driver (the missing piece):** the preview currently does **not** apply `MediaClip.keyframes` to the PlayerView. Implementation: a `KeyframePreviewDriver` that runs in the position-polling loop (`LaunchedEffect(exoPlayer)`), reads the selected clip's `keyframes.interpolateAt(playerPositionMs)`, and applies a Compose `graphicsLayer { translationX/Y, scaleX/Y, rotationZ, alpha = ... }` to the wrapping `AndroidView { PlayerView }`.

**Plan:**
1. New `KeyframePreviewDriver` Composable wrapper.
2. `EditorScreen` wraps the `VideoPreviewArea` call site in the driver.
3. Test clip with 2-3 keyframes at e.g. 0%, 50%, 100% to confirm smooth scale + translation.
4. If `graphicsLayer` on an `AndroidView` doesn't reliably re-render at 60 fps (SurfaceView can be cached), add a `Choreographer.postFrameCallback` driver (Fix #2) to force invalidation.

**Honest limit:** GPU compositor caching of `AndroidView { SurfaceView }` may still prevent transform from being applied to the actual decoded frames. If that proves true, the **export** will reflect the keyframes correctly (because the export uses a different path: `EditedMediaItem` + GL effect), but the **preview** will only reflect keyframes for overlay/sticker layers (which are Compose-drawn). The PR description must state this clearly per the Honesty rule.

---

## Fix #7 — Multi-Track Audio Engine
**Original:** Web Audio API graph with volume / mute / solo / fade / waveform.
**Native equivalent:** Per-track `ExoPlayer` (or `MediaPlayer` for SFX) attached to `MainMixer` which mixes into the device output. The data model (`AudioTrack.volume, isMuted, isSolo, trimStartMs, trimEndMs, fadeInMs, fadeOutMs`) already exists (Models.kt:177).

**Status:**
- ✅ `AudioEngine` class with `setVolume, setPitchSemitones, enableReverb, enableEcho, enableBassBoost` exists.
- ✅ `MainPlayer` (preview video's ExoPlayer) has pitch routed through `registerMainPlayerForAudioEffects` + `applyAudioPitchToMainPlayer`.
- ✅ `MediaAnalyzer.analyzeAudioWaveform` produces per-clip waveform data.
- ❌ Per-track `ExoPlayer` instances for non-main audio tracks not yet constructed.
- ❌ Main mixer that sums all per-track ExoPlayer outputs and applies per-track volume/mute/solo/fade not yet built.

**Plan:**
1. `MainMixer` class: holds a list of `ExoPlayer` instances (one per `AudioTrack`), each with its own `setVolume`, `setPlayWhenReady`, and a `DefaultAudioSink` configured to mix into a shared `AudioMixingSink` (Media3 `EditingAudioProcessor` or a hand-rolled `AudioProcessor` chain).
2. `EditorViewModel.setPlaying(playing: Boolean)` propagates to all per-track players, offset by their `trimStartMs` against the main video's `playerPositionMs`.
3. `EditorViewModel.setTrackVolume / toggleAudioTrackMute / toggleAudioTrackSolo / setAudioTrackFadeIn / setAudioTrackFadeOut` are already defined; wire them through to the per-track players.
4. Waveform rendering: `TimelineTrackArea` audio rows draw the cached `state.audioWaveform: FloatArray` as a row of vertical `Box` bars (the current placeholder is `repeat(32) { … }` of static heights — Fix #3 covers the per-clip filmstrip; this is the audio equivalent).
5. **Critical: when per-track playback is implemented, also wire it into the export path** — `ExportConfig` doesn't currently have an `audioTracks` list. Add `audioTracks: List<AudioTrack> = emptyList()` to `ExportConfig` and route each through `EditedMediaItem.Builder.setEffects(Effects(audioProcessors, ...))` in the export.

**Honest limit:** Mixing multiple ExoPlayer instances requires either (a) a `Media3 EditingAudioProcessor` chain inside a single `Transformer` (the cleanest path, but the current Transformer export is single-clip) or (b) `AudioTrack` (the platform class) write/read PCM streams and hand-mix. (a) is the right path; falls under the broader "multi-clip export" follow-up.

---

## Fix #8 — Advanced WebGL features (`WEBGL_color_buffer_float`, etc.)
**Original:** WebGL extension flags.
**Native equivalent:** On Android, the equivalent of a floating-point render target is `GLES30.GL_RGBA16F` / `GL_RGBA32F` framebuffer textures. The existing `LutFilterGlEffect` and `FxGlEffect` use 8-bit textures (sufficient for preview). If HDR is needed (a future feature), swap the internal `GlProgram` to use `GLES30.GL_RGBA16F` via `GlUtil.createTexture(..., GLES30.GL_RGBA16F, ...)`.

**Status:** Not applicable to current scope; documented for HDR follow-up.

---

## Fix #9 — No Dummy UI Mockups
**Concrete rules for this codebase:**
1. Every "Filter", "Effect", "Transition", "Audio Track", "Text Overlay", "Keyframe" control in the UI must be backed by a class that implements the feature in code. No `// TODO` stubs in shipped code.
2. If a feature is partially implemented, the UI must show a *clearly labelled* fallback (e.g. "Loading thumbnail…" over a flat dark box, not a faked rendered gradient) and the code must `Log.w` once per session with a tag like `ApexStudio.Unimplemented:<feature>`.
3. Already addressed in prior PRs: the "1080P" badge and fullscreen icon overlays (Fix #1 from PR #79) were non-functional placeholders and were removed.

**Audit checklist (will be re-run before each PR):**
- [ ] `FilterPanel`: every chip → `FilterPreset` in `FilterManifest` with a real `.cube` LUT file in `assets/luts/`. Chips for ids without a LUT file must not appear.
- [ ] `FxPanel`: every chip → `FxPreset` enum value with a real shader in `FxGlEffect.fragmentFor(...)`. Chips for unhandled enum values must not appear.
- [ ] `TransmissionTemplatesPanel`: every template → `(filterPreset, fxPreset, transitionType)` triple that resolves to real GL effects.
- [ ] `TimelineTrackArea`: video filmstrip row → real `Bitmap` frames (or labelled "loading" fallback). Audio row → real waveform samples. Sticker/Text rows → real overlay content.
- [ ] `AdjustPanel`: every slider → real `VideoAdjustments` field with a real `AdjustmentsGlEffect` uniform.

---

## Fix #10 — Honest fallback / worker pipeline
**Implementation:**
- All heavy decode (LUT parsing, frame extraction, audio waveform) runs on `Dispatchers.Default` via `viewModelScope.launch(Dispatchers.Default) { ... }`. Existing pattern in `MediaAnalyzer` and `LutBitmapCache` — extend, don't replace.
- When a feature cannot run (e.g. device without `GLES30`, or hardware encoder missing): `Log.w("ApexStudio.FeatureLimit", "...")` once per session, and the UI must reflect the limit *clearly*. Examples:
  - LUT chip with no `.cube` file → "Unavailable on this device" (not "selected", not a fake render).
  - HDR export on a device without HDR support → "HDR not available — exporting SDR".
  - Per-track audio on a single-track export path → "Audio tracks are exported as a single mix" (this is a known limitation of the single-`EditedMediaItem` Transformer path).
- No Web Worker analog needed — `Dispatchers.Default` is the equivalent.

---

## Implementation order (proposed)

Each item becomes a separate PR with a focused diff. None depend on the others blocking, but the order below groups them by risk:

| # | PR | Scope | Risk |
|---|---|---|---|
| A | **Multi-track audio mixer** (Fix #7) | Per-track ExoPlayer + MainMixer + UI wiring + export audio routing | High (audio glitches on hot-plug) |
| B | **Filmstrip thumbnails** (Fix #3) | `TimelineFilmstripEngine` + `MediaMetadataRetriever` + `LruCache` + UI | Medium (memory) |
| C | **Keyframe preview driver** (Fix #6) | `KeyframePreviewDriver` + Choreographer frame driver | Medium (SurfaceView cache limit) |
| D | **Animated text engine** (Fix #5) | `AnimatedTextEngine` + `TextOverlay` model extension + TextPanel UI | Low |
| E | **Audit / no-dummy cleanup** (Fix #9) | Grep for stale placeholders, log unimplemented, fix mismatches | Low |

Fix #1, #2, #4, #8, #10 are either done in PR #80 (compile-clean) or are ongoing engineering rules applied across every PR.

---

## What this spec is NOT promising

- A new web build. This is still the same Android Kotlin app.
- "Visually confirmed" claims. Every PR that includes a visual change will be marked **"Visual confirmation pending on a real device"** in its description until a human runs it and signs off. The build sandbox has no device.
- HDR or 8K pipeline. Not in scope.
- Web exports. Not in scope.

---

## What I will do once you approve

I will implement PR **B (Filmstrip thumbnails)** first because it removes a visible "dummy UI" placeholder (the gradient boxes in the video filmstrip row) and is self-contained. Then PR **A (Multi-track audio)** because the data model already supports it and the export TODO comments literally call it out. Then **D, C, E** in that order.

Each PR will:
1. Compile (verified by CI Build APK (Debug)).
2. Have a "Visual confirmation pending on a real device" callout in the description.
3. Be pushed on the same `fix/...` branch and the PR opened against `main`.
4. **Not** claim a feature works in a category it doesn't.

Reply with approval (or changes) and I'll start on PR B.
