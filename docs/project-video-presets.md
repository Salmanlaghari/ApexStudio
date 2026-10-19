# Existing-project video tools

## What is usable

Open **AI tools → Text templates + video effects · not AI** after selecting an existing main-track video in the current project. These are deterministic local editing presets, not an unlocked AI service or a safety-check bypass.

**Text → Template** offers three built-in titles: Bold Title (“YOUR STORY”), Elegant Title (“A MOMENT”) and Caption Bar (“MADE TO REMEMBER”). Selection inserts the fixed words and styling as a normal clip text overlay. There is no prompt or free-form text field in this workflow. Existing manual text editing elsewhere in the editor is unchanged.

**Real video → Effects** offers Warm Film, Cool Fade and Monochrome. A persisted per-clip preset ID resolves to the same color matrix and `ColorMatrixGlEffect` in the actual decoder preview and export. These presets do not rely on the legacy approximate Compose GPU-filter overlay. The effect does not overwrite other filter settings or source media.

Both actions require the same project, selection and exact clip snapshot captured when the panel opened. Still images, audio, overlays, invalid trim ranges, unknown presets and stale targets are rejected without adding undo history. Each successful edit has one undo snapshot; applying the already-selected effect is a no-op. The original media URI and unrelated project data are preserved. Older projects default to no preset effect.

Single-video export includes the fixed title through the existing text-overlay renderer. Sequential multi-video project export supports the per-clip color effects; its existing text-overlay limitation remains fail-closed rather than silently omitting titles. Export an individual titled clip when that project-composition restriction applies.

## Protected AI is still locked

The latest main was checked after PR #168 merged. The production inventory contains 13 integrity-pinned assets for landmarks, segmentation, captions, alignment, upscaling and matting, but **no approved moderation or OCR classifier**. `GlobalModerationEnforcement` remains denied for every protected action. The reviewed moderation digest catalog remains empty. No cloud call, automatic download, override or fallback was added. Live camera gesture controls are no longer exposed from the editor AI panel because this scope is existing project videos only.

The legacy model-based face-track review path is now also gated before decoder/model startup, both at the editor entry and the analyzer backend. Review-only ML is not exempt from mandatory moderation. Its UI is visibly locked; deterministic histogram/gradient scene review remains available.

The existing NSFW/NSFL candidate is not shipped/approved, lacks hateful coverage and has no OCR/text path. The recorded moderation validation is 0/3 categories with no evaluation samples. Asset hashes, model publisher descriptions, pixel heuristics and keyword lists do not establish safety accuracy. Protected AI was not run or newly enabled by this work.

Before protected AI can be made usable, the app needs licensed, pinned, fully offline visual and OCR/text ML artifacts; current-project video-frame provenance; and app-specific held-out validation covering sexual, violent and hateful content, including relevant visible text. The validation must record true positives, clean samples, edge cases, calibrated thresholds, false positives and false negatives, with conservative rules alongside the ML classifier. Missing models, frame decode/OCR failures, inference errors and timeouts must keep processing locked. No such validation is claimed here.

See [the moderation validation record](moderation-followup-validation.md), `app/src/main/assets/models/models.lock.json`, and `ai/safety/GlobalModerationEnforcement.kt` / `FailClosedModerationGate.kt` for the existing evidence.

## Verification boundaries

Host unit/Compose regressions cover fixed choices, no text input, disabled non-video actions, stale-target rejection, source/data preservation, undo, persistence and shared effect resolution. Existing moderation regressions must continue to pass. Automated renderer-contract tests are not a physical-device pixel, performance or encoder acceptance claim; no such device acceptance is implied.
