## What

PR C of the Android-native rewrite plan. Adopts the four legitimate wins from the pre-refactor `EditorScreen.kt` (commit `c6465b6^`) that the new UI design had lost.

## Why

The previous "fix the editor" series (PR #79 top overlay, PR #80 live FX, PR #81 filmstrip thumbnails) fixed the most visible bugs but left four real wins from the older design on the cutting-room floor:

1. **LutBitmapCache pre-parse.** The pre-existing `LutBitmapCache` / `LutTexture` classes at the bottom of `LutFilterGlEffect.kt` were already implemented. `LutFilterGlEffect` exposes a `preloaded: LutTexture?` slot for them. PR #80 simply didn't use it — it let the effect parse the `.cube` file on the GL thread on first frame, costing 100-300ms on every fresh filter selection.
2. **Effect chain re-assert after `player.prepare()`.** Without this, swapping `mediaItem` could leave the freshly queued media item with no effects until the user next touched a slider.
3. **Paused-frame nudge.** Media3 only renders effects on produced frames. Without `seekTo(currentPosition)` after a filter change, a paused preview keeps its pre-change frame even though the GL pipeline has been updated.
4. **Per-clip `VideoClipBlock` with time-axis positioning.** The new `TimelineTrackArea` had a single static row of gradient boxes (PR #81 replaced it with a flat `Row` of `Image`s for the first video clip, but a real editor timeline needs proportional blocks at time-axis positions, trim-drag handles, and keyframe diamond markers — all of which the pre-refactor design had).

This PR also adds `VideoCropGlEffect` and `KeyframeAnimationEffect` to the chain so the preview matches the export path.

## What I did NOT bring back — and why

**The `Modifier.graphicsLayer { renderEffect = ... }` side-effect-tint that the previous design used to "show a purple tint" on the preview is not in this PR.** That was a bug, not a feature. The RenderEffect on a parent `AndroidView` wraps the entire Compose layer (including sibling composables), but `PlayerView`'s internal `SurfaceView` is composited by SurfaceFlinger as a separate hardware overlay, so the RenderEffect could not reach the video pixels — at best it tinted sibling Compose composables like the "1080P" badge. PR #79 already removed that badge. Restoring the side-effect-tint would re-introduce the original bug.

The real GL effect chain (LUT + FX + Adjustments + Crop + Keyframes) is what the user actually saw tinting the video — that path is the one this PR strengthens. The pre-parsed LUT and paused-frame nudge are what make it feel "instant" instead of "lags 200ms then snaps".

## How

1. **`LutBitmapCache` pre-parse** — a new `LaunchedEffect(activeFilterPreset)` in `EditorScreen` calls `LutBitmapCache.getOrLoad(context, activeFilterPreset)` and stores the result in `preloadedLut`. The `LutFilterGlEffect` constructor in the effect chain receives `preloaded = preloadedLut` so its init only has to upload the pre-packed `IntArray` to the GPU.
2. **Re-assert after prepare** — the existing `LaunchedEffect(exoPlayer, state.selectedClipId, state.project?.clips)` block now calls `player.setVideoEffects(currentEffects)` immediately after `player.prepare()`, inside a try/catch.
3. **Paused-frame nudge** — a new `LaunchedEffect(exoPlayer, currentEffects)` calls `setVideoEffects` and, if `!player.isPlaying && STATE_READY`, follows up with `player.seekTo(player.currentPosition)` to force a one-frame re-render.
4. **Effect chain** — `currentEffects` is now `buildList { add(VideoCropGlEffect); add(KeyframeAnimationEffect); add(LutFilterGlEffect); add(FxGlEffect); add(AdjustmentsGlEffect) }`. Order matches the export pipeline.
5. **Per-clip `VideoClipBlock`** — new private Composable. Each block is laid out at `trackStartMs * pxPerMs` with width `trackLengthMs * pxPerMs`. Reads `media.frames` from the cache and renders the extracted `Bitmap`s as `Image` Composables. Single-tap → `onSelect`. When selected, horizontal drag → `onTrimChange` (wired to `vm.trimClip`). Keyframe diamond markers painted at `keyframe.timeMs - trackStartMs` along the top. Honest "Loading…" fallback (single thin progress line) when extraction hasn't finished.
6. **`TrackLayerRow` waveform** — accepts a `FloatArray` and renders real amplitude bars from the cache. Falls through to a "Loading…" label when no data is available.
7. **TimelineMediaCache wiring** — `EditorScreen` instantiates the cache via `remember`, releases it in a `DisposableEffect` `onDispose`, and passes it to `TimelineTrackArea` along with the new `onTrimChange` parameter (wired to `vm.trimClip`).

## What this PR is NOT claiming

- **Visual confirmation on a real device is NOT done in this PR.** This sandbox has no JDK / no Android SDK / no emulator / no display. CI Build APK (Debug) confirms the code compiles. **Visual sign-off on a real device is required before merge** — open the app, select a filter, scrub the playhead, move Brightness, drag a clip on the timeline, confirm the video pixels change AND the filmstrip blocks reflect real clip content.
- `LutBitmapCache` parsing the `.cube` file on `Dispatchers.Default` is the existing implementation in `LutFilterGlEffect.kt:328-339`. The first tap on a filter still costs the parse; subsequent taps are cache hits. This matches the pre-refactor behaviour.
- Per-clip trim drag updates `clip.trimStartMs` / `clip.trimEndMs` via the existing `vm.trimClip` — undo/redo already works through the existing `pushUndo()` path inside `trimClip`.

## Category-by-category status (vs. the original 10-point spec)

| Item | Status |
|------|--------|
| #1 Player & Preview Canvas | PR #80 ✓ |
| #2 60 fps render loop | position polling exists; Choreographer driver for animated layers pending with PR D (Animated Text) |
| #3 Filmstrip thumbnails | PR #81 ✓ + this PR upgrades to per-clip `VideoClipBlock` |
| #4 Live FX pipeline | PR #80 ✓ + this PR (LutBitmapCache, paused-frame nudge, crop + keyframe) |
| #5 Animated text overlays | PR D pending |
| #6 Transform keyframes preview | data model + export work; **preview-side `KeyframeAnimationEffect` wired in this PR** |
| #7 Multi-track audio playback | PR A pending (data model already works) |
| #8 HDR / float framebuffers | not in scope |
| #9 No dummy UI | this PR removes the last two dummy elements (placeholder gradient + static 32 audio bars) |
| #10 Honest fallback | this PR uses clearly-labelled "Loading…" / "Tap + to add a video clip" + Log warnings from the cache |

## Files changed

- `app/src/main/java/com/apexstudio/app/ui/screens/editor/EditorScreen.kt` — instantiate `TimelineMediaCache` + `DisposableEffect` release; resolve `activeFilterPreset` / `activeFxPreset`; pre-parse LUT via `LutBitmapCache`; rebuild effect chain to include crop + keyframes; re-assert after prepare; paused-frame nudge; per-clip `VideoClipBlock`; `TrackLayerRow` waveform param; pass `timelineMediaCache` and `onTrimChange` to `TimelineTrackArea`.
