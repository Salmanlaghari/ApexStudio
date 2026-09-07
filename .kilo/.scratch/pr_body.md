## Root cause

PR #79 scoped the Compose `Modifier.graphicsLayer { renderEffect = ... }` to the `AndroidView` wrapping the `PlayerView`. That modifier cannot reach the decoded video pixels regardless of where it sits in the Compose tree, because `PlayerView`'s internal `SurfaceView` is composited by SurfaceFlinger as a separate hardware overlay — it lives **outside** Compose's `GraphicsLayer` / `RenderEffect` pipeline. So the selected filter, FX, and adjustments updated state correctly but never touched the video pixels in the preview.

Additionally, the export pipeline (`EditorViewModel.startExport → ExportEngine`) was silently dropping the **transition** type and the **VideoAdjustments** — both fields were declared on `ExportConfig` but never written, so even the exported MP4 had neither the chosen transition nor the slider-driven adjustments baked in.

## Investigation summary (per category)

| Category | State plumbing | Export bake (before) | Live preview bake (before) | Live preview bake (after) |
|----------|---------------|----------------------|---------------------------|--------------------------|
| **Filter (LUT)** | works | works via `LutFilterGlEffect` | broken — Compose RenderEffect on `PlayerView` does nothing | works via `LutFilterGlEffect` on preview player |
| **Adjustments** | works | `ExportConfig.adjustments` was declared but never read | broken | works via new `AdjustmentsGlEffect` on preview **and** export |
| **FX (Effects)** | works | works via `FxGlEffect` | broken | works via `FxGlEffect` on preview player |
| **Transitions** | works | `ExportConfig.transitionType` was declared but never read | no single-clip live concept | baked on export; still no single-clip live preview (see below) |
| **Templates** | works | works via `TimelineTemplateManager` | broken (only because they write filter/fx state) | works (Templates are just filter + fx; the chain reacts) |

## Why single-clip live transitions aren't possible

`TransitionGlEffect` is a **two-input** effect (clip A + clip B) designed for `EditedMediaItemSequence` where a transition crossfades two adjacent clips. A single-clip preview has no "clip B" to transition into. The export pipeline handles this correctly — single-clip export skips the transition; multi-clip template export uses `TransitionGlEffect` via `TimelineTemplateManager`.

## Fix

1. **New `AdjustmentsGlEffect`** (real Media3 `GlEffect` with a GLSL fragment shader) under `data/adjust/`. Applies Brightness / Contrast / Saturation / Exposure / Temperature / Tint / Highlights / Shadows to every decoded frame, with per-uniform lambdas for smooth slider drag.
2. **`EditorScreen.kt`** now installs the live effect chain on the preview ExoPlayer via `player.setVideoEffects([AdjustmentsGlEffect, LutFilterGlEffect, FxGlEffect])` inside a `LaunchedEffect`. Source order is **before** the `prepare()` `LaunchedEffect`, satisfying Media3's "must call `setVideoEffects` at least once before `prepare`" contract. Chain composition is keyed on `(activeFilterId, activeFxId, fxIntensity, adjustments.isDefault)` so chain rebuilds are minimised; per-slider values flow through `MutableStateFlow` side-channels so dragging doesn't re-upload the LUT texture 60×/s.
3. **Dead Compose `RenderEffect` removed** from `VideoPreviewArea` — it was guaranteed never to affect the video pixels, no matter where it was attached.
4. **`EditorViewModel.startExport`** now fills `adjustments` (selected clip's, falling back to project default) and `transitionType` / `transitionDurationMs` (from `project.lastTransitionType`) into `ExportConfig`. The export pipeline now bakes both into the MP4.
5. **`ExportEngine.startExport`** installs `AdjustmentsGlEffect` so the exported MP4 reflects the user's adjustments too (the CPU `FilterColorMatrix` fallback for thumbnails was the only existing path, and it was never reached on export either).

## What the user will now see (expected behaviour)

- Select a filter ("Matrix Green", "Romance Warm", "Film Noir Cinema", …) → the video frame itself tints via the Media3 3D-LUT shader.
- Move Brightness to -91 → frame darkens. To 100 → frame brightens. Same for Contrast / Saturation / Temperature / Tint / Highlights / Shadows.
- Select an FX ("Vignette", "Chromatic", "VHS", …) → the GL shader runs over every frame.
- Apply a Template → same as picking the bundled filter + FX; the chain reacts.
- Toggle a Transition → no single-clip preview, but the exported MP4 (multi-clip template export) will now show the transition. (This is a fundamental limitation of `TransitionGlEffect`, not an oversight.)

## Mandatory visual verification — NOT done in this PR

**I cannot honestly claim visual verification on a real device.** This sandbox has no JDK, no Android SDK, no emulator, and no display. The "visually confirm by eye" requirement from the task description is **not** satisfied by this PR. What I did:

- Traced the full pipeline (state → ExoPlayer → GL shader → pixels).
- Confirmed the API contract (`setVideoEffects` exists in Media3 1.5.0; must be called before `prepare()`; chain composition order).
- Confirmed `setVideoEffects` ordering by source position in `EditorScreen.kt` (the `setVideoEffects` `LaunchedEffect` is defined **before** the `prepare()` `LaunchedEffect`, so Compose launches it first).
- Static syntax checks: braces, parens, imports all balanced.
- CI `Build APK (Debug)` will confirm the code compiles.

**Visual sign-off by a human on a real device is required before this is merged.** This is non-negotiable.

## Files changed

- `app/src/main/java/com/apexstudio/app/data/adjust/AdjustmentsGlEffect.kt` — new (246 lines)
- `app/src/main/java/com/apexstudio/app/data/export/ExportEngine.kt` — install `AdjustmentsGlEffect` in export
- `app/src/main/java/com/apexstudio/app/presentation/viewmodel/EditorViewModel.kt` — fill adjustments + transition fields on `ExportConfig`
- `app/src/main/java/com/apexstudio/app/ui/screens/editor/EditorScreen.kt` — install live effect chain on preview ExoPlayer, remove dead Compose `RenderEffect`

PR #79 is the prior step; this PR supersedes it.
