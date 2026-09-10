## What

PR B of the Android-native rewrite plan: `TimelineTrackArea` now reads from the pre-existing `TimelineMediaCache` (which was defined but never used) instead of drawing gradient-box placeholders for the video filmstrip row and static 32-bar fakes for the audio waveform rows.

## Why

The previous "fix the video editor" series (PR #79 top overlay, PR #80 live FX) addressed two layers of the "editor shows the wrong thing" bug. The `TimelineTrackArea` itself was still rendering two distinct dummy UI elements:

1. **Video filmstrip row** — `repeat(6) { Box(purple gradient) }` repeated six times across the row. Did not represent the loaded video at all.
2. **Audio / voice track rows** — `repeat(32) { Box(width 2dp, height 34 * fixedFraction) }` where `fixedFraction` was 0.3/0.5/0.8. Bore no relationship to any decoded audio.

Both violated the no-dummy-UI rule from the rewrite spec.

`TimelineMediaCache` was already fully implemented (`MediaMetadataRetriever` on `Dispatchers.IO`, LRU byte budget, content-keyed cache key, `StateFlow` publication, race protection for zoom/trim changes) — it just had no caller. The fix is to wire it in.

## How

1. `EditorScreen` instantiates `TimelineMediaCache` via `remember()`, releases its scope in a `DisposableEffect` `onDispose`.
2. `TimelineTrackArea` takes the cache as a parameter, calls `cache.observe(clips, pxPerMs)` every recomposition (no-op when inputs haven't changed thanks to the content-keyed cache), and subscribes to `cache.state` via `collectAsStateWithLifecycle`.
3. The video filmstrip row now reads `ClipMedia.frames` for the first `VIDEO` / `OVERLAY` clip and renders each `Bitmap` via `Image` with `contentScale = Crop`, weighted equally so the cell count is stable across viewport widths. `pxPerMs` is derived from `state.zoomLevel` so a zoom-in triggers a cache miss for sharper frames.
4. The audio / voice rows read `ClipMedia.waveform` (or fall back to `state.audioWaveform` for the legacy decode path) and draw a bar per sample with height proportional to amplitude. Bar count is capped at 48 to match row width regardless of sample-count.
5. **Honest fallback (no fake render):** when no frames / waveform are available (extraction in flight, or the source couldn't be decoded), the row draws a small pulsing "Loading thumbnails…" / "Loading…" label instead of a fake render. The previous purple gradient boxes are gone.

## What this PR is **not** claiming

- **Visual confirmation on a real device is NOT done in this PR.** This sandbox has no JDK / no Android SDK / no emulator / no display. CI Build APK (Debug) confirms the code compiles. **Visual sign-off on a real device is required before merge** (open the app, scrub a video clip, confirm the filmstrip row shows real frame thumbnails matching the playhead; pick an audio clip, confirm the audio row shows real amplitude bars).
- The filmstrip frames are decoded on the device's main IO scheduler; on low-end devices this can take ~100-500ms for a 10s clip at 1 fps. This is the expected behaviour and matches what `MediaMetadataRetriever` does on Android. No "fast Web Worker pipeline" alternative exists on Android.
- Real per-track audio playback (volume/mute/solo/fade) is **PR A**, separate change. This PR only renders the cached waveform visuals.
- Live keyframe preview (PR C) and animated text overlay (PR D) are still pending.

## Category-by-category status (vs. the original 10-point spec)

| Item | Status |
|------|--------|
| #1 Player & Preview Canvas | PR #80 ✓ |
| #2 60 fps render loop | position polling exists; Choreographer driver for animated layers pending with PR C |
| #3 **Filmstrip thumbnails** | **This PR** ✓ (compile-clean; on-device visual pending) |
| #4 Live FX pipeline | PR #80 ✓ |
| #5 Animated text overlays | PR D pending |
| #6 Transform keyframes preview | PR C pending (data model + export already work) |
| #7 Multi-track audio playback | PR A pending (data model already works) |
| #8 HDR / float framebuffers | not in scope |
| #9 No dummy UI | this PR removes the last two dummy elements |
| #10 Honest fallback | this PR uses clearly-labelled "Loading…" + Log warnings |

## Files changed

- `app/src/main/java/com/apexstudio/app/ui/screens/editor/EditorScreen.kt` — instantiate cache, pass to `TimelineTrackArea`, replace placeholder gradients with real `Image` renders + honest loading fallback, render real waveform bars in the audio/voice rows.
